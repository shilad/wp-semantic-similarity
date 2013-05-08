package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.sim.pairwise.PairwiseSimilarityWriter;
import edu.macalester.wpsemsim.utils.EnvConfigurator;
import edu.macalester.wpsemsim.utils.ConfigurationFile;
import edu.macalester.wpsemsim.utils.DefaultOptionBuilder;
import edu.macalester.wpsemsim.utils.Env;
import gnu.trove.list.TIntList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.util.logging.Logger;

public class BuilderMain {

    public static void main(String args[]) throws IOException, ConfigurationFile.ConfigurationException, InterruptedException {
        Options options = new Options();              options.addOption(new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("name")
                .withDescription("Name of similarity metric that should be built.")
                .create('n'));
        options.addOption(new DefaultOptionBuilder()
                .isRequired()
                .hasArg()
                .withLongOpt("output")
                .withDescription("Output file.")
                .create('o'));
        options.addOption(new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("buildIds")
                .withDescription("File listing ids that should be built.")
                .create('i'));

        EnvConfigurator conf;
        try {
            conf = new EnvConfigurator(options, args);
        } catch (org.apache.commons.cli.ParseException e) {
            System.err.println( "Invalid option usage: " + e.getMessage());
            new HelpFormatter().printHelp("SimilarityAnalyzer", options);
            return;
        }

        CommandLine cmd = conf.getCommandLine();
        conf.setShouldLoadMetrics(false);
        Env env = conf.loadEnv();
        int idsToBuild[] = env.getMainIndex().getWpIds();
        if (cmd.hasOption("i")) {
            TIntSet idSet = new TIntHashSet();
            for (String line : FileUtils.readLines(new File(cmd.getOptionValue("i")))) {
                idSet.add(Integer.valueOf(line));
            }
            idsToBuild = idSet.toArray();
        }

        File outputFile = new File(cmd.getOptionValue("o"));
        SimilarityMetric m = conf.loadMetric(cmd.getOptionValue("n"), true);
        PairwiseSimilarityWriter writer = new PairwiseSimilarityWriter(m, outputFile);
        writer.setValidIds(env.getValidIds());
        writer.writeSims(idsToBuild, env.getNumThreads(), env.getNumMostSimilarResults());
    }
}
