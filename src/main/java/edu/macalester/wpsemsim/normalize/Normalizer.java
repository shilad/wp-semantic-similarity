package edu.macalester.wpsemsim.normalize;

import java.io.Serializable;

public interface Normalizer extends Serializable {
    public double normalize(double x);
    public double unnormalize(double x);
    public void observe(double x, double y);
    public void observe(double x);
    public void observationsFinished();
    public boolean isSupervised();
    public boolean needsTraining();
}
