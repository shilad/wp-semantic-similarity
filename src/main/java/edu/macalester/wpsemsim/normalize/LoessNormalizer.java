package edu.macalester.wpsemsim.normalize;

import gnu.trove.list.TDoubleList;
import gnu.trove.list.array.TDoubleArrayList;
import org.apache.commons.math3.analysis.interpolation.LoessInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import java.text.DecimalFormat;
import java.util.logging.Logger;

/**
 * Creates a Normalizer based on a polinomial spline fitting to binned points.
 */

public class LoessNormalizer extends BaseNormalizer {

    private static Logger LOG = Logger.getLogger(LoessNormalizer.class.getName());
    static final long serialVersionUID = -3423242;

    private TDoubleList X = new TDoubleArrayList();
    private TDoubleList Y = new TDoubleArrayList();

    transient private PolynomialSplineFunction interpolator = null;

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

    @Override
    public double normalize(double x) {
        synchronized (interpolator) {
            if (interpolator == null) {
                LOG.info("building interpolator");
                interpolator = new LoessInterpolator().interpolate(X.toArray(), Y.toArray());
            }
        }
        return interpolator.value(x);
    }

    @Override
    public String dump() {
        StringBuffer buff = new StringBuffer("loess normalizer");
        DecimalFormat df = new DecimalFormat("#.##");
        double points[] = new LoessInterpolator().smooth(X.toArray(), Y.toArray());
        for (int i = 0; i < 20; i++) {
            int j = points.length * 20 / i;
            buff.append(" <" +
                    df.format(X.get(j)) + "," +
                    df.format(points[j]) + ">");
        }
        return buff.toString();
    }
}
