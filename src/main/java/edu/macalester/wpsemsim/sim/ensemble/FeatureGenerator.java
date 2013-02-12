package edu.macalester.wpsemsim.sim.ensemble;

import edu.macalester.wpsemsim.normalize.BaseNormalizer;
import edu.macalester.wpsemsim.normalize.PercentileNormalizer;
import edu.macalester.wpsemsim.normalize.RangeNormalizer;
import edu.macalester.wpsemsim.sim.SimilarityMetric;

import java.io.IOException;
import java.io.ObjectInputStream;
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
    protected List<String> names = new ArrayList<String>();

    // list of normalizers for each component.
    // Each component's normalizers are a map of normalize name to normalizer.
    protected List<BaseNormalizer> rangeNormalizers = new ArrayList<BaseNormalizer>();
    protected List<BaseNormalizer> percentNormalizers = new ArrayList<BaseNormalizer>();
    protected int numResults = 0;
    protected boolean trained = false;

    /**
     * Set the components available to the feature generator.
     * After setComponents() is called, generate() will not be available
     * until the generator is trained.
     */
    public void setComponents(List<SimilarityMetric> components) {
        rangeNormalizers.clear();
        percentNormalizers.clear();
        numResults = 0;
        trained = false;
        names.clear();

        this.components = new ArrayList<SimilarityMetric>(components);
        for (SimilarityMetric m : components) {
            names.add(m.getName());
        }
    }

    /**
     * Given a fully trained generator (with or without a valid components attribute)
     * Prune down the components in the trained generator to those that are specified
     * by keepers. At the end of the method, components will be keepers, or a subset of keepers.
     *
     * @param keepers The similarity metrics that should be represented by the feature generator.
     *
     * @param prune If true, prune down the retained metrics to those in keepers.
     *              If false, require that all the metrics exist.
     */
    protected void setAndReorderMetrics(List<SimilarityMetric> keepers, boolean prune) {
        List<SimilarityMetric> pruned = new ArrayList<SimilarityMetric>();
        for (int i = 0; i < names.size();) {
            String name = names.get(i);
            SimilarityMetric metric = null;
            for (SimilarityMetric m : keepers) {
                if (m.getName().equals(name)) {
                    metric = m;
                    break;
                }
            }
            if (metric == null) {
                if (prune) removeComponent(i);
                else {
                    throw new IllegalArgumentException("missing metric: " + name);
                }
            } else {
                pruned.add(metric);
                i++;
            }
        }
        this.components = pruned;

        // make sure that the pruning was not insane
        assert(this.components.size() == this.names.size());
        assert(this.components.size() == this.rangeNormalizers.size());
        assert(this.components.size() == this.percentNormalizers.size());
        for (int i = 0; i < names.size(); i++) {
            assert(this.components.get(i).getName().equals(this.names.get(i)));
        }
    }

    protected void removeComponent(int i) {
        if (this.components != null) {
            components.remove(i);
        }
        names.remove(i);
        rangeNormalizers.remove(i);
        percentNormalizers.remove(i);
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
        trained = true;
    }

    public abstract LinkedHashMap<Integer, Double> generate(Example ex);

    public abstract List<String> getFeatureNames();

    public abstract String featureNameToMetricName(String featureName);

    public boolean hasFeature(String name) {
        return getFeatureNames().contains(name);
    }

    protected double percentileToScore(double p) {
        return (p - 0.5) * 2;
    }

    protected double rankToScore(double rank, int maxRank) {
        double maxLog = Math.log(maxRank + 2);
        double log = Math.log(rank + 1);
        return ((maxLog - log) / maxLog - 0.5) * 2;
    }

    /**
     * Reads in a feature generator from an object stream.
     *
     * , and prunes it down to the specified similarity metrics.
     * @param input the input stream
     * @param metrics the feature generator should be associated with the specified similar metrics
     * @param prune If true, prune the features down to those associated with the passed-in metrics.
     *              If false, require that the feature generator already represent exactly those specified metrics.
     * @return
     * @throws ClassNotFoundException
     * @throws IOException
     */
    public static FeatureGenerator read(ObjectInputStream input, List<SimilarityMetric> metrics, boolean prune) throws ClassNotFoundException, IOException {
        FeatureGenerator g = (FeatureGenerator) input.readObject();
        g.setAndReorderMetrics(metrics, prune);
        return g;
    }

    public int getFeatureIndex(String featureName) {
        return names.indexOf(featureName);
    }
}
