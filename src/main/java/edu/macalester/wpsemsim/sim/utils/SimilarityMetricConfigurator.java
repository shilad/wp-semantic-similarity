package edu.macalester.wpsemsim.sim.utils;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.concepts.DictionaryMapper;
import edu.macalester.wpsemsim.concepts.EnsembleMapper;
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

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

import static edu.macalester.wpsemsim.utils.ConfigurationFile.*;

/*
 * TODO: consider a dependency injection framework
 *
 * For format, see JSON spec and dat/example-configuration.conf
 */
public class SimilarityMetricConfigurator {
    private static final Logger LOG = Logger.getLogger(SimilarityMetricConfigurator.class.getName());

    protected ConfigurationFile configuration;
    private boolean doEnsembles = true;
    private boolean doPairwise = true;
    private boolean shouldLoadIndexes = true;
    private boolean shouldLoadMappers = true;
    private boolean shouldLoadMetrics = true;


    public SimilarityMetricConfigurator(ConfigurationFile conf) {
        this.configuration = conf;
    }

    public Env loadEnv() throws IOException, ConfigurationException {
        Env env = new Env(configuration);
        if (shouldLoadIndexes) {
            loadIndexes(env);
        }
        if (shouldLoadMappers) {
            loadMappers(env);
        }
        if (shouldLoadMetrics) {
            loadMetrics(env);
        }
        return env;
    }

    public void loadMappers(Env env) throws IOException, ConfigurationException {
        // Hack! TODO fixup mapper configuration format.
        getMapper(env);
    }

    public void loadIndexes(Env env) throws ConfigurationException, IOException {
        info("loading indexes");
        JSONObject indexConfig = configuration.get("indexes");
        File dir = requireDirectory(indexConfig, "outputDir");
        Collection<String> namesToSkip = Arrays.asList("inputDir", "outputDir");
        for (String name : (Set<String>)indexConfig.keySet()) {
            if (namesToSkip.contains(name)) {
                continue;
            }
            loadIndex(env, indexConfig, dir, name);
        }
    }

    public List<SimilarityMetric> loadMetrics(Env env) throws IOException, ConfigurationException {
        info("loading metrics");
        Set<String> ensembleKeys = new HashSet<String>();
        List<SimilarityMetric> metrics = new ArrayList<SimilarityMetric>();
        for (String key : configuration.getKeys("metrics")) {
            String type = requireString(configuration.get("metrics", key), "type");
            if (type.equals("ensemble")) {
                ensembleKeys.add(key);
            } else if (type.equals("pairwise") && !doPairwise) {
                // do nothing
            } else {
                metrics.add(loadMetric(env, key));
            }
        }
        if (doEnsembles) {
            for (String key : ensembleKeys) {
                metrics.add(loadEnsembleMetric(env, key, metrics));
            }
        }
        return metrics;
    }

    private void loadIndex(Env env, JSONObject indexConfig, File dir, String name) throws IOException, ConfigurationException {
        JSONObject p = (JSONObject)indexConfig.get(name);
        IndexHelper helper = new IndexHelper(new File(dir, name), true);

        if (p.containsKey("similarity")) {
            String sim = requireString(p, "similarity");
            if (sim.equals("ESA")) {
                helper.getSearcher().setSimilarity(new ESASimilarity.LuceneSimilarity());
            } else {
                throw new ConfigurationException("unknown similarity type: " + sim);
            }
        }
        if (p.containsKey("analyzer")) {
            String analyzer = requireString(p, "analyzer");
            if (analyzer.equals("ESA")) {
                helper.setAnalyzer(new ESAAnalyzer());
            } else {
                throw new ConfigurationException("unknown analyzer type: " + analyzer);
            }
        }
        env.addIndex(name, helper);
    }

    private SimilarityMetric loadEnsembleMetric(Env env, String key, List<SimilarityMetric> metrics) throws IOException, ConfigurationException {
        info("loading ensemble metric " + key);
        Map<String, Object> params = (Map<String, Object>) configuration.get("metrics").get(key);
        EnsembleSimilarity similarity = new EnsembleSimilarity(getMapper(env), env.getMainIndex());
        similarity.setComponents(metrics);
        similarity.read(requireDirectory(params, "model"));
        similarity.setName(key);
        if (params.containsKey("minComponents")) {
            similarity.setMinComponents(requireInteger(params, "minComponents"));
        }
        env.addMetric(key, similarity);
        return similarity;
    }

    public SimilarityMetric loadMetric(Env env, String name) throws IOException, ConfigurationException {
        return loadMetric(env, name, configuration.get("metrics", name));
    }

    protected SimilarityMetric loadMetric(Env env, String name, JSONObject params) throws ConfigurationException, IOException {
        info("loading metric " + name);
        String type = requireString(params, "type");
        SimilarityMetric metric;
        ConceptMapper mapper = getMapper(env);
        if (type.equals("category")) {
            metric = createCategorySimilarity(env, params, mapper);
        } else if (type.equals("text")) {
            metric = createTextSimilarity(env, params, mapper);
        } else if (type.equals("esa")) {
            metric = createEsaSimilarity(env, params, mapper);
        } else if (type.equals("links")) {
            metric = createLinkSimilarity(env, params, mapper);
        } else if (type.equals("pairwise")) {
            metric = createPairwiseSimilarity(env, params, mapper);
        } else {
            throw new ConfigurationException("Unknown metric type: " + type);
        }
        metric.setName(name);
        env.addMetric(name, metric);
        return metric;
    }

    private SimilarityMetric createPairwiseSimilarity(Env env, JSONObject params, ConceptMapper mapper) throws IOException, ConfigurationException {
        SimilarityMetric metric;SparseMatrix m = new SparseMatrix(requireFile(params, "matrix"));
        SparseMatrix mt = new SparseMatrix(requireFile(params, "transpose"));
        metric = new PairwiseCosineSimilarity(mapper, env.getMainIndex(), m, mt);
        return metric;
    }

    private SimilarityMetric createLinkSimilarity(Env env, JSONObject params, ConceptMapper mapper) throws ConfigurationException, IOException {
        SimilarityMetric metric;File luceneDir = requireDirectory(params, "lucene");
        String field = requireString(params, "field");
        LinkSimilarity lmetric = new LinkSimilarity(mapper, new IndexHelper(luceneDir, true), env.getMainIndex(), field);
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

    private SimilarityMetric createEsaSimilarity(Env env, JSONObject params, ConceptMapper mapper) throws ConfigurationException, IOException {
        File luceneDir = requireDirectory(params, "lucene");
        ESASimilarity metric = new ESASimilarity(mapper, new IndexHelper(luceneDir, true));
        if (params.containsKey("textLucene")) {
            File textLuceneDir = requireDirectory(params, "textLucene");
            metric.setTextHelper(new IndexHelper(textLuceneDir, true));
        }
        return metric;
    }

    private SimilarityMetric createTextSimilarity(Env env, JSONObject params, ConceptMapper mapper) throws ConfigurationException, IOException {
        SimilarityMetric metric;File luceneDir = requireDirectory(params, "lucene");
        IndexHelper helper = new IndexHelper(luceneDir, true);
        String field = requireString(params, "field");
        metric = new TextSimilarity(mapper, helper, field);
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

    private SimilarityMetric createCategorySimilarity(Env env, JSONObject params, ConceptMapper mapper) throws ConfigurationException, IOException {
        SimilarityMetric metric;File luceneDir = requireDirectory(params, "lucene");
        IndexHelper helper = new IndexHelper(luceneDir, true);
        CategoryGraph graph = new CategoryGraph(helper);
        graph.init();
        metric = new CategorySimilarity(mapper, graph, helper);
        return metric;
    }

    public synchronized  ConceptMapper getMapper(Env env) throws ConfigurationException, IOException {
        ConceptMapper mapper = env.getMapper(Env.MAIN_KEY);
        if (mapper == null && configuration.get().containsKey("concept-mapper")) {
            if (configuration.get("concept-mapper").containsKey("ensemble")) {
                mapper = getEnsembleMapper(env);
            } else if (configuration.get("concept-mapper").containsKey("dictionary")) {
                mapper = getDictionaryMapper(env);
            } else if (configuration.get("concept-mapper").containsKey("lucene")) {
                mapper = getLuceneMapper(env);
            } else {
                throw new IllegalArgumentException("unrecognized concept mapper");
            }
            env.addMapper(Env.MAIN_KEY, mapper);
        }
        return mapper;
    }

    private ConceptMapper getEnsembleMapper(Env env) throws IOException, ConfigurationException {
        ConceptMapper mapper = new EnsembleMapper(
                getDictionaryMapper(env),
                getLuceneMapper(env)
            );
        env.addMapper("ensemble", mapper);
        return mapper;
    }

    private ConceptMapper getLuceneMapper(Env env) throws IOException, ConfigurationException {
        IndexHelper helper = new IndexHelper(
                requireDirectory(configuration.get("concept-mapper"), "lucene"), false);
        ConceptMapper mapper = new LuceneMapper(helper);
        env.addMapper("lucene", mapper);
        return mapper;
    }

    private ConceptMapper getDictionaryMapper(Env env) throws IOException, ConfigurationException {
        try {
            ConceptMapper mapper = new DictionaryMapper(
                    requireDirectory(configuration.get("concept-mapper"), "dictionary"),
                    env.getMainIndex());
            env.addMapper("dictionary", mapper);
            return mapper;
        } catch (DatabaseException e) {
            throw new IOException(e);
        }
    }

    private void info(String message) {
        LOG.info("configurator for " + configuration.getPath() + ": " + message);
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
