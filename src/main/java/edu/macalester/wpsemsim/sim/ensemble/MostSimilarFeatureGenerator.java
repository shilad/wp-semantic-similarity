package edu.macalester.wpsemsim.sim.ensemble;

import edu.macalester.wpsemsim.normalize.BaseNormalizer;
import edu.macalester.wpsemsim.sim.SimScore;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import gnu.trove.list.array.TDoubleArrayList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

/**
 * Generates numeric features for a component similarity example.
 *
 * Since it is targeted towards SimilarityMetric.mostSimilar(), it presumes
 * it will have only one ranked list of similarities.
 */
public class MostSimilarFeatureGenerator extends FeatureGenerator {
    protected Random random = new Random();

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
            SimScore cs = (!ex.hasReverse() || random.nextFloat() >= 0.5)
                            ? ex.sims.get(i)
                            : ex.reverseSims.get(i);

            double x = getOrImputeSim(cs);
            assert(!Double.isNaN(x));   // it should be define dnow!

            // range normalizer
            BaseNormalizer rn = rangeNormalizers.get(cs.component);
            features.add(rn.normalize(x));

            // percent normalizer
            BaseNormalizer pn = percentNormalizers.get(cs.component);
            double p = cs.hasValue() ? pn.normalize(x) : -0.2;
            features.add(percentileToScore(p));

                // log rank (mean and min)
            int rank = cs.hasValue() ? cs.rank : numResults * 2;
            features.add(rankToScore(rank, numResults * 2));
        }
        assert(features.size() == getNumFeatures());
        return features.toArray();
    }

    @Override
    public int getNumFeatures() {
        return components.size() * 3;
    }

    @Override
    public List<String> getFeatureNames() {
        List<String> names = new ArrayList<String>();
        for (SimilarityMetric m : components) {
            String metricName = m.getName().toLowerCase().replaceAll("[^a-zA-Z]+", "");
            names.add(metricName + "-range");
            names.add(metricName + "-percent");
            names.add(metricName + "-rank");
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
