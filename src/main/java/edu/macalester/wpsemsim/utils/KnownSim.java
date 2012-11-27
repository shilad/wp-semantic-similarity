package edu.macalester.wpsemsim.utils;

import org.apache.commons.lang3.StringEscapeUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class KnownSim {
    public String phrase1;
    public String phrase2;
    public double similarity;

    public KnownSim(String phrase1, String phrase2, double similarity) {
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
