package edu.macalester.wpsemsim.sim.ensemble;

import edu.macalester.wpsemsim.utils.DocScoreList;

/**
 * A single similarity metric's similarity score.
 * Can represent output from either mostSimilar() or similarity().
 * If output is from similarity(), rank = -1 and length = 0.
 */
public class ComponentSim {
    public int component;

    // rank of phrase2 in sim list
    public int rank = -1;

    // maximum value in sim list
    public double maxSim = Double.NaN;
    public double minSim = Double.NaN;

    // actual value in sim list
    public double sim = Double.NaN;

    // length of list
    public int length = 0;

    // scores TODO: is this safe, or too much memory?
    public float scores[];


    public ComponentSim(int component, double sim) {
        this.component = component;
        this.sim = sim;
    }

    public ComponentSim(int component, DocScoreList list, int rank) {
        this.component = component;
        length = list.numDocs();
        if (length > 0) {
            maxSim = list.getScore(0);
            minSim = list.getScore(list.numDocs()-1);
            this.rank = rank;
            if (rank >= 0) {
                sim = list.getScore(rank);
            }
            scores = list.getScoresAsFloat();
        }
    }

    public boolean hasValue() {
        return !Double.isNaN(sim);
    }

    @Override
    public String toString() {
        return "ComponentSim{" +
                "component=" + component +
                ", rank=" + rank +
                ", maxSim=" + maxSim +
                ", minSim=" + minSim +
                ", sim=" + sim +
                ", length=" + length +
                '}';
    }
}
