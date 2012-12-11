package edu.macalester.wpsemsim.sim.ensemble;

import edu.macalester.wpsemsim.utils.KnownSim;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * A collection of predictions from similarity metrics that can be used by an ensemble predictor.
 * Label may be null if it is not known.
 *
 * If this corresponds to {@link edu.macalester.wpsemsim.sim.SimilarityMetric#mostSimilar} reverse
 * sims will be null. If it corresponds to  {@link edu.macalester.wpsemsim.sim.SimilarityMetric#similarity}
 * then sims will capture similarity in one direction, reverse sims will be in the opposite direction.
 */
public class Example {
    KnownSim label;
    List<ComponentSim> sims;
    List<ComponentSim> reverseSims;

    public Example(KnownSim label, List<ComponentSim> sims) {
        this(label, sims, null);
    }

    public Example(List<ComponentSim> sims) {
        this(null, sims, null);
    }
    public Example(List<ComponentSim> sims, List<ComponentSim> reverseSims) {
        this(null, sims, reverseSims);
    }

    public Example(KnownSim label, List<ComponentSim> sims, List<ComponentSim> reverseSims) {
        this.label = label;
        this.sims = sims;
        this.reverseSims = reverseSims;
    }

    public void add(ComponentSim sim) {
        assert(reverseSims == null);
        this.sims.add(sim);
    }

    public void add(ComponentSim sim, ComponentSim reverse) {
        this.sims.add(sim);
        this.reverseSims.add(reverse);
    }

    public boolean hasReverse() {
        return reverseSims != null;
    }

    /*
    public Example toDense(List<ComponentSim> ifMissing) {
        List<ComponentSim> dense = new ArrayList<ComponentSim>();
        int j = 0;
        for (int i = 0; i < ifMissing.size(); i++) {
            if (j < sims.size() && sims.get(j).component == i) {
                dense.add(sims.get(j++));
            } else {
                dense.add(ifMissing.get(i));
            }
        }
        assert(j == sims.size());
        return new Example(dense, label);
    } */

    /**
     * @return The number of components that generated
     * a similarity score for the example.
     */
    public int getNumNotNan() {
        assert(reverseSims != null && sims.size() == reverseSims.size());
        int n = 0;
        for (int i = 0; i < sims.size(); i++) {
            if (sims.get(i).hasValue() || reverseSims.get(i).hasValue()) {
                n += 1;
            }
        }
        return n;
    }

    public static Example makeEmpty() {
        return new Example(new ArrayList<ComponentSim>());
    }
    public static Example makeEmptyWithReverse() {
        return new Example(new ArrayList<ComponentSim>(), new ArrayList<ComponentSim>());
    }
}
