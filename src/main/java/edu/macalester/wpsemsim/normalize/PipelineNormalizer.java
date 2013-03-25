package edu.macalester.wpsemsim.normalize;

import gnu.trove.list.array.TDoubleArrayList;

import java.util.List;

public class PipelineNormalizer implements Normalizer{
    private List<Normalizer> pipeline;
    private transient TDoubleArrayList X = new TDoubleArrayList();
    private transient TDoubleArrayList Y = new TDoubleArrayList();

    public PipelineNormalizer(List<Normalizer> pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public double normalize(double x) {
        for (Normalizer n : pipeline) {
            x = n.normalize(x);
        }
        return x;
    }

    @Override
    public void observe(double x, double y) {
        synchronized (X) { X.add(x); }
        synchronized (Y) { Y.add(x); }
    }

    @Override
    public void observe(double x) {
        synchronized (X) { X.add(x); }
    }

    @Override
    public void observationsFinished() {
        TDoubleArrayList currentX = new TDoubleArrayList(X);
        for (Normalizer n : pipeline) {
            for (int i = 0; i < currentX.size(); i++) {
                n.observe(currentX.get(i), Y.get(i));
            }
            n.observationsFinished();

            // update current x with settings
            for (int i = 0; i < currentX.size(); i++) {
                currentX.set(i, n.normalize(currentX.get(i)));
            }
        }
    }

    @Override
    public String dump() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
