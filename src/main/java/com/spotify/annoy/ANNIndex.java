package com.spotify.annoy;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

/**
 * Read-only Approximate Nearest Neighbor Index which queries databases created by annoy.
 */
public class ANNIndex implements AnnoyIndex {

  private int dimension, minLeafSize, nodeSize;
  private ArrayList<Integer> roots;
  private MappedByteBuffer annBuf;
  private final int kNodeHeaderSize = 12;
  private final int kFloatSize = 4;

  private static float[] zeros = new float[40];

  /**
   * Construct and load an Annoy index.
   * @param dimension  dimensionality of tree, e.g. 40
   * @param filename   filename of tree
   * @throws IOException  if file can't be loaded
   */
  public ANNIndex(final int dimension, final String filename) throws IOException {
    init(dimension);
    load(filename);
  }

  private void init(final int dimension) {
    this.dimension = dimension;
    roots = new ArrayList<Integer>();
    // we can store up to minLeafSize children in leaf nodes (we put them where the separating
    // plane normally goes)
    this.minLeafSize = dimension + 2;
    this.nodeSize = kNodeHeaderSize + kFloatSize * dimension;
  }

  private void load(final String filename) throws IOException {
    RandomAccessFile memoryMappedFile = new RandomAccessFile(filename, "r");
    int fileSize = (int) memoryMappedFile.length();
    System.err.printf("%s: %d bytes\n", filename, fileSize);

    // We only support indexes <4GB as a result of ByteBuffer using an int index
    annBuf = memoryMappedFile.getChannel().map(
            FileChannel.MapMode.READ_ONLY, 0, fileSize);
    annBuf.order(ByteOrder.LITTLE_ENDIAN);

    // an Angular::node contains:
    // 4   int n_descendants
    // 8   int children[2]
    // --  float v[dimension]
    // 12 + 4*dimension total
    int m = -1;
    for (int i = fileSize - nodeSize; i >= 0; i -= nodeSize) {
      int k = annBuf.getInt(i);  // node[i].n_descendants
      if (m == -1 || k == m) {
        roots.add(i);
        m = k;
      } else {
        break;
      }
    }

    System.err.printf("%s: found %d roots with degree %d\n",
                      filename, roots.size(), m);
  }

  @Override
  public final void getNodeVector(final int node, final float[] v) {
    for (int i = 0; i < dimension; i++) {
      v[i] = annBuf.getFloat(i * kFloatSize + node + kNodeHeaderSize);
    }
  }

  @Override
  public final void getItemVector(final int item, final float[] v) {
    getNodeVector(item * nodeSize, v);
  }

  /**
   * Compute the Euclidean norm of a vector.
   * @param u  vector
   * @return   norm
   */
  public static double norm(final float[] u) {
    float n = 0;
    for (float x : u) {
      n += x * x;
    }
    return Math.sqrt(n);
  }

  /**
   * Compute the cosine between two vectors,
   * which runs between 1 (closest) to -1 (farthest).
   * @param u  first vector
   * @param v  second vector
   * @return   cosine of angle between u and v
   */
  public static float cosineMargin(final float[] u, final float[] v) {
    double d = 0;
    double un = norm(u), vn = norm(v);
    for (int i = 0; i < u.length; i++) {
      d += u[i] * v[i];
    }
    return (float) (d / (un * vn));
  }

  /**
   * Compute the cosine *distance* between two vectors,
   * which runs from 0 (closest) to 2 (farthest).
   * @param u  first vector
   * @param v  second vector
   * @return   cosine distance between u and v
   */
  public static float cosineDist(final float[] u, final float[] v) {
    return 1.0f - cosineMargin(u, v);
  }

  private class PQEntry implements Comparable<PQEntry> {
    PQEntry(final float margin, final int node) { this.margin = margin; this.node = node; }
    private float margin;
    private int node;

    @Override
    public int compareTo(final PQEntry o) {
      return Float.compare(o.margin, margin);
    }
  }

  @Override
  public final List<Integer> getNearest(final float[] queryVector, final int nResults) {
    PriorityQueue<PQEntry> pq = new PriorityQueue<>(
            roots.size() * kFloatSize);
    final float kMaxPriority = 1e30f;
    float[] v = new float[dimension];
    for (int r : roots) {
      pq.add(new PQEntry(kMaxPriority, r));
    }

    Set<Integer> nearestNeighbors = new HashSet<Integer>();
    while (nearestNeighbors.size() < roots.size() * nResults && !pq.isEmpty()) {
      PQEntry top = pq.poll();
      int n = top.node;
      int nDescendants = annBuf.getInt(n);
      getNodeVector(n, v);
      if(Arrays.equals(v, ANNIndex.zeros))
          continue;
      if (nDescendants == 1) {  // n_descendants
        // FIXME: does this ever happen?
        nearestNeighbors.add(n / nodeSize);
      } else if (nDescendants <= minLeafSize) {
        for (int i = 0; i < nDescendants; i++) {
          int j = annBuf.getInt(4 + n + i * kFloatSize);
          nearestNeighbors.add(j);
        }
      } else {
        float margin = cosineMargin(v, queryVector);
        int lChild = nodeSize * annBuf.getInt(n + 4);
        int rChild = nodeSize * annBuf.getInt(n + 8);
        pq.add(new PQEntry(-margin, lChild));
        pq.add(new PQEntry(margin, rChild));
      }
    }
    ArrayList<PQEntry> sortedNNs = new ArrayList<PQEntry>();
    int i = 0;
    for (int nn : nearestNeighbors) {
      getItemVector(nn, v);
      if(! Arrays.equals(v, ANNIndex.zeros)) {
        sortedNNs.add(new PQEntry(cosineMargin(v, queryVector), nn));
      }
    }
    Collections.sort(sortedNNs);
    ArrayList<Integer> result = new ArrayList<>(nResults);
    for (i = 0; i < nResults && i < sortedNNs.size(); i++) {
      result.add(sortedNNs.get(i).node);
    }
    return result;
  }

  /**
   * a test query program.
   * @param args  tree filename, dimension, and query item id.
   * @throws IOException  if unable to load index
   */
  public static void main(final String[] args) throws IOException {
    ANNIndex annIndex = new ANNIndex(Integer.parseInt(args[0]), args[1]);
    float[] u = new float[annIndex.dimension],
            v = new float[annIndex.dimension];
    int queryItem = Integer.parseInt(args[2]);
    annIndex.getItemVector(queryItem, u);
    System.out.printf("vector[%d]: ", queryItem);
    for (float x : u) {
      System.out.printf("%2.2f ", x);
    }
    System.out.printf("\n");
    List<Integer> nearestNeighbors = annIndex.getNearest(u, 10);
    for (int nn : nearestNeighbors) {
      annIndex.getItemVector(nn, v);
      System.out.printf("%d %d %f\n", queryItem, nn, cosineMargin(u, v));
    }
  }

}
