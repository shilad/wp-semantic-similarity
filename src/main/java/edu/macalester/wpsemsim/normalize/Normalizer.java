package edu.macalester.wpsemsim.normalize;

import edu.macalester.wpsemsim.utils.DocScoreList;

import java.io.Serializable;

public interface Normalizer extends Serializable {
    public DocScoreList normalize(DocScoreList list);
    public double normalize(double x);

    public void observe(DocScoreList sims, int rank, double y);
    public void observe(double x, double y);
    public void observe(double x);
    public void observationsFinished();
    public String dump();
}
