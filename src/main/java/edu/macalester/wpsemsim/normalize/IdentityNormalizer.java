package edu.macalester.wpsemsim.normalize;

public class IdentityNormalizer extends BaseNormalizer{
    private boolean NeedsTraining=false;

    @Override
    public double normalize(double x) { return x; }

    @Override
    public void observe(double x, double y){}

    @Override
    public void observe(double x) {}

    @Override
    public void observationsFinished() {}
}
