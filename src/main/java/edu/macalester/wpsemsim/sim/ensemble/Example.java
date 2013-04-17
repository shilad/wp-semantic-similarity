package edu.macalester.wpsemsim.sim.ensemble;

import edu.macalester.wpsemsim.sim.SimScore;
import edu.macalester.wpsemsim.utils.KnownSim;

import java.util.ArrayList;
import java.util.List;

/**
 * A collection of predictions from similarity metrics that can be used by an ensemble predictor.
 * Label may be null if it is not known.
 *
 * Sims will capture similarity in one direction and reverse sims will capture similarity in the opposite direction
 * if one of the following is true:
 * 1. The example corresponds to a call to {@link edu.macalester.wpsemsim.sim.SimilarityMetric#similarity}.
 * 2. The example corresponds to a call to {@link edu.macalester.wpsemsim.sim.SimilarityMetric#mostSimilar} and the
 * call is associated with a labeled training example.
 *
 * Otherwise, reverse sims will be null.
 *
 * The component sims arrays are dense. They will always have size equal to the number of components.
 * If reverseSims is not null, it's size will equal sims.
 *
 * KnownSim will be non-null if the call is associated with a labeled training instance.
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
    public String toString() {
        StringBuffer buff = new StringBuffer();
        for (int i = 0; i < sims.size(); i++) {
            if (i != 0) buff.append(" ");
            buff.append("" + sims.get(i).sim);
        }
        return buff.toString();
    }

    public static Example makeEmpty() {
        return new Example(new ArrayList<SimScore>());
    }
    public static Example makeEmptyWithReverse() {
        return new Example(new ArrayList<SimScore>(), new ArrayList<SimScore>());
    }
}
