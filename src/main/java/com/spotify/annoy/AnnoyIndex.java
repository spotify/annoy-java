package com.spotify.annoy;

import java.util.List;

public interface AnnoyIndex {

    public void getNodeVector(int node, float[] v);

    public void getItemVector(int item, float[] v);

    public List<Integer> getNearest(float[] queryVector, int nResults);
}
