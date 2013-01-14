package edu.macalester.wpsemsim.sim.ensemble;

import edu.macalester.wpsemsim.normalize.BaseNormalizer;
import edu.macalester.wpsemsim.normalize.PercentileNormalizer;
import edu.macalester.wpsemsim.normalize.RangeNormalizer;
import edu.macalester.wpsemsim.sim.SimilarityMetric;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Base class for MostSimilar and Similarity feature generators.
 */
public abstract class FeatureGenerator implements Serializable {
    // don't serialize the components they must be set manually.
    protected transient List<SimilarityMetric> components = null;
    // list of normalizers for each component.
    // Each component's normalizers are a map of normalize name to normalizer.
    protected List<BaseNormalizer> rangeNormalizers = new ArrayList<BaseNormalizer>();
    protected List<BaseNormalizer> percentNormalizers = new ArrayList<BaseNormalizer>();
    protected int numResults = 0;

    public void setComponents(List<SimilarityMetric> components) {
        this.components = components;   // the ordering for this better not change!
    }

    public void train(List<Example> examples) {
        if (components == null) throw new NullPointerException("components not set");
        for (int i = 0; i < components.size(); i++) {
            rangeNormalizers.add(new RangeNormalizer(-1, +1, false));
            percentNormalizers.add(new PercentileNormalizer());
        }
        for (Example ex : examples) {
            List<ComponentSim> allSims = new ArrayList<ComponentSim>(ex.sims);
            if (ex.hasReverse()) allSims.addAll(ex.reverseSims);
            for (ComponentSim s : allSims) {
                numResults = Math.max(numResults, s.length);
                if (s.scores != null) {
                    for (float x : s.scores) {
                        if (!Double.isNaN(x) && !Double.isInfinite(x)) {
                            rangeNormalizers.get(s.component).observe(x);
                            percentNormalizers.get(s.component).observe(x);
                        }
                    }
                }
            }
        }

        for (BaseNormalizer n : rangeNormalizers) { n.observationsFinished(); }
        for (BaseNormalizer n : percentNormalizers) { n.observationsFinished(); }
    }

    public abstract LinkedHashMap<Integer, Double> generate(Example ex);

    public abstract List<String> getFeatureNames();

    protected double percentileToScore(double p) {
        return (p - 0.5) * 2;
    }

    protected double rankToScore(double rank, int maxRank) {
        double maxLog = Math.log(maxRank + 2);
        double log = Math.log(rank + 1);
        return ((maxLog - log) / maxLog - 0.5) * 2;
    }
}
