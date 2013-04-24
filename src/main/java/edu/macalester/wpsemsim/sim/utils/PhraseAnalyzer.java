package edu.macalester.wpsemsim.sim.utils;

import edu.macalester.wpsemsim.concepts.Disambiguator;
import edu.macalester.wpsemsim.matrix.*;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.utils.*;
import gnu.trove.list.TFloatList;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntDoubleMap;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Given a list of phrases, constructs:
 * 1. A mapping from phrases to WP ids.
 * 2. A list of mostSimilar phrases for each phrase.
 * 3. A dense pairwise similarity matrix for the phrases.
 */
public class PhraseAnalyzer {
    private static final Logger LOG = Logger.getLogger(PhraseAnalyzer.class.getName());

    private Env env;
    private final List<PhraseInfo> phrases = new ArrayList<PhraseInfo>();
    private final TIntHashSet phraseWpIds = new TIntHashSet();
    private File pathPhrases;

    class PhraseInfo {
        String phrase;
        int wpId;
        DocScoreList mostSimilar;
        float pairwiseSims[];
    }

    public PhraseAnalyzer(Env env, File pathPhrases){
        this.env = env;
        this.pathPhrases = pathPhrases;
    }

    public void build(SimilarityMetric metric) throws IOException {
        mapPhrases(pathPhrases);
        buildMostSimilar(metric);
        buildSimilarity(metric);
    }
    /**
     * Map all the phrases in a file to Wikipedia pages.
     * One phrase appears per line.
     * Phrases without a mapping are not added to the phrases attribute.
     * @param file
     * @throws IOException
     */
    private void mapPhrases(File file) throws IOException {
        LOG.info("mapping phrases");
        phraseWpIds.clear();
        phrases.clear();
        final Disambiguator dab = new Disambiguator(env.getMainMapper(), null, env.getMainIndex(), 5);
        ParallelForEach.loop(FileUtils.readLines(file), env.getNumThreads(),
                new Function<String>() {
                    public void call(String line) throws Exception {
                        PhraseInfo pi = new PhraseInfo();
                        pi.phrase = line.trim();
                        Disambiguator.Match m = dab.disambiguate(normalize(pi.phrase));
                        if (m != null && m.hasPhraseMatch()) {
                            pi.wpId = m.phraseWpId;
                            synchronized (phrases) {
                                phrases.add(pi);
                                phraseWpIds.add(pi.wpId);
                            }
                        }
                    }
        });
        LOG.info("finished mapping phrases");
    }

    private void buildMostSimilar(final SimilarityMetric metric) throws IOException {
        LOG.info("building most similar");
        ParallelForEach.loop(phrases, env.getNumThreads(), new Function<PhraseInfo>() {
            public void call(PhraseInfo pi) throws Exception {
                pi.mostSimilar = metric.mostSimilar(pi.wpId, env.getNumMostSimilarResults(), env.getValidIds());
                if (pi.mostSimilar == null) pi.mostSimilar = new DocScoreList(0);
                pi.pairwiseSims = new float[phrases.size()];
            }
        });
        LOG.info("finished building most similar");
    }

    private void buildSimilarity(final SimilarityMetric metric) throws IOException {
        LOG.info("building similarity");
        ParallelForEach.range(0, phrases.size(), env.getNumThreads(), new Function<Integer>() {
            @Override
            public void call(Integer i) throws Exception {
                PhraseInfo pi = phrases.get(i);
                for (int j = 0; j < phrases.size(); j++) {
                    pi.pairwiseSims[j] = (float) cosine(pi.mostSimilar, phrases.get(j).mostSimilar);
                }
            }
        });
        LOG.info("finished building similarity");
    }

    public void write(File outputDir) throws IOException {
        writePhraseMappings(new File(outputDir, "phrases.tsv"));
        writeMostSimilar(new File(outputDir, "mostSimilar.matrix"));
        writeSimilarity(new File(outputDir, "similarity.matrix"));
        writePhraseSimilarities(new File(outputDir, "phrase_sims.tsv"));
    }

    private void writePhraseSimilarities(File file) throws IOException {
        LOG.info("writing pairwise phrase sims to " + file);
        DecimalFormat df = new DecimalFormat("#.#####");
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        for (PhraseInfo pi : phrases) {
            for (int i = 0; i < phrases.size(); i++) {
                writer.write(
                        df.format(pi.pairwiseSims[i]) +
                        "\t" + pi.phrase +
                        "\t" + phrases.get(i).phrase + "\n"
                );
            }
        }
        writer.close();
        LOG.info("finished writing pairwise phrase sims to " + file);
    }

    private void writeMostSimilar(File path) throws IOException {
        LOG.info("writing mostSimilar matrix to " + path);
        LOG.info("calculating range of mostSimilar scores");
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (PhraseInfo pi : phrases) {
            for (DocScore ds : pi.mostSimilar) {
                min = Math.min(ds.getScore(), min);
                max = Math.max(ds.getScore(), max);
            }
        }
        LOG.info("setting mostSimilar score range to [" + min+ ", " + max + "]");

        LOG.info("writing mostSimilar matrix");
        ValueConf vconf = new ValueConf((float)min, (float)max);
        SparseMatrixWriter mostSimilarWriter = new SparseMatrixWriter(path, vconf);
        TIntSet wpIds = getWpIds();
        for (int i = 0; i < phrases.size(); i++) {
            PhraseInfo pi = phrases.get(i);
            TIntList ids = new TIntArrayList();
            TFloatList vals = new TFloatArrayList();
            for (DocScore ds : pi.mostSimilar) {
                if (wpIds.contains(ds.getId())) {
                    ids.add(ds.getId());
                    vals.add((float) ds.getScore());
                }
            }
            mostSimilarWriter.writeRow(new SparseMatrixRow(vconf, i, ids.toArray(), vals.toArray()));
        }
        mostSimilarWriter.finish();
        LOG.info("finished writing mostSimilar matrix to " + path);
    }

    private void writeSimilarity(File path) throws IOException {
        LOG.info("writing similarity matrix to " + path);
        LOG.info("calculating range of similarity scores");
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (PhraseInfo pi : phrases) {
            for (float score : pi.pairwiseSims) {
                min = Math.min(score, min);
                max = Math.max(score, max);
            }
        }
        LOG.info("setting similarity score range to [" + min+ ", " + max + "]");

        LOG.info("writing similarity matrix");
        int colIds[] = new int[phrases.size()];
        for (int i = 0; i < colIds.length; i++) { colIds[i] = i; }
        ValueConf vconf = new ValueConf((float)min, (float)max);
        DenseMatrixWriter writer = new DenseMatrixWriter(path, vconf);
        for (int i = 0; i < phrases.size(); i++) {
            writer.writeRow(new DenseMatrixRow(vconf, i, colIds, phrases.get(i).pairwiseSims));
        }
        writer.finish();
        LOG.info("finished writing similarity matrix to " + path);
    }

    private void writePhraseMappings(File path) throws IOException {
        LOG.info("writing phrase mappings to " + path);
        BufferedWriter mapping = new BufferedWriter(new FileWriter(path));
        for (int i = 0; i < phrases.size(); i++) {
            PhraseInfo pi = phrases.get(i);
            mapping.write(
                    i + "\t" +
                    pi.wpId + "\t" +
                    pi.phrase.trim() + "\t" +
                    env.getMainIndex().wpIdToTitle(pi.wpId) + "\n"
            );
        }
        mapping.close();
        LOG.info("finished writing phrase mappings to " + path);
    }

    private double cosine(DocScoreList dsl1, DocScoreList dsl2) {
        if (dsl1.numDocs() == 0 || dsl2.numDocs() == 0) {
            return 0.0;
        }
        double len1 = 0.0;
        double len2 = 0.0;
        double dot = 0.0;
        TIntDoubleMap scores1 = new TIntDoubleHashMap();
        for (DocScore ds : dsl1) {
            len1 += ds.getScore() * ds.getScore();
            scores1.put(ds.getId(), ds.getScore());
        }
        for (DocScore ds : dsl2) {
            len2 += ds.getScore() * ds.getScore();
            if (scores1.containsKey(ds.getId())) {
                dot += ds.getScore() * scores1.get(ds.getId());
            }
        }
        return dot / Math.sqrt(len1 * len2);
    }

    private TIntSet getWpIds() {
        TIntSet wpIds = new TIntHashSet();
        for (PhraseInfo pi : phrases) { wpIds.add(pi.wpId); }
        return wpIds;
    }


    private static Pattern REPLACE_WEIRD = Pattern.compile("[^\\p{L}\\p{N}]+");
    public static String normalize(String s) {
        return REPLACE_WEIRD.matcher(s).replaceAll(" ").toLowerCase().trim();
    }


    public static void main(String args[]) throws IOException, ConfigurationFile.ConfigurationException, InterruptedException {
        Options options = new Options();              options.addOption(new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("name")
                .withDescription("Name of similarity metric that should be used.")
                .create('n'));
        options.addOption(new DefaultOptionBuilder()
                .isRequired()
                .hasArg()
                .withLongOpt("output")
                .withDescription("Output directory.")
                .create('o'));
        options.addOption(new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("phrases")
                .withDescription("File listing phrases in the universe.")
                .create('p'));

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

        File pathPhrases = new File(cmd.getOptionValue("p"));
        File outputDir = new File(cmd.getOptionValue("o"));
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        SimilarityMetric m = conf.loadMetric(cmd.getOptionValue("n"), true);

        PhraseAnalyzer pa = new PhraseAnalyzer(env, pathPhrases);
        pa.build(m);
        pa.write(outputDir);
    }
}
