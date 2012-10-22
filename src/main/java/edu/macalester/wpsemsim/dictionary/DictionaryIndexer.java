package edu.macalester.wpsemsim.dictionary;

import com.sleepycat.je.DatabaseException;
import org.apache.commons.lang3.math.Fraction;

import java.io.*;
import java.text.DecimalFormat;
import java.util.logging.Logger;

public class DictionaryIndexer {
    private static final Logger LOG = Logger.getLogger(DictionaryIndexer.class.getName());

    private int minNumLinks = 5;
    private double minFractionLinks = 0.2;
    private DictionaryDatabase db;

    public DictionaryIndexer(File path) throws IOException, DatabaseException {
        this.db = new DictionaryDatabase(path, true);
    }

    public void prune(BufferedReader in) throws IOException, DatabaseException {
        long numLines = 0;
        long numLinesRetained = 0;
        DictionaryDatabase.Record record = null;
        while (true) {
            String line = in.readLine();
            if (line == null) {
                break;
            }
            if (++numLines % 1000000 == 0) {
                double p = 100.0 * numLinesRetained / numLines;
                LOG.info("processing line: " + numLines +
                        ", retained " + numLinesRetained +
                        "(" + new DecimalFormat("#.#").format(p) + "%)");
            }
            DictionaryEntry entry = new DictionaryEntry(line);
            if (retain(entry)) {
                numLinesRetained++;
                if (record != null && record.shouldContainEntry(entry)) {
                    record.add(entry);
                } else {
                    if (record != null) {
                        db.put(record, true);
                    }
                    record = new DictionaryDatabase.Record(entry);
                }
            }
        }
        db.put(record, true);
        db.close();
    }

    public boolean retain(DictionaryEntry entry) {
        Fraction f = entry.getFractionEnglishQueries();
        return (
            f != null
            && f.getNumerator() >= minNumLinks
            && 1.0 * f.getNumerator() / f.getDenominator() > minFractionLinks
        );

    }
    public int getMinNumLinks() {
        return minNumLinks;
    }
    public void setMinNumLinks(int minNumLinks) {
        this.minNumLinks = minNumLinks;
    }
    public double getMinFractionLinks() {
        return minFractionLinks;
    }
    public void setMinFractionLinks(double minFractionLinks) {
        this.minFractionLinks = minFractionLinks;
    }

    public static void main(String args[]) throws IOException, DatabaseException {
        if (args.length != 4) {
            System.err.println("usage: java " +
                    DictionaryIndexer.class.getName() +
                    "inputFile outputFile minNumLinks minFractionLinks");
            System.exit(1);
        }
        BufferedReader in = new BufferedReader(new FileReader(args[0]));
        int minNumLinks = Integer.valueOf(args[2]);
        double minFractionLinks = Double.valueOf(args[3]);
        DictionaryIndexer indexer = new DictionaryIndexer(new File(args[1]));
        indexer.setMinNumLinks(minNumLinks);
        indexer.setMinFractionLinks(minFractionLinks);
        indexer.prune(in);
        in.close();
    }
}
