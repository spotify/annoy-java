package com.spotify.annoy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@RunWith(JUnit4.class)
public class ANNIndexTest {

  private static final String DIR = "src/test/resources";

  private void testIndex(IndexType type, int blockSize, boolean verbose)
          throws IOException {

    String ts = type.toString().toLowerCase();
    ANNIndex index = new ANNIndex(8,
            String.format("%s/points.%s.annoy", DIR, ts), type, blockSize);
    BufferedReader reader = new BufferedReader(new FileReader(
            String.format("%s/points.%s.ann.txt", DIR, ts)));

    while (true) {

      // read in expected results from file (precomputed from c++ version)
      String line = reader.readLine();
      if (line == null)
        break;
      String[] _l = line.split("\t");
      Integer queryItemIndex = Integer.parseInt(_l[0]);
      List<Integer> expectedResults = new LinkedList<>();
      for (String _i : _l[1].split(","))
        expectedResults.add(Integer.parseInt(_i));

      // do the query
      float[] itemVector = index.getItemVector(queryItemIndex);
      List<Integer> retrievedResults = index.getNearest(itemVector, 10);

      if (verbose) {
        System.out.println(String.format("query: %d", queryItemIndex));
        for (int i = 0; i < 10; i++)
          System.out.println(String.format("expected %6d retrieved %6d",
                  expectedResults.get(i),
                  retrievedResults.get(i)));
        System.out.println();
      }

      // results will not match exactly, but at least 5/10 should overlap
      Set<Integer> totRes = new TreeSet<>();
      totRes.addAll(expectedResults);
      totRes.retainAll(retrievedResults);
      assert (totRes.size() >= 5);

    }
  }

  @Test
  /**
   Make sure that the NNs retrieved by the Java version match the
   ones pre-computed by the C++ version of the Angular index
   using the default block size (for files up to 2GB).
   */
  public void testAngular() throws IOException {
    testIndex(IndexType.ANGULAR, 0, false);
  }


  @Test
  /**
   Make sure that the NNs retrieved by the Java version match the
   ones pre-computed by the C++ version of the Euclidean index
   using the default block size (for files up to 2GB).
   */
  public void testEuclidean() throws IOException {
    testIndex(IndexType.EUCLIDEAN, 0, false);
  }

  @Test
  /**
   Make sure that the NNs retrieved by the Java version match the
   ones pre-computed by the C++ version of the Angular index
   simulating files larger than 2GB.
   */
  public void testAngularBlocks() throws IOException {
    testIndex(IndexType.ANGULAR, 10, false);
    testIndex(IndexType.ANGULAR, 1, false);
  }


  @Test
  /**
   Make sure that the NNs retrieved by the Java version match the
   ones pre-computed by the C++ version of the Euclidean index
   simulating files larger than 2GB.
   */
  public void testEuclideanMultipleBlocks() throws IOException {
    testIndex(IndexType.EUCLIDEAN, 10, false);
    testIndex(IndexType.EUCLIDEAN, 1, false);
  }

  @Test(expected = RuntimeException.class)
  /**
   Make sure wrong dimension size used to init ANNIndex will throw RuntimeException.
   */
  public void testLoadFileWithWrongDimension() throws IOException {
    ANNIndex index = new ANNIndex(7, "src/test/resources/points.euclidean.annoy");
  }

  @Test(expected = RuntimeException.class)
  /**
   Make sure wrong dimension size throw exception in getNearest()
   */
  public void testGetNearesWithWrongDim() throws IOException {
    ANNIndex index = new ANNIndex(8, "src/test/resources/points.angular.annoy", IndexType.ANGULAR);
    float[] u = {0f, 1.0f, 0.2f, 0.1f, 0f, 1.0f, 0.2f, 0.1f, 1f};
    index.getNearest(u, 10);
  }
}
