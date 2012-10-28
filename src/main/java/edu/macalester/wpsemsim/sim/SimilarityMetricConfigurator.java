package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.matrix.SparseMatrix;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.plist.PropertyListConfiguration;
import org.apache.commons.configuration.tree.ConfigurationNode;
import org.junit.experimental.categories.Category;

import java.io.*;
import java.util.*;

/*
* TODO: share resources when possible; move to a true
* For format, see:
* https://developer.apple.com/library/mac/#documentation/Cocoa/Conceptual/PropertyLists/OldStylePlists/OldStylePLists.html
* And look at dat/example.conf
*/
public class SimilarityMetricConfigurator {
    PropertyListConfiguration configuration;

    public SimilarityMetricConfigurator(File file) throws ConfigurationException {
        this.configuration = new PropertyListConfiguration(file);
    }

    public List<SimilarityMetric> configure() throws IOException, ConfigurationException {
        List<SimilarityMetric> metrics = new ArrayList<SimilarityMetric>();
        ConfigurationNode root = configuration.getRootNode();
        for (ConfigurationNode child : root.getChildren()) {
            metrics.add(configureMetric(child));
        }

        return metrics;
    }

    private SimilarityMetric configureMetric(ConfigurationNode root) throws ConfigurationException, IOException {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        for (ConfigurationNode child : root.getChildren()) {
            params.put(child.getName(), child.getValue());
        }
        return configureMetric(params);
    }

    protected SimilarityMetric configureMetric(Map<String,Object> params) throws ConfigurationException, IOException {
        String name = requireString(params, "name");
        String type = requireString(params, "type");
        SimilarityMetric metric;
        if (type.equals("category")) {
            File luceneDir = requireDirectory(params, "lucene");
            IndexHelper helper = new IndexHelper(luceneDir, true);
            CategoryGraph graph = new CategoryGraph(helper);
            metric = new CatSimilarity(graph, helper);
        } else if (type.equals("text")) {
            File luceneDir = requireDirectory(params, "lucene");
            IndexHelper helper = new IndexHelper(luceneDir, true);
            String field = requireString(params, "field");
            metric = new TextSimilarity(helper, field);
        } else if (type.equals("pairwise")) {
            SparseMatrix m = new SparseMatrix(requireFile(params, "matrix"), false, PairwiseCosineSimilarity.PAGE_SIZE);
            SparseMatrix mt = new SparseMatrix(requireFile(params, "transpose"));
            metric = new PairwiseCosineSimilarity(m, mt);
        } else {
            throw new ConfigurationException("Unknown metric type: " + type);
        }
        metric.setName(name);
        return null;
    }

    protected String requireString(Map<String, Object> params, String key) throws ConfigurationException {
        Object val = params.get(key);
        if (val == null) {
            throw new ConfigurationException("Missing configuration parameter " + key);
        }
        if (!(val instanceof String)) {
            throw new ConfigurationException("expected " + key + " to be a string, was " + val.getClass().getName());
        }
        return (String)val;
    }
    protected File requireDirectory(Map<String, Object> params, String key) throws ConfigurationException {
        File f = new File(requireString(params, key));
        if (!f.isDirectory()) {
            throw new ConfigurationException("directory for parameter " + key + " = " + f + " does not exist.");
        }
        return f;
    }
    protected File requireFile(Map<String, Object> params, String key) throws ConfigurationException {
        File f = new File(requireString(params, key));
        if (!f.isFile()) {
            throw new ConfigurationException("file for parameter " + key + " = " + f + " does not exist.");
        }
        return f;
    }
    protected Integer requireInteger(Map<String, Object> params, String key) throws ConfigurationException {
        String s = requireString(params, key);
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            throw new ConfigurationException("parameter " + key + " = " + s + " is not an integer");
        }
    }
    protected Double requireDouble(Map<String, Object> params, String key) throws ConfigurationException {
        String s = requireString(params, key);
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            throw new ConfigurationException("parameter " + key + " = " + s + " is not a double");
        }
    }
}
