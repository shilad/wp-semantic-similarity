package edu.macalester.wpsemsim.sim.utils;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.sim.ensemble.EnsembleSimilarity;
import edu.macalester.wpsemsim.utils.ConfigurationFile;
import edu.macalester.wpsemsim.utils.DocScoreList;
import edu.macalester.wpsemsim.utils.Env;
import org.apache.lucene.queryparser.surround.parser.ParseException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class SimilarityConsole {

    public static void main(String args[]) throws IOException, ConfigurationFile.ConfigurationException, DatabaseException, ParseException {
        if (args.length != 2) {
            System.err.println(
                    "usage: java " + EnsembleSimilarity.class.toString() +
                            " path/to/sim/metric/conf.txt metric-name");
            System.exit(1);
        }
        EnvConfigurator conf = new EnvConfigurator(
                new ConfigurationFile(new File(args[0])));
        Env env = conf.loadEnv();
        SimilarityMetric metric = env.getMetric(args[1]);
        if (metric == null) {
            System.err.println("couldn't find metric named " + args[1]);
            System.exit(1);
        }

        System.out.println("Please enter:\n" +
                        "\tone phrase for mostSimilar()\n" +
                        "\tor two phrases separated by commas for similarity()" +
                        "\tor exit to stop");

        IndexHelper helper = env.getMainIndex();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            String line = in.readLine();
            if (line == null) {
                break;
            }
            line = line.trim();
            if (line.equalsIgnoreCase("exit")) {
                break;
            }
            String phrases[] = line.split(",");
            if (phrases.length == 1) {
                DocScoreList results = metric.mostSimilar(phrases[0].trim(), 10000);
                if (results.numDocs() > 500) {
                    results.truncate(500);
                }
                System.out.println("top results for " + phrases[0]);
                for (int i = 0; i < results.numDocs(); i++) {
                    String title = helper.wpIdToTitle(results.getId(i));
                    System.out.println("" + (i+1) + ". " + results.getScore(i) + ": " + title);
                }
            } else if (phrases.length == 2) {
                double sim = metric.similarity(phrases[0].trim(), phrases[1].trim());
                System.out.println("similarity between " + phrases[0] + " and " + phrases[1] + " is " + sim);
            } else {
                System.err.println("expected at most one comma, found " + (phrases.length - 1));
            }
        }
    }
}
