package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.utils.DocScoreList;

/**
 * A single similarity metric's similarity score.
 *
 * Can represent output from either mostSimilar() or similarity().
 *
 * If output is from similarity(), rank = -1 and length = 0.
 */
public class SimScore {
    public int component;

    // rank of phrase2 in sim list
    public int rank = -1;

    // maximum value in sim list
    public double maxSim = Double.NaN;
    public double minSim = Double.NaN;

    // actual similarity value
    public double sim = Double.NaN;

    // inferred similarity value for an element not in a mostSimilar list.
    public double missingSim = Double.NaN;

    // length of list
    public int length = 0;

    // TODO: is this safe, or too much memory?
    public float listSims[];


    public SimScore(int component, double sim) {
        this.component = component;
        this.sim = sim;
    }

    public SimScore(int component, DocScoreList list, int rank) {
        this.component = component;
        length = list.numDocs();
        if (length > 0) {
            maxSim = list.getScore(0);
            minSim = list.getScore(list.numDocs()-1);
            this.rank = rank;
            if (rank >= 0) {
                sim = list.getScore(rank);
            }
            listSims = list.getScoresAsFloat();
            missingSim = list.getMissingScore();
        }
    }

    public boolean hasValue() {
        return !Double.isNaN(sim);
    }

    @Override
    public String toString() {
        return "SimScore{" +
                "component=" + component +
                ", rank=" + rank +
                ", maxSim=" + maxSim +
                ", minSim=" + minSim +
                ", sim=" + sim +
                ", length=" + length +
                '}';
    }
}
