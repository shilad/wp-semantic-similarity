package edu.macalester.wpsemsim.normalize;

import gnu.trove.list.TDoubleList;
import gnu.trove.list.array.TDoubleArrayList;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.LoessInterpolator;
import org.apache.commons.math3.distribution.NormalDistribution;

import java.text.DecimalFormat;
import java.util.*;
import java.util.logging.Logger;

/**
 * Normalizes in two steps:
 * 1. Create a smoothed weighted average defined over a sample of the observed points.
 * 2. Creates a local linear spline fitted to smoothed points.
 */

public class LoessNormalizer extends BaseNormalizer {

    private static Logger LOG = Logger.getLogger(LoessNormalizer.class.getName());
    static final long serialVersionUID = -34232429;

    private TDoubleList X = new TDoubleArrayList();
    private TDoubleList Y = new TDoubleArrayList();
    private boolean logTransform = false;

    transient private double interpolatorMin;
    transient private double interpolatorMax;
    transient private UnivariateFunction interpolator = null;
    transient private boolean isSorted = false;

    @Override
    public void observe(double x, double y){
        if (!Double.isNaN(y) && !Double.isInfinite(y)) {
            X.add(x);
            Y.add(y);
        }
    }

    @Override
    public void observationsFinished(){
        // lazily initialized to overcome problems
        // with PolynomialSplineFunction serialization.
    }

    private static final double EPSILON = 1E-10;
    private synchronized void sortByX() {
        if (isSorted) {
            return;
        }
        List<Integer> indexes = new ArrayList<Integer>();
        for (int  i = 0; i < X.size(); i++) { indexes.add(i); }
        Collections.sort(indexes, new Comparator<Integer>() {
            @Override
            public int compare(Integer i, Integer j) {
                return new Double(X.get(i)).compareTo(X.get(j));
            }
        });
        TDoubleList sortedX = new TDoubleArrayList();
        TDoubleList sortedY = new TDoubleArrayList();

        // Add the sorted X and Y values. Take care to ensure Xs strictly increase.
        double lastX = - Double.MAX_VALUE;
        for (int i : indexes) {
            sortedX.add(Math.max(lastX + EPSILON, X.get(i)));
            sortedY.add(Y.get(i));
            lastX = sortedX.get(sortedX.size() - 1);
        }
        X = sortedX;
        Y = sortedY;
        isSorted = true;
    }

    @Override
    public double normalize(double x) {
        x = logIfNeeded(x);
        double sMin = interpolatorMin;
        double sMax = interpolatorMax;

        double x2;
        if (sMin <= x && x <= sMax) {
            x2 = getInterpolationFunction().value(x);
        } else {
            double yMin = getInterpolationFunction().value(sMin);
            double yMax = getInterpolationFunction().value(sMax);
            double halfLife = (sMax - sMin) / 4.0;
            double yDelta = 0.1 * (yMax - yMin);
            if (x < sMin) {
                x2 =  toAsymptote(sMin - x, halfLife, yMin, yMin - yDelta);
            } else if (x > sMax) {
                x2 = toAsymptote(x - sMax, halfLife, yMax, yMax + yDelta);
            } else {
                throw new IllegalStateException();
            }
        }
        return x2;
    }



    public static double toAsymptote(double xDelta, double xHalfLife, double y0, double yInf) {
        assert(xDelta > 0);
        double hl = xDelta / xHalfLife;
        return y0 + (1.0 - Math.exp(-hl)) * (yInf - y0);
    }

    private synchronized  UnivariateFunction getInterpolationFunction() {
        if (interpolator != null) {
            return interpolator;
        }
        sortByX();
        double smoothed[][] = smooth(
                logIfNeeded(X.toArray()),
                Y.toArray(),
                Math.max(10, X.size() / 25),
                50);
        double smoothedX[] = smoothed[0];
        double smoothedY[] = smoothed[1];
        interpolatorMin = smoothedX[0];
        interpolatorMax = smoothedX[smoothedX.length - 1];

//        DecimalFormat df = new DecimalFormat("#.##");
//        for (int i = 0; i < smoothedX.length; i++) {
//            System.out.println("" + i + ". " + df.format(smoothedX[i]) + ", " + df.format(smoothedY[i]));
//        }
        return new LoessInterpolator().interpolate(smoothedX, smoothedY);
    }

    private static double[][] smooth(double X[], double Y[], int windowSize, int numPoints) {
        TDoubleArrayList smoothedX = new TDoubleArrayList();
        TDoubleArrayList smoothedY= new TDoubleArrayList();
        for (int i = windowSize / 2; i < X.length - windowSize / 2; i += X.length / (numPoints + 1)) {
            double subYs[] = Arrays.copyOfRange(Y, i - windowSize / 2, i + windowSize);
            smoothedX.add(X[i]);
            smoothedY.add(robustMean(subYs));   // median
        }
        return new double[][] { smoothedX.toArray(), smoothedY.toArray()};
    }

    private static double robustMean(double[] X) {
        Arrays.sort(X);
        NormalDistribution dist = new NormalDistribution(
                X.length / 2,               // heaviest weight at midpoint
                Math.max(3, X.length / 6)); // 66% of the weight within 3 pts on either side
        double sum = 0.0;
        double weight = 0.0;
        for (int i = 0; i < X.length; i++) {
            weight += dist.density(i);
            sum += X[i] * dist.density(i);
        }
        return sum / weight;
    }

    private double logIfNeeded(double x) {
        if (logTransform) {
            sortByX();
            return Math.log(1 + X.get(0) + x);
        } else {
            return x;
        }
    }

    private double[] logIfNeeded(double X[]) {
        if (logTransform) {
            sortByX();
            double X2[] = new double[X.length];
            for (int i = 0; i < X.length; i++) {
                X2[i] = logIfNeeded(X[i]);
            }
            return X2;
        } else {
            return X;
        }
    }

    @Override
    public String dump() {
        StringBuffer buff = new StringBuffer("loess normalizer");
        if (logTransform) buff.append(" (log'ed)");
        DecimalFormat df = new DecimalFormat("#.##");
        sortByX();
        UnivariateFunction func = getInterpolationFunction();
        double sMin = interpolatorMin;
        double sMax = interpolatorMax;
        for (int i = 0; i < 100; i++) {
            double x = sMin + (sMax - sMin) * i / 100;
            buff.append(" <" +
                    df.format(x) + "," +
                    df.format(func.value(x)) + ">");
        }
        return buff.toString();
    }

    public void setLogTransform(boolean b) {
        this.logTransform = b;
    }

    public boolean getLogTransform() {
        return logTransform;
    }
}
