package com.spotify.annoy;

import static org.junit.Assert.assertEquals;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.List;

@RunWith(JUnit4.class)
public class ANNIndexTest {
  @Test
  public void testSampleIndex() {
    try {
      ANNIndex annIndex = new ANNIndex(5, "src/test/resources/test-index.tree");
      float u[] = new float[5],
            v[] = new float[5];
      annIndex.getItemVector(0, u);
      List<Integer> NNs = annIndex.getNearest(u, 10);
      // there are 100 items in the test index, so we should definitely fill our request for 10
      assertEquals(NNs.size(), 10);
      // the most similar item should be the item we started with
      assertEquals((int) NNs.get(0), 0);
      // just for fun, print 'em
      for (int nn : NNs) {
        annIndex.getItemVector(nn, v);
        System.out.printf("%d %f\n", nn, ANNIndex.cosineMargin(u, v));
      }
    } catch (Exception e) {
      Assert.fail(e.getMessage());
    }
  }

  public void benchmarkAccuracy() {
    try {
      ANNIndex annIndex = new ANNIndex(40, "vector_exp-artist-40.tree");
      float[] v = new float[40],
              u = new float[40];
      float accuracysum = 0;
      for (int i = 0; i < 100000; i += 100) {
        annIndex.getItemVector(i, v);
        List<Integer> NNs = annIndex.getNearest(v, 100);
        float marginsum = 0;
        for (int nn : NNs) {
          annIndex.getItemVector(nn, u);
          marginsum += ANNIndex.cosineMargin(u, v);
        }
        accuracysum += marginsum / NNs.size();
      }
      System.err.printf("avg cosine dist: %f\n", accuracysum / 1000);
    } catch (IOException e) {
      Assert.fail(e.getMessage());
    }
  }
}
