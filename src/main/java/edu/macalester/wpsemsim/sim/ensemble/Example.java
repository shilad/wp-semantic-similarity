package edu.macalester.wpsemsim.sim.ensemble;

import edu.macalester.wpsemsim.sim.SimScore;
import edu.macalester.wpsemsim.utils.KnownSim;

import java.util.ArrayList;
import java.util.List;

/**
 * A collection of predictions from similarity metrics that can be used by an ensemble predictor.
 * Label may be null if it is not known.
 *
 * If this corresponds to {@link edu.macalester.wpsemsim.sim.SimilarityMetric#mostSimilar} reverse
 * sims will be null. If it corresponds to  {@link edu.macalester.wpsemsim.sim.SimilarityMetric#similarity}
 * then sims will capture similarity in one direction, reverse sims will be in the opposite direction.
 *
 * The component sims arrays are dense. They will always have size equal to the number of components.
 * If reverseSims is not null, it's size will equal sims.
 */
public class Example {
    KnownSim label;
    List<SimScore> sims;
    List<SimScore> reverseSims;

    public Example(KnownSim label, List<SimScore> sims) {
        this(label, sims, null);
    }

    public Example(List<SimScore> sims) {
        this(null, sims, null);
    }
    public Example(List<SimScore> sims, List<SimScore> reverseSims) {
        this(null, sims, reverseSims);
    }

    public Example(KnownSim label, List<SimScore> sims, List<SimScore> reverseSims) {
        this.label = label;
        this.sims = sims;
        this.reverseSims = reverseSims;
    }

    public void add(SimScore sim) {
        assert(reverseSims == null);
        this.sims.add(sim);
    }

    public void add(SimScore sim, SimScore reverse) {
        this.sims.add(sim);
        this.reverseSims.add(reverse);
    }

    public boolean hasReverse() {
        return reverseSims != null;
    }

    /*
    public Example toDense(List<SimScore> ifMissing) {
        List<SimScore> dense = new ArrayList<SimScore>();
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
        return new Example(new ArrayList<SimScore>());
    }
    public static Example makeEmptyWithReverse() {
        return new Example(new ArrayList<SimScore>(), new ArrayList<SimScore>());
    }
}
