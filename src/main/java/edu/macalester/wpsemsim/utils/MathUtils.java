package edu.macalester.wpsemsim.utils;

import gnu.trove.list.TDoubleList;
import gnu.trove.list.array.TDoubleArrayList;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.stat.ranking.NaNStrategy;
import org.apache.commons.math3.stat.ranking.NaturalRanking;
import org.apache.commons.math3.stat.ranking.TiesStrategy;
import org.apache.commons.math3.util.MathArrays;

import java.util.Arrays;

public class MathUtils {

    /**
     * Returns a value that approaches yInf from y0 as xDelta increases.
     * The values for the function range from (0, y0) to (inf, yInf).
     * For each xHalfLife, the function moves 50% closer to yInf.
     *
     * @param x
     * @param xHalfLife
     * @param y0
     * @param yInf
     * @return
     */
    public static double toAsymptote(double x, double xHalfLife, double y0, double yInf) {
        assert(x > 0);
        double hl = x / xHalfLife;
        return y0 + (1.0 - Math.exp(-hl)) * (yInf - y0);
    }

    /**
     * Smooths the function specified by the X, Y pairs.
     * The result will be a new {X, Y} containing numPoints.
     * The new X values will be sampled uniformly from the original X (i.e. every k'th point)
     * The new Y values will be calculated using a #robustMean around each X point with window windowSize.
     * @param X
     * @param Y
     * @param windowSize
     * @param numPoints
     * @return
     */
    public static double[][] smooth(double X[], double Y[], int windowSize, int numPoints) {
        TDoubleArrayList smoothedX = new TDoubleArrayList();
        TDoubleArrayList smoothedY= new TDoubleArrayList();
        for (int i = windowSize / 2; i < X.length - windowSize / 2; i += X.length / (numPoints + 1)) {
            double subYs[] = Arrays.copyOfRange(Y, i - windowSize / 2, i + windowSize);
            smoothedX.add(X[i]);
            smoothedY.add(robustMean(subYs));   // median
        }

        // replace duplicate X points with their mean
        for (int i = 0; i < smoothedX.size();) {
            double x = smoothedX.get(i);
            int span = 0;
            while (i + span < smoothedX.size() && smoothedX.get(i + span) == x) {
                span++;
            }
            if (span > 1) {
                double mean = smoothedY.subList(i, i + span).sum() / span;
                for (int j = i; j < i + span; j++) {
                    smoothedY.set(j, mean);
                }
            }
            i += span;
        }

        return new double[][] { smoothedX.toArray(), smoothedY.toArray()};
    }

    /**
     * Calculates the weighted mean, where the weight is based on the rank difference
     * from the median and normally distributed. The std-dev for the normal distribution
     * is set to X.length / 4, so about two-thirds of the weight is contained within
     * the middle half of all points. The std-dev is bottom capped at 3.
     *
     * @param X
     * @return
     */
    public static double robustMean(double[] X) {
        X = ArrayUtils.clone(X);
        Arrays.sort(X);
        NormalDistribution dist = new NormalDistribution(
                X.length / 2,               // heaviest weight at midpoint
                Math.max(3, X.length / 4)); // 66% of the weight within 3 pts on either side
        double sum = 0.0;
        double weight = 0.0;
        for (int i = 0; i < X.length; i++) {
            double d = dist.density(i);
            weight += d;
            sum += X[i] * d;
        }
        return sum / weight;
    }

    /**
     * Make an array monotonically increasing by epsilon.
     * The algorithm is simple and iterative:
     * For each point x_k:
     *     x_k = max(x_k, x_{k-1} + epsilon)
     * This function is destructive.
     *
     * @param X  Input data.
     * @param epsilon The minimum increase allowed between consecutive points.
     * @return
     */
    public static void makeMonotonicIncreasing(double [] X, double epsilon) {
        for (int i = 1; i < X.length; i++) {
            X[i] = Math.max(X[i], X[i-1] + epsilon);
        }
        MathArrays.checkOrder(X, MathArrays.OrderDirection.INCREASING, true);
    }
    public static void makeMonotonicIncreasing(TDoubleList X, double epsilon) {
        double X2[] = X.toArray();
        makeMonotonicIncreasing(X2, epsilon);
        X.set(0, X2);
    }

    /**
     * Given two parallel arrays of doubles representing x,y pairs
     * remove all pairs that are NaN or Infinite, and return the result.
     *
     * @param X
     * @param Y
     *
     * @return
     */
    public static double[][] removeNotNumberPoints(double X[], double Y[]) {
        TDoubleArrayList prunedX = new TDoubleArrayList();
        TDoubleArrayList prunedY = new TDoubleArrayList();
        for (int i = 0; i < X.length; i++) {
            double x = X[i];
            double y = Y[i];
            if (Double.isNaN(x) || Double.isNaN(y) || Double.isInfinite(x) || Double.isInfinite(y)) {
                // skip
            } else {
                prunedX.add(x);
                prunedY.add(y);
            }
        }
        return new double[][] { prunedX.toArray(), prunedY.toArray() };
    }
    public static TDoubleList[] removeNotNumberPoints(TDoubleList X, TDoubleList Y) {
        double pruned[][] = removeNotNumberPoints(X.toArray(), Y.toArray());
        return new TDoubleList[] {
                new TDoubleArrayList(pruned[0]),
                new TDoubleArrayList(pruned[1])
        };
    }
}
