package edu.macalester.wpsemsim.utils;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class TestMathUtils {
    @Test
    public void testFindColinearColumns() {
        testMatrix(
                new double[][]{
                        {1.0, 2.0, 3.0},
                        {1.0, 1.0, 4.0},
                        {3.0, 2.0, 34.0},
                        {8.0, 21.0, 3.0},
                },
                new int[][] {{}, {}, {}}
            );
        testMatrix(
                new double[][]{
                        {1.0, 0.0},
                        {2.0, 0.0},
                        {4.0, 0.0},
                },
                new int[][] {{1}, {}}
        );
        testMatrix(
                new double[][]{
                        {1.0, 0.0, 9.0},
                        {2.0, 0.0, 11.0},
                        {4.0, 0.0, 1.0},
                },
                new int[][] {{1}, {}, {}}
        );
        testMatrix(
                new double[][]{
                        {1.0, 2.0},
                        {2.0, 4.0},
                        {4.0, 8.0},
                },
                new int[][] {{1}, {}}
        );
        testMatrix(
                new double[][]{
                        {1.0, 2.0, 0.5},
                        {2.0, 4.0, 1.0},
                        {4.0, 8.0, 2.0},
                },
                new int[][] {{1,2}, {}, {}}
        );
        testMatrix(
                new double[][]{
                        {1.3, 2.0, 0.5},
                        {2.0, 4.0, 1.0},
                        {4.0, 8.0, 2.0},
                },
                new int[][] {{}, {2}, {}}
        );
    }

    public void testMatrix(double X[][], int expectedColinear[][]) {
        int colinear[][] = MathUtils.findColinearColumns(X);
        assertEquals(colinear.length, expectedColinear.length);
        for (int i = 0; i < colinear.length; i++) {
            assertTrue(
                    "expected " + MathUtils.toString(expectedColinear) +
                    ", received " + MathUtils.toString(colinear),
                    Arrays.equals(colinear[i], expectedColinear[i]));
        }
    }
}
