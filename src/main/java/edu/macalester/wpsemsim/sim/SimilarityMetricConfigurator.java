package edu.macalester.wpsemsim.sim;

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
* For format, see:
* <a href="https://developer.apple.com/library/mac/#documentation/Cocoa/Conceptual/PropertyLists/OldStylePlists/OldStylePLists.html">The Mac documentation</a>
* And look at dat/example.conf
*/
public class SimilarityMetricConfigurator {
    private static final Logger LOG = Logger.getLogger(SimilarityMetricConfigurator.class.getName());

    ConfigurationFile configuration;

    public SimilarityMetricConfigurator(ConfigurationFile conf) {
        this.configuration = conf;
    }

    public List<SimilarityMetric> load() throws IOException, ConfigurationException {
        info("loading metrics");
        List<SimilarityMetric> metrics = new ArrayList<SimilarityMetric>();
        for (String key : configuration.getKeys()) {
            if (configuration.isSimilarityMetric(key)) {
                metrics.add(loadMetric(key, configuration.get(key)));
            }
        }
        return metrics;
    }

    protected SimilarityMetric loadMetric(String name, JSONObject params) throws ConfigurationException, IOException {
        info("loading metric " + name);
        String type = requireString(params, "type");
        SimilarityMetric metric;
        if (type.equals("category")) {
            File luceneDir = requireDirectory(params, "lucene");
            IndexHelper helper = new IndexHelper(luceneDir, true);
            CategoryGraph graph = new CategoryGraph(helper);
            graph.init();
            metric = new CatSimilarity(graph, helper);
        } else if (type.equals("text")) {
            File luceneDir = requireDirectory(params, "lucene");
            IndexHelper helper = new IndexHelper(luceneDir, true);
            String field = requireString(params, "field");
            metric = new TextSimilarity(helper, field);
            if (params.containsKey("maxPercentage")) {
                ((TextSimilarity)metric).setMaxPercentage(requireInteger(params, "maxPercentage"));
            }
            if (params.containsKey("minTermFreq")) {
                ((TextSimilarity)metric).setMinTermFreq(requireInteger(params, "minTermFreq"));
            }
            if (params.containsKey("minDocFreq")) {
                ((TextSimilarity)metric).setMinDocFreq(requireInteger(params, "minDocFreq"));
            }
        } else if (type.equals("pairwise")) {
            SparseMatrix m = new SparseMatrix(requireFile(params, "matrix"), false, PairwiseCosineSimilarity.PAGE_SIZE);
            SparseMatrix mt = new SparseMatrix(requireFile(params, "transpose"));
            metric = new PairwiseCosineSimilarity(m, mt);
        } else {
            throw new ConfigurationException("Unknown metric type: " + type);
        }
        metric.setName(name);
        return metric;
    }


    public void build() throws IOException, ConfigurationException, InterruptedException {
        info("building all metrics");
        List<SimilarityMetric> metrics = new ArrayList<SimilarityMetric>();

        IndexHelper helper = new IndexHelper(requireDirectory(configuration.get(), "index"), true);

        // first do non-pairwise (no pre-processing required)
        for (String key : configuration.getKeys()) {
            if (configuration.isSimilarityMetric(key) && !configuration.get(key).get("type").equals("pairwise")) {
                metrics.add(loadMetric(key, configuration.get(key)));
            }
        }

        // next do pairwise
        for (String key : configuration.getKeys()) {
            if (configuration.isSimilarityMetric(key) && configuration.get(key).get("type").equals("pairwise")) {
                metrics.add(buildPairwise(key, configuration.get(key), metrics, helper.getWpIds()));
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
