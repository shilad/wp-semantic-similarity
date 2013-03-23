package edu.macalester.wpsemsim.utils;

import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.sim.SimilarityMetric;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A container for all the components needed to create similarity metrics.
 */
public class Env {
    /**
     * Mappers, indexes, and metrics with this name will be considered
     * the "official" version when more than one exist.
     */
    public static final String MAIN_KEY = "main";

    private ConfigurationFile config;
    private Map<String, ConceptMapper> mappers = new HashMap<String, ConceptMapper>();
    private Map<String, SimilarityMetric> metrics = new HashMap<String, SimilarityMetric>();
    private Map<String, IndexHelper> indexes = new HashMap<String, IndexHelper>();
    private List<KnownSim> gold;

    public Env(ConfigurationFile file) { this.config = file; }

    public void addMapper(String name, ConceptMapper mapper) {
        this.mappers.put(name, mapper);
    }

    public boolean hasMapper(String name) {
        return getMapper(name) != null;
    }

    public ConceptMapper getMapper(String name) {
        return mappers.get(name);
    }

    public ConceptMapper getMainMapper() {
        return mappers.get(MAIN_KEY);
    }

    public void setMainMapper(ConceptMapper mapper) {
        mappers.put(MAIN_KEY, mapper);
    }

    public void addMetric(String name, SimilarityMetric metric) {
        this.metrics.put(name, metric);
    }

    public SimilarityMetric getMetric(String name) {
        return metrics.get(name);
    }

    public boolean hasMetric(String name) {
        return getMetric(name) != null;
    }

    public void addIndex(String name, IndexHelper helper) {
        this.indexes.put(name, helper);
    }

    public IndexHelper getIndex(String name) {
        return indexes.get(name);
    }

    public IndexHelper getMainIndex() {
        return indexes.get(MAIN_KEY);
    }

    public boolean hasIndex(String name) {
        return getIndex(name) != null;
    }

    public Map<String, SimilarityMetric> getMetrics() {
        return metrics;
    }

    public List<KnownSim> getGold(){
        return gold;
    }

    public void setGold(List<KnownSim> g){
        gold=g;
    }
}
