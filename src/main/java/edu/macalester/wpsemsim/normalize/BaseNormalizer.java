package edu.macalester.wpsemsim.normalize;

import gnu.trove.list.array.TDoubleArrayList;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.Serializable;
import java.util.Random;

/**
 * A class that supports various kinds of normalization.
 * Usage:
 * 1. Create the normalizer.
 * 2. Call observe with each observation.
 * 3. Call finalize.
 * 4. Call normalize() on a new datapoint.
 */
public abstract class BaseNormalizer implements Serializable, Normalizer {
    public final static int SAMPLE_SIZE = 1000;

    public double min = Double.MIN_VALUE;
    protected double max = -Double.MAX_VALUE;

    // After calling finalize, stats will be non-null.
    protected TDoubleArrayList sample = new TDoubleArrayList();
    protected DescriptiveStatistics stats;

    protected int numObservations = 0;
    protected Random random = new Random();

    private boolean Supervised=false;
    private boolean NeedsTraining=true;

    public boolean isSupervised(){
        return Supervised;
    };

    public boolean needsTraining(){
        return NeedsTraining;
    }

    /**
     * To meet the serializable contract.
     */
    protected BaseNormalizer() {}

    @Override
    public void observe(double x, double y){
        observe(x);
    }

    @Override
    public void observe(double x) {
        if (!Double.isNaN(x)) {
            if (x < min) { min = x; }
            if (x > max) { max = x; }
        }
        if (sample.size() < SAMPLE_SIZE) {
            sample.add(x);
        } else if (random.nextDouble() < 1.0 * sample.size() / (numObservations + 1)) {
            sample.set(random.nextInt(sample.size()),  x);
        }
        numObservations++;
    }

    @Override
    public void observationsFinished() {
        sample.sort();
        stats = new DescriptiveStatistics(sample.toArray());
    }

    public String toString() { return "min=" + min + ", max=" + max; }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }
}
