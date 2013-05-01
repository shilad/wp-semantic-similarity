package edu.macalester.wpsemsim.sim.ensemble;

import edu.macalester.wpsemsim.normalize.BaseNormalizer;
import edu.macalester.wpsemsim.sim.SimScore;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import gnu.trove.list.array.TDoubleArrayList;

import java.util.*;

/**
 * Generates numeric features for a component similarity example.
 *
 * Since it is targeted towards SimilarityMetric.similarity(), it presumes
 * that we are given a pair of words or articles.
 */
public class SimilarityFeatureGenerator extends FeatureGenerator {

    @Override
    public double[] generate(Example ex) {
        if (components == null) throw new NullPointerException("components not set");
        if (!ex.hasReverse()) {
            throw new UnsupportedOperationException();  // TODO: fixme
        }
        if (ex.sims.size() != components.size()) {
            throw new IllegalStateException();
        }

        TDoubleArrayList features = new TDoubleArrayList();

        for (int i = 0; i < ex.sims.size(); i++) {
            SimScore cs1 = ex.sims.get(i);
            SimScore cs2 = ex.reverseSims.get(i);
            assert(cs1.component == cs2.component);

            double s1 = getOrImputeSim(cs1);
            double s2 = getOrImputeSim(cs2);
            assert(!Double.isNaN(s1));   // it should be defined now!
            assert(!Double.isNaN(s2));   // it should be defined now!

            // range normalizer
            BaseNormalizer rn = rangeNormalizers.get(cs1.component);
            double r1 = rn.normalize(s1);
            double r2 = rn.normalize(s2);
            features.add(0.5 * r1 + 0.5 * r2);
            features.add(Math.max(r1, r2));

            // percent normalizer
            BaseNormalizer pn = percentNormalizers.get(cs1.component);
            double p1 = pn.normalize(s1);
            double p2 = pn.normalize(s2);
            features.add(percentileToScore(0.5 * p1 + 0.5 * p2));
            features.add(percentileToScore(Math.max(p1, p2)));
        }
        assert(features.size() == getNumFeatures());
        return features.toArray();
    }

    @Override
    public int getNumFeatures() {
        return components.size() * 4;
    }

    @Override
    public List<String> getFeatureNames() {
        List<String> names = new ArrayList<String>();
        for (SimilarityMetric m : components) {
            String metricName = m.getName().toLowerCase().replaceAll("[^a-zA-Z]+", "");
            names.add(metricName + "-rangemean");
            names.add(metricName + "-rangemax");
            names.add(metricName + "-percentmean");
            names.add(metricName + "-percentmax");
        }
        return names;
    }

    @Override
    public String featureNameToMetricName(String featureName) {
        int i = featureName.lastIndexOf("-");
        if (i >= 0) {
            return featureName.substring(0, i);
        } else {
            return featureName;
        }
    }
}
