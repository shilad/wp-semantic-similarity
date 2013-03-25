package edu.macalester.wpsemsim.normalize;

/**
 * A simple normalizer that returns log(1 + min-value).
 * In the case that an x is observed that is less than min-value, it returns 0.
 */
public class LogNormalizer implements Normalizer{
    private double c;

    @Override
    public double normalize(double x) {
        if (x < c) {
            return 0;
        } else {
            return Math.log(c + x);
        }
    }

    @Override
    public void observe(double x, double y) {
        observe(x);
    }

    @Override
    public void observe(double x) {
        c = Math.min(x, 1 + c);
    }

    @Override
    public void observationsFinished() { }

    @Override
    public String dump() {
        return "log normalizer: log(" + c + " + x)";
    }
}
