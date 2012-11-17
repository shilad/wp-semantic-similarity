package edu.macalester.wpsemsim.sim;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.concepts.DictionaryDatabase;
import edu.macalester.wpsemsim.concepts.LuceneMapper;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.matrix.SparseMatrix;
import edu.macalester.wpsemsim.matrix.SparseMatrixTransposer;
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
        info("loading metrics");
        List<SimilarityMetric> metrics = new ArrayList<SimilarityMetric>();
        for (String key : configuration.getKeys("metrics")) {
            metrics.add(loadMetric(key, configuration.get("metrics", key)));
        }
        return metrics;
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
            File luceneDir = requireDirectory(params, "lucene");
            IndexHelper helper = new IndexHelper(luceneDir, true);
            CategoryGraph graph = new CategoryGraph(helper);
            graph.init();
            metric = new CatSimilarity(mapper, graph, helper);
        } else if (type.equals("text")) {
            File luceneDir = requireDirectory(params, "lucene");
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
        } else if (type.equals("esa")) {
            File luceneDir = requireDirectory(params, "lucene");
            metric = new ESASimilarity(new IndexHelper(luceneDir, true));
        } else if (type.equals("pairwise")) {
            SparseMatrix m = new SparseMatrix(requireFile(params, "matrix"));
            SparseMatrix mt = new SparseMatrix(requireFile(params, "transpose"));
            metric = new PairwiseCosineSimilarity(mapper, getHelper(), m, mt);
        } else if (type.equals("pairwise-phrase")) {
            File luceneDir = requireDirectory(params, "lucene");
            SparseMatrix m = new SparseMatrix(requireFile(params, "matrix"));
            metric = new PairwisePhraseSimilarity(new IndexHelper(luceneDir, true), m);
        } else {
            throw new ConfigurationException("Unknown metric type: " + type);
        }
        metric.setName(name);
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
            if (configuration.get("concept-mapper").containsKey("dictionary")) {
                mapper = new DictionaryDatabase(
                        requireDirectory(configuration.get("concept-mapper"), "dictionary"),
                        getHelper());
            } else if (configuration.get("concept-mapper").containsKey("lucene")) {
                IndexHelper helper = new IndexHelper(
                        requireDirectory(configuration.get("concept-mapper"), "lucene"), false);
                mapper = new LuceneMapper(helper);
            } else {
                throw new IllegalArgumentException("unrecognized concept mapper");
            }
        }
        return mapper;
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
