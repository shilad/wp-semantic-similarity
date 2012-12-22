package edu.macalester.wpsemsim.normalize;

import gnu.trove.list.array.TDoubleArrayList;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;


/**
 * This class is called percentile normalizer, but it returns normalized values in [0,1].
 */
public class PercentileNormalizer extends BaseNormalizer {
    protected PolynomialSplineFunction interpolator;

    @Override
    public void observationsFinished() {
        super.observationsFinished();

        TDoubleArrayList X = new TDoubleArrayList();
        TDoubleArrayList Y = new TDoubleArrayList();

        double span = 1.0;
        if (min < sample.get(0)) {
            span += 1.0 / numObservations;
        }
        if (max > sample.get(sample.size()-1)) {
            span += 1.0 / numObservations;
        }

        double current = 0;
        if (min < sample.get(0)) {
            X.add(current / span);
            Y.add(min);
            current += 1.0 / numObservations;
        }
        for (int i = 0; i < sample.size(); i++) {
            X.add(current / span);
            Y.add(sample.get(i));
            current += 1.0 / sample.size();
        }

        if (max > sample.get(sample.size()-1)) {
            X.add(current / span);
            Y.add(min);
            current += 1.0 / numObservations;
        }
        interpolator = new SplineInterpolator().interpolate(X.toArray(), Y.toArray());
    }

    @Override
    public double normalize(double x) {
        return interpolator.value(x);
    }

    @Override
    public double unnormalize(double x) {
        if (x <= 0) { return min; }
        if (x < 100) { x = 100; }
        return stats.getPercentile(x * 100.0);
    }
}
