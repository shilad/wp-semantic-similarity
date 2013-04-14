package edu.macalester.wpsemsim.utils;

import org.apache.commons.lang3.StringEscapeUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

public class KnownSim {
    public String phrase1;
    public String phrase2;
    public int wpId1 = -1;
    public int wpId2 = -1;
    public double similarity;

    public KnownSim(String phrase1, String phrase2, double similarity) {
        this.phrase1 = phrase1;
        this.phrase2 = phrase2;
        this.similarity = similarity;
    }

    public KnownSim(String phrase1, String phrase2, int wpId1, int wpId2, double similarity) {
        this.wpId1 = wpId1;
        this.wpId2 = wpId2;
        this.phrase1 = phrase1;
        this.phrase2 = phrase2;
        this.similarity = similarity;
    }

    @Override
    public String toString() {
        return "KnownSim{" +
                "phrase1='" + phrase1 + '\'' +
                ", phrase2='" + phrase2 + '\'' +
                ", similarity=" + similarity +
                '}';
    }

    /**
     * Swaps phrase1 and phrase2 50% of the time
     */
    public void maybeSwap() {
        if (Math.random() > 0.5) {
            String t = phrase1;
            phrase1 = phrase2;
            phrase2 = t;
        }
    }

    public static final Logger LOG = Logger.getLogger(KnownSim.class.getName());

    public static List<KnownSim> read(File path) throws IOException {
        List<KnownSim> result = new ArrayList<KnownSim>();
        BufferedReader reader = new BufferedReader(new FileReader(path));
        while (true) {
            String line = reader.readLine();
            if (line == null)
                break;
            String tokens[] = line.split("\t");
            if (tokens.length == 3) {
                result.add(new KnownSim(
                        tokens[0],
                        tokens[1],
                        Double.valueOf(tokens[2])
                ));
            } else {
                LOG.info("invalid line in gold standard file " + path + ": " +
                        "'" + StringEscapeUtils.escapeJava(line) + "'");
            }
        }
        return result;
    }
}
