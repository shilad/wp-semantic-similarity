package edu.macalester.wpsemsim.sim.ensemble;

import edu.macalester.wpsemsim.utils.DocScoreList;

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
        }
    }

    public boolean hasValue() {
        return !Double.isNaN(sim);
    }

    public static String getArffHeader(String prefix) {
        StringBuffer buff = new StringBuffer();
        buff.append("@attribute " +  prefix + "length integer\n");
        buff.append("@attribute " +  prefix + "rank integer\n");
        buff.append("@attribute " +  prefix + "min real\n");
        buff.append("@attribute " +  prefix + "max real\n");
        buff.append("@attribute " +  prefix + "sim real\n");
        return buff.toString();
    }

    public String getArffEntry() {
        StringBuffer buff = new StringBuffer();
        buff.append(length  + ",");
        buff.append("" + (rank == -1 ? "?" : rank) + ",");
        buff.append("" + (Double.isNaN(minSim) ? "?" : minSim) + ",");
        buff.append("" + (Double.isNaN(maxSim) ? "?" : maxSim) + ",");
        buff.append("" + (Double.isNaN(sim) ? "?" : sim) + ",");
        return buff.toString();
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
