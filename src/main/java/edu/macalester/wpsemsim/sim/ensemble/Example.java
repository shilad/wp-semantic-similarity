package edu.macalester.wpsemsim.sim.ensemble;

import edu.macalester.wpsemsim.utils.KnownSim;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

public class Example {
    KnownSim label;
    List<Pair<ComponentSim, ComponentSim>> simPairs;
    List<ComponentSim> sims;

    public Example(KnownSim label, List<Pair<ComponentSim, ComponentSim>> simPairs) {
        this.label = label;
        this.simPairs = simPairs;
    }

    /**
     * Hack: order of constructor args are differ from above
     * to eliminate  type-erasure ambiguity.
     * @param sims
     * @param label
     */
    public Example(List<ComponentSim> sims, KnownSim label) {
        this.sims = sims;
        this.label = label;
    }

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
    }

    public int getNumRanked() {
        int n = 0;
        for (Pair<ComponentSim, ComponentSim> pair : simPairs) {
            if (pair.getLeft().hasValue() || pair.getRight().hasValue()) {
                n += 1;
            }
        }
        return n;
    }
}
