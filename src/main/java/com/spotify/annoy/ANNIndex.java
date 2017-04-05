package com.spotify.annoy;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

/**
 * Read-only Approximate Nearest Neighbor Index which queries
 * databases created by annoy.
 */
public class ANNIndex implements AnnoyIndex {

  private final ArrayList<Integer> roots;
  private MappedByteBuffer annBuf;

  private final int DIMENSION, MIN_LEAF_SIZE;
  private final IndexType INDEX_TYPE;
  private final int INDEX_TYPE_OFFSET;

  // size of C structs in bytes (initialized in init)
  private final int K_NODE_HEADER_STYLE;
  private final int NODE_SIZE;

  private final int INT_SIZE = 4;
  private final int FLOAT_SIZE = 4;
  private RandomAccessFile memoryMappedFile;


  /**
   * Construct and load an Annoy index of a specific type (euclidean / angular).
   *
   * @param dimension dimensionality of tree, e.g. 40
   * @param filename  filename of tree
   * @param indexType type of index
   * @throws IOException if file can't be loaded
   */
  public ANNIndex(final int dimension,
                  final String filename,
                  IndexType indexType) throws IOException {
    DIMENSION = dimension;
    INDEX_TYPE = indexType;
    INDEX_TYPE_OFFSET = (INDEX_TYPE == IndexType.ANGULAR) ? 4 : 8;
    K_NODE_HEADER_STYLE = (INDEX_TYPE == IndexType.ANGULAR) ? 12 : 16;
    // we can store up to MIN_LEAF_SIZE children in leaf nodes (we put
    // them where the separating plane normally goes)
    this.MIN_LEAF_SIZE = DIMENSION + 2;
    this.NODE_SIZE = K_NODE_HEADER_STYLE + FLOAT_SIZE * DIMENSION;

    roots = new ArrayList<Integer>();
    load(filename);
  }

  /**
   * Construct and load an (Angular) Annoy index.
   *
   * @param dimension dimensionality of tree, e.g. 40
   * @param filename  filename of tree
   * @throws IOException if file can't be loaded
   */
  public ANNIndex(final int dimension,
                  final String filename) throws IOException {
    this(dimension, filename, IndexType.ANGULAR);
  }


  private void load(final String filename) throws IOException {
    memoryMappedFile = new RandomAccessFile(filename, "r");
    int fileSize = (int) memoryMappedFile.length();

    if (fileSize % DIMENSION != 0) {
      throw new RuntimeException("ANNIndex initiated with wrong dimension size");
    }

    // We only support indexes <4GB as a result of ByteBuffer using an int index
    annBuf = memoryMappedFile.getChannel().map(
            FileChannel.MapMode.READ_ONLY, 0, fileSize);
    annBuf.order(ByteOrder.LITTLE_ENDIAN);
    int m = -1;
    for (int i = fileSize - NODE_SIZE; i >= 0; i -= NODE_SIZE) {
      int k = annBuf.getInt(i);  // node[i].n_descendants
      if (m == -1 || k == m) {
        roots.add(i);
        m = k;
      } else {
        break;
      }
    }
  }

  @Override
  public void getNodeVector(final int nodeOffset, float[] v) {
    for (int i = 0; i < DIMENSION; i++)
      v[i] = annBuf.getFloat(nodeOffset + K_NODE_HEADER_STYLE + i * FLOAT_SIZE);
  }

  @Override
  public void getItemVector(int itemIndex, float[] v) {
    getNodeVector(itemIndex * NODE_SIZE, v);
  }

  private float getNodeBias(final int nodeOffset) { // euclidean-only
    return annBuf.getFloat(nodeOffset + 4);
  }

  public final float[] getItemVector(final int itemIndex) {
    return getNodeVector(itemIndex * NODE_SIZE);
  }

  public float[] getNodeVector(final int nodeOffset) {
    float[] v = new float[DIMENSION];
    getNodeVector(nodeOffset, v);
    return v;
  }

  private static float norm(final float[] u) {
    float n = 0;
    for (float x : u)
      n += x * x;
    return (float) Math.sqrt(n);
  }

  private static float euclideanDistance(final float[] u, final float[] v) {
    float[] diff = new float[u.length];
    for (int i = 0; i < u.length; i++)
      diff[i] = u[i] - v[i];
    return norm(diff);
  }

  public static float cosineMargin(final float[] u, final float[] v) {
    double d = 0;
    for (int i = 0; i < u.length; i++)
      d += u[i] * v[i];
    return (float) (d / (norm(u) * norm(v)));
  }

  public static float euclideanMargin(final float[] u,
                                      final float[] v,
                                      final float bias) {
    float d = bias;
    for (int i = 0; i < u.length; i++)
      d += u[i] * v[i];
    return d;
  }

  /**
   * Closes this stream and releases any system resources associated
   * with it. If the stream is already closed then invoking this
   * method has no effect.
   * <p/>
   * <p> As noted in {@link AutoCloseable#close()}, cases where the
   * close may fail require careful attention. It is strongly advised
   * to relinquish the underlying resources and to internally
   * <em>mark</em> the {@code Closeable} as closed, prior to throwing
   * the {@code IOException}.
   *
   * @throws IOException if an I/O error occurs
   */
  @Override
  public void close() throws IOException {
    memoryMappedFile.close();
  }

  private class PQEntry implements Comparable<PQEntry> {

    PQEntry(final float margin, final int nodeOffset) {
      this.margin = margin;
      this.nodeOffset = nodeOffset;
    }

    private float margin;
    private int nodeOffset;

    @Override
    public int compareTo(final PQEntry o) {
      return Float.compare(o.margin, margin);
    }

  }

  private static boolean isZeroVec(float[] v) {
    for (int i = 0; i < v.length; i++)
      if (v[i] != 0)
        return false;
    return true;
  }

  @Override
  public final List<Integer> getNearest(final float[] queryVector,
                                        final int nResults) {
    PriorityQueue<PQEntry> pq = new PriorityQueue<>(
            roots.size() * FLOAT_SIZE);
    final float kMaxPriority = 1e30f;

    for (int r : roots) {
      pq.add(new PQEntry(kMaxPriority, r));
    }

    Set<Integer> nearestNeighbors = new HashSet<Integer>();
    while (nearestNeighbors.size() < roots.size() * nResults && !pq.isEmpty()) {
      PQEntry top = pq.poll();
      int topNodeOffset = top.nodeOffset;
      int nDescendants = annBuf.getInt(topNodeOffset);
      float[] v = getNodeVector(topNodeOffset);
      if (nDescendants == 1) {  // n_descendants
        // FIXME: does this ever happen?
        if (isZeroVec(v))
          continue;
        nearestNeighbors.add(topNodeOffset / NODE_SIZE);
      } else if (nDescendants <= MIN_LEAF_SIZE) {
        for (int i = 0; i < nDescendants; i++) {
          int j = annBuf.getInt(topNodeOffset +
                  INDEX_TYPE_OFFSET +
                  i * INT_SIZE);
          if (isZeroVec(getNodeVector(j * NODE_SIZE)))
            continue;
          nearestNeighbors.add(j);
        }
      } else {
        float margin = (INDEX_TYPE == IndexType.ANGULAR) ?
                cosineMargin(v, queryVector) :
                euclideanMargin(v, queryVector, getNodeBias(topNodeOffset));
        int childrenMemOffset = topNodeOffset + INDEX_TYPE_OFFSET;
        int lChild = NODE_SIZE * annBuf.getInt(childrenMemOffset);
        int rChild = NODE_SIZE * annBuf.getInt(childrenMemOffset + 4);
        pq.add(new PQEntry(-margin, lChild));
        pq.add(new PQEntry(margin, rChild));
      }
    }

    ArrayList<PQEntry> sortedNNs = new ArrayList<PQEntry>();
    for (int nn : nearestNeighbors) {
      float[] v = getItemVector(nn);
      if (!isZeroVec(v)) {
        sortedNNs.add(
                new PQEntry((INDEX_TYPE == IndexType.ANGULAR) ?
                        cosineMargin(v, queryVector) :
                        -euclideanDistance(v, queryVector),
                        nn));
      }
    }
    Collections.sort(sortedNNs);

    ArrayList<Integer> result = new ArrayList<>(nResults);
    for (int i = 0; i < nResults && i < sortedNNs.size(); i++) {
      result.add(sortedNNs.get(i).nodeOffset);
    }
    return result;
  }


  /**
   * a test query program.
   *
   * @param args tree filename, dimension, indextype ("angular" or
   *             "euclidean" and query item id.
   * @throws IOException if unable to load index
   */
  public static void main(final String[] args) throws IOException {

    String indexPath = args[0];                 // 0
    int dimension = Integer.parseInt(args[1]);  // 1
    IndexType indexType = null;                 // 2
    if (args[2].toLowerCase().equals("angular"))
      indexType = IndexType.ANGULAR;
    else if (args[2].toLowerCase().equals("euclidean"))
      indexType = IndexType.EUCLIDEAN;
    else
      throw new RuntimeException("wrong index type specified");
    int queryItem = Integer.parseInt(args[3]);  // 3

    ANNIndex annIndex = new ANNIndex(dimension, indexPath, indexType);

    // input vector
    float[] u = annIndex.getItemVector(queryItem);
    System.out.printf("vector[%d]: ", queryItem);

    for (float x : u) {
      System.out.printf("%2.2f ", x);
    }

    System.out.printf("\n");

    List<Integer> nearestNeighbors = annIndex.getNearest(u, 10);

    for (int nn : nearestNeighbors) {
      float[] v = annIndex.getItemVector(nn);
      System.out.printf("%d %d %f\n",
              queryItem, nn,
              (indexType == IndexType.ANGULAR) ?
                      cosineMargin(u, v) : euclideanDistance(u, v));
    }
  }

}
