package edu.macalester.wpsemsim.normalize;

public class IdentityNormalizer implements Normalizer{

    @Override
    public double normalize(double x) { return x; }

    @Override
    public double unnormalize(double x) { return x; }

    @Override
    public void observe(double x) {}

    @Override
    public void observationsFinished() {}
}
