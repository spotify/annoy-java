package com.spotify.annoy;

import java.io.IOException;
import java.util.Random;


// assumes there are 1M points in the index

public class SpeedTest  {

  public static void testSpeed(
    String indexPath,
    Integer dimension,
    Integer nQueries) throws IOException {

    ANNIndex index = new ANNIndex(dimension, indexPath);

    Random r = new Random();
    // float[] itemVector= new float[dimension];

    long tStart = System.currentTimeMillis();
    for(int i = 0; i < nQueries; i++) {
      int k = Math.abs(r.nextInt() % 1000000);
      // System.out.println("querying with item " + k);
      float[] itemVector = index.getItemVector(k);
      index.getNearest(itemVector, 10);
    }
    long tEnd = System.currentTimeMillis();

    System.out.println(
      String.format("Total time elapsed: %.3fs",
		    (tEnd - tStart) / 1000.));
    System.out.println(
      String.format("Avg. time per query: %.3fms",
		    (tEnd - tStart) / ((float) nQueries)));

  }

  public static void main(String[] args) throws IOException {
    String indexPath = args[0];
    Integer dimension = Integer.parseInt(args[1]);
    Integer nQueries = Integer.parseInt(args[2]);
    testSpeed(indexPath, dimension, nQueries);
  }

}
