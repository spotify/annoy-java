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


  private final ArrayList<Long> roots;
  private MappedByteBuffer[] buffers;

  private final int DIMENSION, MIN_LEAF_SIZE;
  private final IndexType INDEX_TYPE;
  private final int INDEX_TYPE_OFFSET;

  // size of C structs in bytes (initialized in init)
  private final int K_NODE_HEADER_STYLE;
  private final int NODE_SIZE;

  private final int INT_SIZE = 4;
  private final int FLOAT_SIZE = 4;
  private final int MAX_NODES_IN_BUFFER;
  private final int BLOCK_SIZE;
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
    this(dimension, filename, indexType, 0);
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

  ANNIndex(final int dimension,
                  final String filename,
                  IndexType indexType,
                  final int blockSize) throws IOException {
    DIMENSION = dimension;
    INDEX_TYPE = indexType;
    INDEX_TYPE_OFFSET = (INDEX_TYPE == IndexType.ANGULAR) ? 4 : 8;
    K_NODE_HEADER_STYLE = (INDEX_TYPE == IndexType.ANGULAR) ? 12 : 16;
    // we can store up to MIN_LEAF_SIZE children in leaf nodes (we put
    // them where the separating plane normally goes)
    this.MIN_LEAF_SIZE = DIMENSION + 2;
    this.NODE_SIZE = K_NODE_HEADER_STYLE + FLOAT_SIZE * DIMENSION;
    this.MAX_NODES_IN_BUFFER = blockSize == 0 ?
            Integer.MAX_VALUE / NODE_SIZE : blockSize * NODE_SIZE;
    BLOCK_SIZE = this.MAX_NODES_IN_BUFFER * NODE_SIZE;
    roots = new ArrayList<>();
    load(filename);
  }

  private void load(final String filename) throws IOException {
    memoryMappedFile = new RandomAccessFile(filename, "r");
    long fileSize = memoryMappedFile.length();
    if (fileSize == 0L) {
      throw new IOException("Index is a 0-byte file?");
    }

    int numNodes = (int) (fileSize / NODE_SIZE);
    int buffIndex =  (numNodes - 1) / MAX_NODES_IN_BUFFER;
    int rest = (int) (fileSize % BLOCK_SIZE);
    int blockSize = (rest > 0 ? rest : BLOCK_SIZE);
    long position = fileSize - blockSize;

    buffers = new MappedByteBuffer[buffIndex + 1];
    boolean process = true;
    int m = -1;
    long index = fileSize;
    while(position >= 0) {
      MappedByteBuffer annBuf = memoryMappedFile.getChannel().map(
              FileChannel.MapMode.READ_ONLY, position, blockSize);
      annBuf.order(ByteOrder.LITTLE_ENDIAN);

      buffers[buffIndex--] = annBuf;

      for (int i = blockSize - NODE_SIZE; process && i >= 0; i -= NODE_SIZE) {
        index -= NODE_SIZE;
        int k = annBuf.getInt(i);  // node[i].n_descendants
        if (m == -1 || k == m) {
          roots.add(index);
          m = k;
        } else {
          process = false;
        }
      }
      blockSize = BLOCK_SIZE;
      position -= blockSize;
    }
  }

  private float getFloatInAnnBuf(long pos) {
    int b = (int) (pos / BLOCK_SIZE);
    int f = (int) (pos % BLOCK_SIZE);
    return buffers[b].getFloat(f);
  }

  private int getIntInAnnBuf(long pos) {
    int b = (int) (pos / BLOCK_SIZE);
    int i = (int) (pos % BLOCK_SIZE);
    return buffers[b].getInt(i);
  }

  @Override
  public void getNodeVector(final long nodeOffset, float[] v) {
    MappedByteBuffer nodeBuf = buffers[(int) (nodeOffset / BLOCK_SIZE)];
    int offset = (int) ((nodeOffset % BLOCK_SIZE) + K_NODE_HEADER_STYLE);
    for (int i = 0; i < DIMENSION; i++) {
      v[i] = nodeBuf.getFloat(offset + i * FLOAT_SIZE);
    }
  }

  @Override
  public void getItemVector(int itemIndex, float[] v) {
    getNodeVector(itemIndex * NODE_SIZE, v);
  }

  private float getNodeBias(final long nodeOffset) { // euclidean-only
    return getFloatInAnnBuf(nodeOffset + 4);
  }

  public final float[] getItemVector(final int itemIndex) {
    return getNodeVector(((long) itemIndex) * NODE_SIZE);
  }

  public float[] getNodeVector(final long nodeOffset) {
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

    PQEntry(final float margin, final long nodeOffset) {
      this.margin = margin;
      this.nodeOffset = nodeOffset;
    }

    private float margin;
    private long nodeOffset;

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

    for (long r : roots) {
      pq.add(new PQEntry(kMaxPriority, r));
    }

    Set<Integer> nearestNeighbors = new HashSet<Integer>();
    while (nearestNeighbors.size() < roots.size() * nResults && !pq.isEmpty()) {
      PQEntry top = pq.poll();
      long topNodeOffset = top.nodeOffset;
      int nDescendants = getIntInAnnBuf(topNodeOffset);
      float[] v = getNodeVector(topNodeOffset);
      if (nDescendants == 1) {  // n_descendants
        // FIXME: does this ever happen?
        if (isZeroVec(v))
          continue;
        nearestNeighbors.add((int) (topNodeOffset / NODE_SIZE));
      } else if (nDescendants <= MIN_LEAF_SIZE) {
        for (int i = 0; i < nDescendants; i++) {
          int j = getIntInAnnBuf(topNodeOffset +
                  INDEX_TYPE_OFFSET +
                  i * INT_SIZE);
          if (isZeroVec(getNodeVector(((long) j) * NODE_SIZE)))
            continue;
          nearestNeighbors.add(j);
        }
      } else {
        float margin = (INDEX_TYPE == IndexType.ANGULAR) ?
                cosineMargin(v, queryVector) :
                euclideanMargin(v, queryVector, getNodeBias(topNodeOffset));
        long childrenMemOffset = topNodeOffset + INDEX_TYPE_OFFSET;
        long lChild = ((long) NODE_SIZE) * getIntInAnnBuf(childrenMemOffset);
        long rChild = ((long) NODE_SIZE) * getIntInAnnBuf(childrenMemOffset + 4);
        pq.add(new PQEntry(-margin, lChild));
        pq.add(new PQEntry(margin, rChild));
      }
    }

    ArrayList<PQEntry> sortedNNs = new ArrayList<PQEntry>();
    int i = 0;
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
    for (i = 0; i < nResults && i < sortedNNs.size(); i++) {
      result.add((int) sortedNNs.get(i).nodeOffset);
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
    else throw new RuntimeException("wrong index type specified");
    int queryItem = Integer.parseInt(args[3]);  // 3

    ANNIndex annIndex = new ANNIndex(dimension, indexPath, indexType);

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
