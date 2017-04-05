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

  private void testIndex(ANNIndex index, BufferedReader reader, boolean verbose)
          throws IOException {

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
   ones pre-computed by the C++ version of the Angular index.
   */
  public void testAngular() throws IOException {
    ANNIndex index = new ANNIndex(8, "src/test/resources/points.angular.annoy", IndexType.ANGULAR);
    BufferedReader reader = new BufferedReader(new FileReader("src/test/resources/points.angular.ann.txt"));
    testIndex(index, reader, false);
  }


  @Test
  /**
   Make sure that the NNs retrieved by the Java version match the
   ones pre-computed by the C++ version of the Euclidean index.
   */
  public void testEuclidean() throws IOException {
    ANNIndex index = new ANNIndex(8, "src/test/resources/points.euclidean.annoy", IndexType.EUCLIDEAN);
    BufferedReader reader = new BufferedReader(new FileReader("src/test/resources/points.euclidean.ann.txt"));
    testIndex(index, reader, false);
  }


  @Test(expected = RuntimeException.class)
  /**
   Make sure wrong dimension size used to init ANNIndex will throw RuntimeException.
   */
  public void testLoadFile() throws IOException {
    ANNIndex index = new ANNIndex(9, "src/test/resources/points.euclidean.annoy");
  }
}
