package edu.macalester.wpsemsim.utils;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.concepts.*;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.matrix.SparseMatrix;
import edu.macalester.wpsemsim.normalize.IdentityNormalizer;
import edu.macalester.wpsemsim.normalize.LoessNormalizer;
import edu.macalester.wpsemsim.normalize.Normalizer;
import edu.macalester.wpsemsim.normalize.RankAndScoreNormalizer;
import edu.macalester.wpsemsim.sim.BaseSimilarityMetric;
import edu.macalester.wpsemsim.sim.LinkSimilarity;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.sim.TextSimilarity;
import edu.macalester.wpsemsim.sim.category.CategoryGraph;
import edu.macalester.wpsemsim.sim.category.CategorySimilarity;
import edu.macalester.wpsemsim.sim.ensemble.Ensemble;
import edu.macalester.wpsemsim.sim.ensemble.EnsembleSimilarity;
import edu.macalester.wpsemsim.sim.ensemble.LinearEnsemble;
import edu.macalester.wpsemsim.sim.ensemble.SvmEnsemble;
import edu.macalester.wpsemsim.sim.esa.ESAAnalyzer;
import edu.macalester.wpsemsim.sim.esa.ESASimilarity;
import edu.macalester.wpsemsim.sim.pairwise.PairwiseCosineSimilarity;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONObject;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static edu.macalester.wpsemsim.utils.ConfigurationFile.*;

/*
 * TODO: consider a dependency injection framework
 *
 * For format, see JSON spec and dat/example-configuration.conf
 */
public class EnvConfigurator {
    private static final Logger LOG = Logger.getLogger(EnvConfigurator.class.getName());

    protected ConfigurationFile configuration;
    private boolean doEnsembles = true;
    private boolean doPairwise = true;
    private boolean shouldLoadIndexes = true;
    private boolean shouldLoadMappers = true;
    private boolean shouldLoadMetrics = true;
    private boolean shouldLoadGold = true;
    private boolean shouldReadModels = false;

    protected Env env;
    private CommandLine cmd;

    /**
     * Creates a new configuration based on a particular configuration file.
     * @param conf
     */
    public EnvConfigurator(ConfigurationFile conf) {
        this.configuration = conf;
        this.env = new Env();
    }

    public EnvConfigurator(Options options, String args[]) throws ParseException, IOException, ConfigurationException {
        this.env = new Env();

        Option newOpts[] = new Option[] {
            new DefaultOptionBuilder()
                .isRequired()
                .hasArg()
                .withLongOpt("conf")
                .withDescription("Path to configuration file.")
                .create('c'),
            new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("gold")
                .withDescription("Path to gold standard")
                .create('g'),
            new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("threads")
                .withDescription("Number of threads")
                .create('e'),
            new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("titles")
                .withDescription("Input phrases are article titles (takes path to dictionary database).")
                .create('t'),
            new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("results")
                .withDescription("Maximum number of similar wikipedia pages.")
                .create('r'),
            new DefaultOptionBuilder()
                .withLongOpt("skipcats")
                .withDescription("Do not load the category graph.")
                .create('p'),
            new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("validIds")
                .withDescription("Ids that can be included in results list.")
                .create('v')
        };

        for (Option o : newOpts) {
            if (options.hasOption(o.getOpt())) {
                throw new IllegalArgumentException("option " + o.getOpt() + " appears twice.");
            }
            if (options.hasOption(o.getLongOpt())) {
                throw new IllegalArgumentException("option " + o.getOpt() + " appears twice.");
            }
            options.addOption(o);
        }

        CommandLineParser parser = new PosixParser();
        this.cmd = parser.parse(options, args);
        this.configuration= new ConfigurationFile(
                new File(cmd.getOptionValue("c")));
        LOG.info("creating configuration based on " + configuration.getPath());

        if (cmd.hasOption("e")) {
            env.setNumThreads(Integer.valueOf(cmd.getOptionValue("e")));
            LOG.info("set num threads to " + env.getNumThreads());
        }
        if (cmd.hasOption("r")) {
            env.setNumMostSimilarResults(Integer.valueOf(cmd.getOptionValue("r")));
            LOG.info("set max mostSimilar results " + env.getNumMostSimilarResults());
        }
        if (cmd.hasOption("v")) {
            env.setValidIds(readIds(cmd.getOptionValue("v")));
            LOG.info("set valid ids to " + env.getValidIds().size() +
                     " ids in " + cmd.getOptionValue("v"));
        }
    }

    public CommandLine getCommandLine() {
        return cmd;
    }

    /**
     * Creates a new environment from the configuration file.
     * @return
     * @throws IOException
     * @throws ConfigurationException
     */
    public Env loadEnv() throws IOException, ConfigurationException {
        if (shouldLoadIndexes) {
            loadIndexes();
        }
        if (shouldLoadMappers) {
            loadMappers();
        }
        if (shouldLoadMetrics) {
            loadMetrics();
        }
        if (shouldLoadGold) {
            loadGold();
        }
        return env;
    }

    /**
     * Loads mappers and puts them in the environment.
     * @throws IOException
     * @throws ConfigurationException
     */
    public void loadMappers() throws IOException, ConfigurationException {
        info("loading mappers");
        for (String name : (Set<String>)configuration.getMappers().keySet()) {
            loadMapper(name);
        }
    }

    /**
     * Loads indexes and puts them in the environment.
     * @throws ConfigurationException
     * @throws IOException
     */
    public void loadIndexes() throws ConfigurationException, IOException {
        info("loading indexes");
        Collection<String> namesToSkip = Arrays.asList("inputDir", "outputDir");
        for (String name : (Set<String>)configuration.getIndexes().keySet()) {
            if (namesToSkip.contains(name)) {
                continue;
            }
            loadIndex(name);
        }
    }

    /**
     * Loads metrics and puts them in the environment.
     * @return
     * @throws IOException
     * @throws ConfigurationException
     */
    public List<SimilarityMetric> loadMetrics() throws IOException, ConfigurationException {
        return loadMetrics(shouldReadModels);
    }


    /**
     * Loads metrics and puts them in the environment.
     * @return
     * @throws IOException
     * @throws ConfigurationException
     */
    public List<SimilarityMetric> loadMetrics(boolean readModel) throws IOException, ConfigurationException {
        info("loading metrics");
        info("loading metrics");
        Set<String> ensembleKeys = new HashSet<String>();
        List<SimilarityMetric> metrics = new ArrayList<SimilarityMetric>();
        for (String key : (Set<String>)configuration.getMetrics().keySet()) {
            String type = requireString(configuration.getMetric(key), "type");
            if (type.equals("ensemble")) {
                ensembleKeys.add(key);
            } else if (type.equals("pairwise") && !doPairwise) {
                // do nothing
            } else {
                metrics.add(loadMetric(key, readModel));
            }
        }
        if (doEnsembles) {
            for (String key : ensembleKeys) {
                metrics.add(loadMetric(key, readModel));
            }
        }
        return metrics;
    }

    /**
     * Loads the mapper with the specified name if it is not already loaded.
     *
     * @param name
     * @return The requested mapper.
     * @throws ConfigurationException
     * @throws IOException
     */
    public synchronized  ConceptMapper loadMapper(String name) throws ConfigurationException, IOException {
        if (env.hasMapper(name)) {
            // do nothing
        } else if (cmd != null && cmd.hasOption("t") && name.equals(Env.MAIN_KEY)) {
            LOG.info("overriding main mapper with title mapper");
            try {
                env.setMainMapper(
                        new TitleMapper(
                                new File(cmd.getOptionValue("t")),
                                loadMainIndex())
                );
            } catch (DatabaseException e) {
                LOG.log(Level.SEVERE, "creation of title mapper failed: ", e);
                throw new ConfigurationException("creation of title mapper failed: " + e.getMessage());
            }
        } else {
            info("loading mapper " + name);
            JSONObject params = configuration.getMapper(name);
            String type = requireString(params, "type");
            ConceptMapper mapper;
            if (type.equals("dictionary")) {
                mapper = getDictionaryMapper(name);
            } else if (type.equals("lucene")) {
                mapper = getLuceneMapper(name);
            } else if (type.equals("ensemble")) {
                mapper = getEnsembleMapper(name);
            } else if (type.equals("hierarchical")) {
                mapper = getHierarchicalMapper(name);
            } else {
                throw new ConfigurationException("unknown type for mapper " + name + ": " + type);
            }
            env.addMapper(name, mapper);
        }
        return env.getMapper(name);
    }

    /**
     * Loads an index if it is not already loaded.
     *
     * @param name - The name of the
     * @return
     * @throws IOException
     * @throws ConfigurationException
     */
    public IndexHelper loadIndex(String name) throws IOException, ConfigurationException {
        if (env.hasIndex(name)) {
            return env.getIndex(name);
        }
        info("loading index " + name);
        JSONObject indexConfig = configuration.getIndex(name);
        File parentDir = requireDirectory(configuration.getIndexes(), "outputDir");
        IndexHelper helper = new IndexHelper(new File(parentDir, name), true);
        if (indexConfig.containsKey("similarity")) {
            String sim = requireString(indexConfig, "similarity");
            if (sim.equals("ESA")) {
                helper.getSearcher().setSimilarity(new ESASimilarity.LuceneSimilarity());
            } else {
                throw new ConfigurationException("unknown similarity type: " + sim);
            }
        }
        if (indexConfig.containsKey("analyzer")) {
            String analyzer = requireString(indexConfig, "analyzer");
            if (analyzer.equals("ESA")) {
                helper.setAnalyzer(new ESAAnalyzer());
            } else {
                throw new ConfigurationException("unknown analyzer type: " + analyzer);
            }
        }
        env.addIndex(name, helper);
        return helper;
    }

    /**
     * Loads a similarity metric if it isn't already loaded.
     *
     * @param name
     * @return
     * @throws ConfigurationException
     * @throws IOException
     */
    public SimilarityMetric loadMetric(String name) throws ConfigurationException, IOException {
        return this.loadMetric(name, shouldReadModels);
    }


    /**
     * Loads a similarity metric if it isn't already loaded.
     *
     * @param name
     * @return
     * @throws ConfigurationException
     * @throws IOException
     */
    public SimilarityMetric loadMetric(String name, boolean readModel) throws ConfigurationException, IOException {
        if (env.hasMetric(name)) {
            return env.getMetric(name);
        }
        info("loading metric " + name);
        JSONObject params = configuration.getMetric(name);
        String type = requireString(params, "type");
        SimilarityMetric metric;
        if (type.equals("category")) {
            metric = createCategorySimilarity(name);
        } else if (type.equals("text")) {
            metric = createTextSimilarity(name);
        } else if (type.equals("esa")) {
            metric = createEsaSimilarity(name);
        } else if (type.equals("links")) {
            metric = createLinkSimilarity(name);
        } else if (type.equals("pairwise")) {
            metric = createPairwiseSimilarity(name, readModel);
        } else if (type.equals("ensemble")) {
            metric = loadEnsembleMetric(name, readModel);
        } else {
            throw new ConfigurationException("Unknown metric type: " + type);
        }
        if (metric instanceof BaseSimilarityMetric) {
            ((BaseSimilarityMetric)metric).setNumThreads(env.getNumThreads());
        }
        metric.setName(name);
        if (params.containsKey("mostSimilarMatrix")) {
            File path = requireFile(params, "mostSimilarMatrix");
            LOG.info("setting most similar matrix for " + name + " to " + path);
            SparseMatrix m = new SparseMatrix(path, 3, 1024*1024*1024); // 3 * 1GB
            ((BaseSimilarityMetric)metric).setMostSimilarMatrix(m);
        }
        if (readModel) {
            LOG.info("reading model from " + getModelDirectory(metric));
            metric.read(getModelDirectory(metric));
        } else {
            metric.setNormalizer(parseNormalizer(params));
        }
        env.addMetric(name, metric);
        return metric;
    }

    private SimilarityMetric loadEnsembleMetric(String key, boolean readModel) throws IOException, ConfigurationException {
        info("loading ensemble metric " + key);
        Map<String, Object> params = (Map<String, Object>) configuration.getMetric(key);

        // load underlying metrics
        List<SimilarityMetric> metrics = new ArrayList<SimilarityMetric>();
        if (params.containsKey("basedOn")) {
            for (String k : requireListOfStrings(params, "basedOn")) {
                metrics.add(loadMetric(k, readModel));
            }
        } else {
            // load everything except this ensemble
            for (String k : (Set<String>)configuration.getMetrics().keySet()) {
                if (!k.equals(key)) {
                    metrics.add(loadMetric(k, readModel));
                }
            }
        }

        String type = requireString(params, "ensembleType");
        Ensemble ensemble = null;
        if (type.equals("svm")) {
            ensemble = new SvmEnsemble(metrics);
        } else if (type.equals("linear")) {
            ensemble = new LinearEnsemble(metrics);
        } else {
            throw new ConfigurationException("unknown ensemble type for " + key + ": " + type);
        }
        EnsembleSimilarity similarity = new EnsembleSimilarity(ensemble, loadMainMapper(), env.getMainIndex());
        similarity.setComponents(metrics);
        similarity.read(requireDirectory(params, "model"));
        similarity.setName(key);
        if (params.containsKey("minComponents")) {
            similarity.setMinComponents(requireInteger(params, "minComponents"));
        }
        return similarity;
    }

    private SimilarityMetric createPairwiseSimilarity(String name, boolean loadModels) throws IOException, ConfigurationException {
        JSONObject params = configuration.getMetric(name);
        PairwiseCosineSimilarity metric;
        SparseMatrix m = new SparseMatrix(requireFile(params, "matrix"));
        SparseMatrix mt = new SparseMatrix(requireFile(params, "transpose"));
        metric = new PairwiseCosineSimilarity(loadMainMapper(), loadMainIndex(), m, mt);
        if (params.containsKey("basedOn")) {
            metric.setBasedOn(loadMetric(requireString(params, "basedOn"), loadModels));
        }
        if (params.containsKey("buildPhraseVectors")) {
            metric.setBuildPhraseVectors(requireBoolean(params, "buildPhraseVectors"));
        }
        return metric;
    }

    private SimilarityMetric createLinkSimilarity(String name) throws ConfigurationException, IOException {
        JSONObject params = configuration.getMetric(name);
        SimilarityMetric metric;
        String field = requireString(params, "field");
        LinkSimilarity lmetric = new LinkSimilarity(loadMainMapper(), loadIndex(requireString(params, "lucene")), env.getMainIndex(), field);
        if (params.containsKey("similarity")) {
            String sim = requireString(params, "similarity");
            if (sim.equals("tfidf")) {
                lmetric.setSimilarity(LinkSimilarity.SimFn.TFIDF);
            } else if (sim.equals("google")) {
                lmetric.setSimilarity(LinkSimilarity.SimFn.GOOGLE);
            } else if (sim.equals("logodds")) {
                lmetric.setSimilarity(LinkSimilarity.SimFn.LOGODDS);
            } else if (sim.equals("jacard")) {
                lmetric.setSimilarity(LinkSimilarity.SimFn.JACARD);
            } else if (sim.equals("lucene")) {
                lmetric.setSimilarity(LinkSimilarity.SimFn.LUCENE);
            } else {
                throw new IllegalArgumentException("unknown similarity: " + sim);
            }
        }
        if (params.containsKey("minDocFreq")) {
            lmetric.setMinDocFreq(requireInteger(params, "minDocFreq"));
        }
        metric = lmetric;
        return metric;
    }

    private SimilarityMetric createEsaSimilarity(String name) throws ConfigurationException, IOException {
        JSONObject params = configuration.getMetric(name);
        ESASimilarity metric = new ESASimilarity(
                loadMainMapper(),
                loadIndex(requireString(params, "lucene")));
        if (params.containsKey("textLucene")) {
            metric.setTextHelper(loadIndex(requireString(params, "textLucene")));
        }
        return metric;
    }

    private SimilarityMetric createTextSimilarity(String name) throws ConfigurationException, IOException {
        JSONObject params = configuration.getMetric(name);
        SimilarityMetric metric;
        IndexHelper helper = loadIndex(requireString(params, "lucene"));
        String field = requireString(params, "field");
        metric = new TextSimilarity(loadMainMapper(), helper, field);
        if (params.containsKey("maxPercentage")) {
            ((TextSimilarity)metric).setMaxPercentage(requireInteger(params, "maxPercentage"));
        }
        if (params.containsKey("minTermFreq")) {
            ((TextSimilarity)metric).setMinTermFreq(requireInteger(params, "minTermFreq"));
        }
        if (params.containsKey("minDocFreq")) {
            ((TextSimilarity)metric).setMinDocFreq(requireInteger(params, "minDocFreq"));
        }
        if (params.containsKey("useInternalMapper")) {
            ((TextSimilarity)metric).setUseInternalMapper(requireBoolean(params, "useInternalMapper"));
        }
        return metric;
    }

    private SimilarityMetric createCategorySimilarity(String name) throws ConfigurationException, IOException {
        JSONObject params = configuration.getMetric(name);
        SimilarityMetric metric;
        IndexHelper helper = loadIndex(requireString(params, "lucene"));
        CategoryGraph graph = null;
        if (cmd == null || !cmd.hasOption("p")) {
            graph = new CategoryGraph(helper);
            graph.init();
        }
        metric = new CategorySimilarity(loadMainMapper(), graph, helper);
        return metric;
    }

    private ConceptMapper getEnsembleMapper(String name) throws IOException, ConfigurationException {
        throw new NotImplementedException();
//        ConceptMapper mapper = new EnsembleMapper(
//                getDictionaryMapper(name),
//                getLuceneMapper(name)
//            );
//        env.addMapper("ensemble", mapper);
//        return mapper;
    }

    private ConceptMapper getLuceneMapper(String name) throws IOException, ConfigurationException {
        JSONObject params = configuration.getMapper(name);
        IndexHelper helper = loadIndex(requireString(params, "indexName"));
        return new LuceneMapper(helper);
    }

    private ConceptMapper getDictionaryMapper(String name) throws IOException, ConfigurationException {
        try {
            JSONObject params = configuration.getMapper(name);
            return new DictionaryMapper(
                    requireDirectory(params, "dictionary"),
                    loadIndex(requireString(params, "indexName")));
        } catch (DatabaseException e) {
            throw new IOException(e);
        }
    }

    private ConceptMapper getTitleMapper(String name) throws IOException, ConfigurationException {
        try {
            JSONObject params = configuration.getMapper(name);
            return new DictionaryMapper(
                    requireDirectory(params, "dictionary"),
                    loadIndex(requireString(params, "indexName")));
        } catch (DatabaseException e) {
            throw new IOException(e);
        }
    }

    private ConceptMapper getHierarchicalMapper(String name) throws IOException, ConfigurationException {
        JSONObject params = configuration.getMapper(name);
        List<ConceptMapper> delegates = new ArrayList<ConceptMapper>();
        for (String n : requireListOfStrings(params, "delegates")) {
            delegates.add(loadMapper(n));
        }
        return new HierarchicalMapper(delegates);
    }

    private void info(String message) {
        LOG.info("configurator for " + configuration.getPath() + ": " + message);
    }

    protected ConceptMapper loadMainMapper() throws IOException, ConfigurationException {
        return loadMapper(Env.MAIN_KEY);
    }

    protected IndexHelper loadMainIndex() throws IOException, ConfigurationException {
        return loadIndex(Env.MAIN_KEY);
    }

    public void setDoEnsembles(boolean doEnsembles) {
        this.doEnsembles = doEnsembles;
    }

    public void setDoPairwise(boolean doPairwise) {
        this.doPairwise = doPairwise;
    }

    /**
     * @param shouldLoadIndexes If true, loadEnv loads indexes.
     *                          Default is true.
     */
    public void setShouldLoadIndexes(boolean shouldLoadIndexes) {
        this.shouldLoadIndexes = shouldLoadIndexes;
    }

    /**
     * @param shouldLoadMappers If true, loadEnv loads mappers.
     *                          Default is true.
     */
    public void setShouldLoadMappers(boolean shouldLoadMappers) {
        this.shouldLoadMappers = shouldLoadMappers;
    }

    /**
     * If true, the models for similarity metrics will be read from disk.
     * If false, the model similarity metrics are created from scratch (i.e. untrained).
     * Defaults to false.
     * @param shouldReadModels
     */
    public void setShouldReadModels(boolean shouldReadModels) {
        this.shouldReadModels = shouldReadModels;
    }

    /**
     * @param shouldLoadMetrics If true, loadEnv() loads metrics.
     *                          Default is true.
     */
    public void setShouldLoadMetrics(boolean shouldLoadMetrics) {
        this.shouldLoadMetrics = shouldLoadMetrics;
    }

    private Normalizer parseNormalizer(JSONObject parentParams)throws ConfigurationException{
        JSONObject params = (JSONObject) parentParams.get("normalizer");
        if (params == null) {
            return new IdentityNormalizer();
        }
        String type = StringUtils.capitalize(requireString(params, "type"));
        if (type.equalsIgnoreCase("loess")) {
            LoessNormalizer norm = new LoessNormalizer();
            if (params.containsKey("monotonic")) {
                norm.setMonotonic(requireBoolean(params, "monotonic"));
            }
            if (params.containsKey("log")) {
                norm.setLogTransform(requireBoolean(params, "log"));
            }
            return norm;
        } else if (type.equalsIgnoreCase("rankAndScore")) {
                RankAndScoreNormalizer norm = new RankAndScoreNormalizer();
                if (params.containsKey("log")) {
                    norm.setLogTransform(requireBoolean(params, "log"));
                }
                return norm;
        } else {
            try {
                return (Normalizer) Class.forName("edu.macalester.wpsemsim.normalize."+type+"Normalizer").newInstance();
            }catch (Exception e){
                throw new ConfigurationException("unknown normalizer: " + type);
            }
        }
    }

    private List<KnownSim> loadGold() throws ConfigurationException, IOException {
        JSONObject params = configuration.getGold();
        String path = requireString(params, "path");
        if (cmd != null && cmd.hasOption("g")) {
            path = cmd.getOptionValue("g");
        }
        List<KnownSim> g = KnownSim.read(new File(path));
        LOG.info("read " + g.size() + " entries in gold standard " + path);

        // add in wp ids if possible.
        if (env.getMainMapper() != null && env.getMainMapper() instanceof TitleMapper) {
            int numMapped = 0;
            for (KnownSim ks : g) {
                ks.wpId1 = getFirstMapping(ks.phrase1);
                ks.wpId2 = getFirstMapping(ks.phrase2);
                if (ks.wpId1 >= 0) numMapped++;
                if (ks.wpId2 >= 0) numMapped++;
            }
            LOG.info("added in mapping for " + numMapped + " wikipedia ids.");
        }
        env.setGold(g);
        return g;
    }

    private int getFirstMapping(String phrase) throws IOException {
        Disambiguator dab = new Disambiguator(env.getMainMapper(), null, env.getMainIndex(), 1);
        Disambiguator.Match m = dab.disambiguateMostSimilar(phrase, null, 1, null);
        return (m == null) ? -1 : m.phraseWpId;
    }

    public File getModelDirectory(SimilarityMetric m) throws ConfigurationException {
        return getModelDirectory(m.getName());
    }

    public File getModelDirectory(String metricName) throws ConfigurationException {
        return new File(requireString(configuration.getModels(), "path"), metricName);
    }

    /**
     * Reads a list of integer ids listed in a file, one id per line.
     * @param path
     * @return Set of ids
     * @throws IOException
     */
    public static TIntSet readIds(String path) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(path));
        TIntSet ids = new TIntHashSet();
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            ids.add(Integer.valueOf(line.trim()));
        }
        return ids;
    }
}
