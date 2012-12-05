package edu.macalester.wpsemsim.sim.utils;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.concepts.DictionaryMapper;
import edu.macalester.wpsemsim.concepts.EnsembleMapper;
import edu.macalester.wpsemsim.concepts.LuceneMapper;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.matrix.SparseMatrix;
import edu.macalester.wpsemsim.matrix.SparseMatrixTransposer;
import edu.macalester.wpsemsim.sim.esa.ESASimilarity;
import edu.macalester.wpsemsim.sim.LinkSimilarity;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.sim.TextSimilarity;
import edu.macalester.wpsemsim.sim.category.CatSimilarity;
import edu.macalester.wpsemsim.sim.category.CategoryGraph;
import edu.macalester.wpsemsim.sim.ensemble.EnsembleSimilarity;
import edu.macalester.wpsemsim.sim.pairwise.PairwiseCosineSimilarity;
import edu.macalester.wpsemsim.sim.pairwise.PairwisePhraseSimilarity;
import edu.macalester.wpsemsim.sim.pairwise.PairwiseSimilarityWriter;
import edu.macalester.wpsemsim.utils.ConfigurationFile;
import org.json.simple.JSONObject;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

import static edu.macalester.wpsemsim.utils.ConfigurationFile.*;

/*
* TODO: share resources when possible; move to a true dependency injection framework
*
* For format, see JSON spec and dat/example-configuration.conf
*/
public class SimilarityMetricConfigurator {
    private static final Logger LOG = Logger.getLogger(SimilarityMetricConfigurator.class.getName());

    ConfigurationFile configuration;
    ConceptMapper mapper;
    IndexHelper helper;

    public SimilarityMetricConfigurator(ConfigurationFile conf) {
        this.configuration = conf;
    }

    public List<SimilarityMetric> loadAllMetrics() throws IOException, ConfigurationException {
        return loadAllMetrics(false);

    }
    public List<SimilarityMetric> loadAllMetrics(boolean doEnsembles) throws IOException, ConfigurationException {
        info("loading metrics");
        Set<String> ensembleKeys = new HashSet<String>();
        List<SimilarityMetric> metrics = new ArrayList<SimilarityMetric>();
        for (String key : configuration.getKeys("metrics")) {
            String type = requireString(configuration.get("metrics", key), "type");
            if (type.equals("ensemble")) {
                ensembleKeys.add(key);
            } else {
                metrics.add(loadMetric(key));
            }
        }
        if (doEnsembles) {
            for (String key : ensembleKeys) {
                metrics.add(loadEnsembleMetric(key, metrics));
            }
        }
        return metrics;
    }

    private SimilarityMetric loadEnsembleMetric(String key, List<SimilarityMetric> metrics) throws IOException, ConfigurationException {
        info("loading ensemble metric " + key);
        try {
            Map<String, Object> params = (Map<String, Object>) configuration.get("metrics").get(key);
            EnsembleSimilarity similarity = new EnsembleSimilarity(getMapper(), getHelper());
            similarity.setComponents(metrics);
            similarity.read(requireDirectory(params, "model"));
            similarity.setName(key);
            if (params.containsKey("minComponents")) {
                similarity.setMinComponents(requireInteger(params, "minComponents"));
            }
            return similarity;
        } catch (DatabaseException e) {
            throw new ConfigurationException(e.toString());
        }
    }

    public SimilarityMetric loadMetric(String name) throws IOException, ConfigurationException {
        return loadMetric(name, configuration.get("metrics", name));
    }

    protected SimilarityMetric loadMetric(String name, JSONObject params) throws ConfigurationException, IOException {
        info("loading metric " + name);
        String type = requireString(params, "type");
        SimilarityMetric metric;
        ConceptMapper mapper = null;
        try {
            mapper = getMapper();
        } catch (DatabaseException e) {
            throw new ConfigurationException(e.getMessage());
        }
        if (type.equals("category")) {
            metric = createCategorySimilarity(params, mapper);
        } else if (type.equals("text")) {
            metric = createTextSimilarity(params, mapper);
        } else if (type.equals("esa")) {
            metric = createEsaSimilarity(params, mapper);
        } else if (type.equals("links")) {
            metric = createLinkSimilarity(params, mapper);
        } else if (type.equals("pairwise")) {
            metric = createPairwiseSimilarity(params, mapper);
        } else if (type.equals("pairwise-phrase")) {
            metric = createPairwisePhraseSimilarity(params);
        } else {
            throw new ConfigurationException("Unknown metric type: " + type);
        }
        metric.setName(name);
        return metric;
    }

    private SimilarityMetric createPairwisePhraseSimilarity(JSONObject params) throws ConfigurationException, IOException {
        SimilarityMetric metric;File luceneDir = requireDirectory(params, "lucene");
        SparseMatrix m = new SparseMatrix(requireFile(params, "matrix"));
        metric = new PairwisePhraseSimilarity(new IndexHelper(luceneDir, true), m);
        return metric;
    }

    private SimilarityMetric createPairwiseSimilarity(JSONObject params, ConceptMapper mapper) throws IOException, ConfigurationException {
        SimilarityMetric metric;SparseMatrix m = new SparseMatrix(requireFile(params, "matrix"));
        SparseMatrix mt = new SparseMatrix(requireFile(params, "transpose"));
        metric = new PairwiseCosineSimilarity(mapper, getHelper(), m, mt);
        return metric;
    }

    private SimilarityMetric createLinkSimilarity(JSONObject params, ConceptMapper mapper) throws ConfigurationException, IOException {
        SimilarityMetric metric;File luceneDir = requireDirectory(params, "lucene");
        String field = requireString(params, "field");
        LinkSimilarity lmetric = new LinkSimilarity(mapper, new IndexHelper(luceneDir, true), getHelper(), field);
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

    private SimilarityMetric createEsaSimilarity(JSONObject params, ConceptMapper mapper) throws ConfigurationException, IOException {
        SimilarityMetric metric;File luceneDir = requireDirectory(params, "lucene");
        metric = new ESASimilarity(mapper, new IndexHelper(luceneDir, true));
        return metric;
    }

    private SimilarityMetric createTextSimilarity(JSONObject params, ConceptMapper mapper) throws ConfigurationException, IOException {
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

    private SimilarityMetric createCategorySimilarity(JSONObject params, ConceptMapper mapper) throws ConfigurationException, IOException {
        SimilarityMetric metric;File luceneDir = requireDirectory(params, "lucene");
        IndexHelper helper = new IndexHelper(luceneDir, true);
        CategoryGraph graph = new CategoryGraph(helper);
        graph.init();
        metric = new CatSimilarity(mapper, graph, helper);
        return metric;
    }

    public synchronized IndexHelper getHelper() throws ConfigurationException, IOException {
        if (helper == null) {
            helper = new IndexHelper(requireDirectory(configuration.get(), "index"), true);
        }
        return helper;
    }

    public synchronized  ConceptMapper getMapper() throws ConfigurationException, IOException, DatabaseException {
        if (mapper == null && configuration.get().containsKey("concept-mapper")) {
            if (configuration.get("concept-mapper").containsKey("ensemble")) {
                mapper = getEnsembleMapper();
            } else if (configuration.get("concept-mapper").containsKey("dictionary")) {
                mapper = getDictionaryMapper();
            } else if (configuration.get("concept-mapper").containsKey("lucene")) {
                mapper = getLuceneMapper();
            } else {
                throw new IllegalArgumentException("unrecognized concept mapper");
            }
        }
        return mapper;
    }

    private ConceptMapper getEnsembleMapper() throws IOException, ConfigurationException, DatabaseException {
        return new EnsembleMapper(
                getDictionaryMapper(),
                getLuceneMapper()
            );
    }

    private ConceptMapper getLuceneMapper() throws IOException, ConfigurationException {
        IndexHelper helper = new IndexHelper(
                requireDirectory(configuration.get("concept-mapper"), "lucene"), false);
        return new LuceneMapper(helper);
    }

    private ConceptMapper getDictionaryMapper() throws IOException, DatabaseException, ConfigurationException {
        return new DictionaryMapper(
                requireDirectory(configuration.get("concept-mapper"), "dictionary"),
                getHelper());
    }

    public void build() throws IOException, ConfigurationException, InterruptedException {
        info("building all metrics");
        List<SimilarityMetric> metrics = new ArrayList<SimilarityMetric>();

        // first do non-pairwise (no pre-processing required)
        for (String key : configuration.getKeys("metrics")) {
            JSONObject params = configuration.get("metrics", key);
            if (!params.get("type").equals("pairwise")) {
                metrics.add(loadMetric(key, params));
            }
        }

        // next do pairwise
        IndexHelper helper = getHelper();
        int wpIds[] = helper.getWpIds();
        for (String key : configuration.getKeys("metrics")) {
            JSONObject params = configuration.get("metrics", key);
            if (params.get("type").equals("pairwise")) {
                metrics.add(buildPairwise(key, params, metrics, wpIds));
            }
        }
    }

    protected SimilarityMetric buildPairwise(String name, JSONObject params, List<SimilarityMetric> metrics, int[] wpIds) throws ConfigurationException, IOException, InterruptedException {
        info("building metric " + name);
        String basedOnName = requireString(params, "basedOn");
        SimilarityMetric basedOn = null;
        for (SimilarityMetric m : metrics) {
            if (m.getName().equals(basedOnName)) {
                basedOn = m;
                break;
            }
        }
        if (basedOn == null) {
            throw new ConfigurationException("could not find basedOn metric: " + basedOnName);
        }

        File matrixFile = new File(requireString(params, "matrix"));
        File transposeFile = new File(requireString(params, "transpose"));
        PairwiseSimilarityWriter writer =
                new PairwiseSimilarityWriter(basedOn, matrixFile);
        writer.writeSims(wpIds, 1, 20);
        SparseMatrix matrix = new SparseMatrix(matrixFile);
        SparseMatrixTransposer transposer = new SparseMatrixTransposer(matrix, transposeFile, 100);
        transposer.transpose();
        SparseMatrix transpose = new SparseMatrix(transposeFile);

        SimilarityMetric metric = new PairwiseCosineSimilarity(matrix, transpose);
        metric.setName(name);
        return metric;
    }

    private void info(String message) {
        LOG.info("configurator for " + configuration.getPath() + ": " + message);
    }
}
