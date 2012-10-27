package edu.macalester.wpsemsim.utils;

import gnu.trove.set.hash.TIntHashSet;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TestLeaderboard {
    int K = 1000;
    int Ns[] = { K * 100, K + 1, K, K - 1, K/2, 1, 0 };

    Random rand = new Random();

    @Test
    public void testBig() {
        for (int n : Ns) {
            testSize(n, K);
        }
    }
    public void testSize(int n, int k) {
        System.err.println("n, k is " + n + ", " + k);
        Leaderboard top = new Leaderboard(k);
        Map<Integer, Double> vals = new HashMap<Integer, Double>();
        for (int id : pickIds(n)) {
            double val = rand.nextDouble();
            top.tallyScore(id, val);
            vals.put(id, val);
        }
        List<Map.Entry<Integer, Double>> entries = new ArrayList(vals.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<Integer, Double>>() {
            @Override
            public int compare(Map.Entry<Integer, Double> e1, Map.Entry<Integer, Double> e2) {
                return - e1.getValue().compareTo(e2.getValue());
            }
        });
        DocScoreList topScores = top.getTop();
        assertEquals(topScores.numDocs(), Math.min(k, n));
        for (int i = 0; i < Math.min(k, n); i++) {
            assertEquals(entries.get(i).getKey().intValue() , topScores.getId(i));
            assertEquals(entries.get(i).getValue().floatValue(), topScores.getScore(i), 0.00001);
        }
    }

    private int[] pickIds(int numIds) {
        TIntHashSet picked = new TIntHashSet();
        for (int i = 0; i < numIds; i++) {
            while (true) {
                int id = rand.nextInt(numIds * 100);
                if (!picked.contains(id)) {
                    picked.add(id);
                    break;
                }
            }
        }
        return picked.toArray();
    }

}
