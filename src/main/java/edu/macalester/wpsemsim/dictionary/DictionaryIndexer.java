package edu.macalester.wpsemsim.dictionary;

import com.sleepycat.je.*;
import org.apache.commons.compress.compressors.FileNameUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.math.Fraction;

import java.io.*;
import java.text.DecimalFormat;
import java.util.logging.Logger;

public class DictionaryIndexer {
    private static final Logger LOG = Logger.getLogger(DictionaryIndexer.class.getName());

    private int minNumLinks = 5;
    private double minFractionLinks = 0.2;
    private Database db;
    private Environment env;


    public DictionaryIndexer(File path) throws DatabaseException, IOException {
        if (path.isDirectory()) {
            FileUtils.deleteDirectory(path);
        }
        path.mkdirs();
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setTransactional(false);
        envConfig.setAllowCreate(true);
        this.env = new Environment(path.getParentFile(), envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        this.db = env.openDatabase(null,
                FilenameUtils.getName(path.toString()),
                dbConfig);
    }

    public void prune(BufferedReader in) throws IOException, DatabaseException {
        long numLines = 0;
        long numLinesRetained = 0;
        while (true) {
            String line = in.readLine();
            if (line == null) {
                break;
            }
            if (++numLines % 1000000 == 0) {
                double p = 100.0 * numLinesRetained / numLines;
                LOG.info("processing line: " + numLines +
                        ", retained " + numLinesRetained
                        + "(" + new DecimalFormat("#.#").format(p) + "%)");
            }
            DictionaryEntry entry = new DictionaryEntry(line);
            if (retain(entry)) {
                numLinesRetained++;
                db.put(null,
                        new DatabaseEntry(entry.getText().getBytes()),
                        new DatabaseEntry(line.getBytes()));
            }
        }
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
    public void close() throws DatabaseException {
        env.close();
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
        indexer.close();
    }
}
