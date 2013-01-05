package edu.macalester.wpsemsim.normalize;

import gnu.trove.list.array.TDoubleArrayList;
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.analysis.interpolation.LoessInterpolator;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;


/**
 * This class is called percentile normalizer, but it returns normalized values in [0,1].
 */
public class PercentileNormalizer extends BaseNormalizer {
    protected transient PolynomialSplineFunction interpolator;

    @Override
    public void observationsFinished() {
        super.observationsFinished();
        makeInterpolater();
    }

    protected void makeInterpolater() {
        TDoubleArrayList X = new TDoubleArrayList();
        TDoubleArrayList Y = new TDoubleArrayList();

        // save two "fake" sample observations worth of wiggle room for low and high out of range values.
        for (int i = 0; i < sample.size(); i++) {
            double fudge = max * 10E-8 * i;    // ensures monotonic increasing
            X.add(sample.get(i) + fudge);
            Y.add((i + 1.0) / (sample.size() + 1));
        }

        interpolator = new LinearInterpolator().interpolate(X.toArray(), Y.toArray());
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();

    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        makeInterpolater();
    }

    @Override
    public double normalize(double x) {
        double sMin = sample.get(0);
        double sMax = sample.get(sample.size() - 1);
        double halfLife = (sMax - sMin) / 4.0;
        double yDelta = 1.0 / (sample.size() + 1);

        if (x < sample.get(0)) {
            return toAsymptote(sMin - x, halfLife, yDelta, 0.0);
        } else if (x > sample.get(sample.size() - 1)) {
            return toAsymptote(x - sMax, halfLife, 1.0 - yDelta, 1.0);
        } else {
            return interpolator.value(x);
        }
    }

    @Override
    public double unnormalize(double y) {
        double sMin = sample.get(0);
        double sMax = sample.get(sample.size() - 1);
        double halfLife = (sMax - sMin) / 4.0;
        double yDelta = 1.0 / (sample.size() + 1);

        if (y < yDelta) {
            return sMin - (1 - Math.exp(y - yDelta)) * halfLife;
        } else if (y > 1.0 - yDelta) {
            return sMax + (1 - Math.exp(-(y - (1 - yDelta)))) * halfLife;
        } else {
            // transform x
            y = (y - yDelta) / (1.0 - 2 * yDelta);
            return stats.getPercentile(y * 100.0);
        }
    }

    public static double toAsymptote(double xDelta, double xHalfLife, double y0, double yInf) {
        assert(xDelta > 0);
        double hl = xDelta / xHalfLife;
        return y0 + (1.0 - Math.exp(-hl)) * (yInf - y0);
    }
}
