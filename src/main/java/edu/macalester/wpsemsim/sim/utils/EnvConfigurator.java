package edu.macalester.wpsemsim.sim.utils;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.concepts.DictionaryMapper;
import edu.macalester.wpsemsim.concepts.LuceneMapper;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.matrix.SparseMatrix;
import edu.macalester.wpsemsim.sim.esa.ESAAnalyzer;
import edu.macalester.wpsemsim.sim.esa.ESASimilarity;
import edu.macalester.wpsemsim.sim.LinkSimilarity;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.sim.TextSimilarity;
import edu.macalester.wpsemsim.sim.category.CategorySimilarity;
import edu.macalester.wpsemsim.sim.category.CategoryGraph;
import edu.macalester.wpsemsim.sim.ensemble.EnsembleSimilarity;
import edu.macalester.wpsemsim.sim.pairwise.PairwiseCosineSimilarity;
import edu.macalester.wpsemsim.utils.ConfigurationFile;
import edu.macalester.wpsemsim.utils.Env;
import org.json.simple.JSONObject;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.*;
import java.util.*;
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
    protected Env env;


    /**
     * Creates a new configuration based on a particular configuration file.
     * @param conf
     */
    public EnvConfigurator(ConfigurationFile conf) {
        this.configuration = conf;
        this.env = new Env(configuration);
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
                metrics.add(loadMetric(key));
            }
        }
        if (doEnsembles) {
            for (String key : ensembleKeys) {
                metrics.add(loadMetric(key));
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
            return env.getMapper(name);
        }
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
        } else {
            throw new ConfigurationException("unknown type for mapper " + name + ": " + type);
        }
        env.addMapper(name, mapper);
        return mapper;
    }

    /**
     * Loads an index if it is not already loaded.
     *
     * @param name - The name of the
     * @return
     * @throws IOException
     * @throws ConfigurationException
     */
    private IndexHelper loadIndex(String name) throws IOException, ConfigurationException {
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
        if (env.hasMetric(name)) {
            return env.getMetric(name);
        }
        info("loading metric " + name);
        String type = requireString(configuration.getMetric(name), "type");
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
            metric = createPairwiseSimilarity(name);
        } else if (type.equals("ensemble")) {
            metric = loadEnsembleMetric(name);
        } else {
            throw new ConfigurationException("Unknown metric type: " + type);
        }
        metric.setName(name);
        env.addMetric(name, metric);
        return metric;
    }

    private SimilarityMetric loadEnsembleMetric(String key) throws IOException, ConfigurationException {
        info("loading ensemble metric " + key);
        Map<String, Object> params = (Map<String, Object>) configuration.getMetric(key);
        EnsembleSimilarity similarity = new EnsembleSimilarity(loadMainMapper(), env.getMainIndex());
        similarity.setComponents(new ArrayList<SimilarityMetric>(env.getMetrics().values()));
        similarity.read(requireDirectory(params, "model"));
        similarity.setName(key);
        if (params.containsKey("minComponents")) {
            similarity.setMinComponents(requireInteger(params, "minComponents"));
        }
        return similarity;
    }

    private SimilarityMetric createPairwiseSimilarity(String name) throws IOException, ConfigurationException {
        JSONObject params = configuration.getMetric(name);
        PairwiseCosineSimilarity metric;
        SparseMatrix m = new SparseMatrix(requireFile(params, "matrix"));
        SparseMatrix mt = new SparseMatrix(requireFile(params, "transpose"));
        metric = new PairwiseCosineSimilarity(loadMainMapper(), loadMainIndex(), m, mt);
        if (params.containsKey("basedOn")) {
            metric.setBasedOn(loadMetric(requireString(params, "basedOn")));
        }
        if (params.containsKey("buildPhraseVectors")) {
            metric.setBuildPhraseVectors(requireBoolean(params, "buildPhraseVectors"));
        }
        return metric;
    }

    private SimilarityMetric createLinkSimilarity(String name) throws ConfigurationException, IOException {
        JSONObject params = configuration.getMetric(name);
        SimilarityMetric metric;File luceneDir = requireDirectory(params, "lucene");
        String field = requireString(params, "field");
        LinkSimilarity lmetric = new LinkSimilarity(loadMainMapper(), new IndexHelper(luceneDir, true), env.getMainIndex(), field);
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
        File luceneDir = requireDirectory(params, "lucene");
        ESASimilarity metric = new ESASimilarity(loadMainMapper(), new IndexHelper(luceneDir, true));
        if (params.containsKey("textLucene")) {
            File textLuceneDir = requireDirectory(params, "textLucene");
            metric.setTextHelper(new IndexHelper(textLuceneDir, true));
        }
        return metric;
    }

    private SimilarityMetric createTextSimilarity(String name) throws ConfigurationException, IOException {
        JSONObject params = configuration.getMetric(name);
        SimilarityMetric metric;File luceneDir = requireDirectory(params, "lucene");
        IndexHelper helper = new IndexHelper(luceneDir, true);
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
        SimilarityMetric metric;File luceneDir = requireDirectory(params, "lucene");
        IndexHelper helper = new IndexHelper(luceneDir, true);
        CategoryGraph graph = new CategoryGraph(helper);
        graph.init();
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

    public void setShouldLoadIndexes(boolean shouldLoadIndexes) {
        this.shouldLoadIndexes = shouldLoadIndexes;
    }

    public void setShouldLoadMappers(boolean shouldLoadMappers) {
        this.shouldLoadMappers = shouldLoadMappers;
    }

    public void setShouldLoadMetrics(boolean shouldLoadMetrics) {
        this.shouldLoadMetrics = shouldLoadMetrics;
    }
}
