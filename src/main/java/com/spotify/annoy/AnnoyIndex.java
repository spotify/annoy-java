package com.spotify.annoy;

import java.util.List;

/**
 * AnnoyIndex interface, provided to aid with dependency injection in tests.
 */
public interface AnnoyIndex {
  /**
   * Get the vector for a given node in the tree.
   * @param node  node index in the ANN tree
   * @param v     output vector; overwritten.
   */
  void getNodeVector(int node, float[] v);

  /**
   * Get the vector for a given item in the tree.
   * @param item  item id
   * @param v     output vector; overwritten.
   */
  void getItemVector(int item, float[] v);

  /**
   * Look up nearest neighbors in the tree.
   * @param queryVector  find nearest neighbors for this query point
   * @param nResults     number of items to return
   * @return             list of items in descending nearness to query point
   */
  List<Integer> getNearest(float[] queryVector, int nResults);
}
