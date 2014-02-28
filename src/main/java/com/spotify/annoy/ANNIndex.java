package com.spotify.annoy;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

public class ANNIndex {

  public ANNIndex(int dimension, String filename) throws IOException {
    init(dimension);
    load(filename);
  }

  private void init(int dimension) {
    this.dimension = dimension;
    roots = new ArrayList<Integer>();
    // we can store up to minLeafSize children in leaf nodes (we put them where the separating
    // plane normally goes)
    this.minLeafSize = dimension + 2;
    this.nodeSize = 12 + 4*dimension;
  }

  private void load(String filename) throws IOException {
    memoryMappedFile = new RandomAccessFile(filename, "r");
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

  public void getNodeVector(int node, float[] v) {
    for (int i = 0; i < dimension; i++) {
      v[i] = annBuf.getFloat(i*4 + node + 12);
    }
  }

  public void getItemVector(int item, float[] v) {
    getNodeVector(item * nodeSize, v);
  }

  public static float margin(float[] u, float[] v) {
    float d = 0;
    for (int i = 0; i < u.length; i++) {
      d += u[i] * v[i];
    }
    return d;
  }

  private class PQEntry implements Comparable<PQEntry> {
    PQEntry(float margin, int node) { this.margin = margin; this.node = node; }
    public float margin;
    public int node;

    @Override
    public int compareTo(PQEntry o) {
      if (o.margin == margin) return 0;
      return o.margin > margin ? 1 : -1;
    }
  }

  public List<Integer> getNearest(float[] queryVector, int nResults) {
    PriorityQueue<PQEntry> pq = new PriorityQueue<PQEntry>(
            roots.size()*4);
    float[] v = new float[dimension];
    for (int r : roots) {
      getNodeVector(r, v);
      pq.add(new PQEntry(1e30f, r));
    }

    Set<Integer> NNs = new HashSet<Integer>();
    while (NNs.size() < roots.size() * nResults && !pq.isEmpty()) {
      PQEntry top = pq.poll();
      int n = top.node;
      int nDescendants = annBuf.getInt(n);
      if (nDescendants == 1) {  // n_descendants
        // FIXME: does this ever happen?
        NNs.add(n / nodeSize);
      } else if (nDescendants <= minLeafSize) {
        for (int i = 0; i < nDescendants; i++) {
          int j = annBuf.getInt(n + 4 + i*4);
          NNs.add(j);
        }
      } else {
        getNodeVector(n, v);
        float margin = margin(v, queryVector);
        int lChild = nodeSize * annBuf.getInt(n+4);
        int rChild = nodeSize * annBuf.getInt(n+8);
        pq.add(new PQEntry(-margin, lChild));
        pq.add(new PQEntry( margin, rChild));
      }
    }
    PQEntry sortedNNs[] = new PQEntry[NNs.size()];
    int i = 0;
    for (int nn : NNs) {
      getItemVector(nn, v);
      sortedNNs[i++] = new PQEntry(margin(v, queryVector), nn);
    }
    Arrays.sort(sortedNNs);
    ArrayList<Integer> result = new ArrayList<Integer>(nResults);
    for (i = 0; i < nResults && i < sortedNNs.length; i++)
      result.add(sortedNNs[i].node);
    return result;
  }

  public static void main(String[] args) throws Exception {
    ANNIndex annIndex = new ANNIndex(Integer.parseInt(args[0]), args[1]);
    float[] u = new float[annIndex.dimension],
            v = new float[annIndex.dimension];
    int queryItem = Integer.parseInt(args[2]);
    annIndex.getItemVector(queryItem, u);
    System.out.printf("vector[%d]: ", queryItem);
    for (float x : u)
      System.out.printf("%2.2f ", x);
    System.out.printf("\n");
    List<Integer> NNs =  annIndex.getNearest(u, 10);
    for (int nn : NNs) {
      annIndex.getItemVector(nn, v);
      System.out.printf("%d %d %f\n", queryItem, nn, margin(u, v));
    }
  }

  public int dimension, minLeafSize, nodeSize;
  private ArrayList<Integer> roots;
  private RandomAccessFile memoryMappedFile;
  private MappedByteBuffer annBuf;
}
