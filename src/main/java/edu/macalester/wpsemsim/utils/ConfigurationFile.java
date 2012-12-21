package edu.macalester.wpsemsim.utils;

import org.apache.commons.io.FileUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Contains the configuration for the similarity metric environment in JSON format.
 * See conf/example.configuration.json.
 */
public class ConfigurationFile {
    private JSONObject conf;
    private File file;

    public ConfigurationFile(File file) throws IOException, ConfigurationException {
        this.file = file;

        // HACK: treat lines that start with "//" as comments
        List<String> lines = FileUtils.readLines(file);
        StringBuffer content = new StringBuffer();
        for (String line : lines) {
            if (!line.trim().startsWith("//")) {
                content.append(line);
            }
        }

        Object obj= null;
        try {
            obj = JSONValue.parseWithException(content.toString());
        } catch (ParseException e) {
            throw new ConfigurationException("parse error in configuration file " + file + ": " + e.toString());
        }
        if (!(obj instanceof JSONObject)) {
            throw new ConfigurationException("configuration file: " + file + " is not a json map.");
        }
        conf = (JSONObject)obj;
    }

    public Object getPrimitive(String key) {
        return conf.get(key);
    }

    public JSONObject get(String ...keys) {
        JSONObject obj = conf;
        for (int i = 0; i < keys.length; i++) {
            Object val = obj.get(keys[i]);
            if (val == null) {
                throw new RuntimeException("missing key: " + keys[i]);
            }
            if (!(val instanceof JSONObject)) {
                throw new RuntimeException("key " + keys[i] + " is not a JSONObject. It is a " + val.getClass().getName());
            }
            obj = (JSONObject) val;
        }
        return obj;
    }

    public Set<String> getKeys(String ...keys) {
        return get(keys).keySet();
    }


    public JSONObject get() {
        return conf;
    }

    public File getPath() {
        return file;
    }
    /**
     * Gets the configuration for all indexes.
     * @return
     */
    public JSONObject getIndexes() {
        return get("indexes");
    }

    /**
     * Returns the configuration of a particular index
     * @param name Name of index.
     * @return Configuration for that index.
     */
    public JSONObject getIndex(String name) {
        if (!get("indexes").containsKey(name)) {
            throw new IllegalArgumentException("no index named " + name);
        }
        return (JSONObject) get("indexes").get(name);
    }

    /**
     * Gets the configuration for all metrics.
     * @return
     */
    public JSONObject getMetrics() {
        return get("metrics");
    }

    /**
     * Returns the configuration of a particular metric.
     * @param name Name of metric.
     * @return Configuration for that metric.
     */
    public JSONObject getMetric(String name) {
        if (!get("metrics").containsKey(name)) {
            throw new IllegalArgumentException("no metric named " + name);
        }
        return (JSONObject)get("metrics").get(name);
    }

    /**
     * The configuration of all mappers.
     * @return
     */
    public JSONObject getMappers() {
        return get("mappers");
    }

    /**
     * The configuration of a specific mapper.
     * @param name
     * @return
     */
    public JSONObject getMapper(String name) {
        if (!get("mappers").containsKey(name)) {
            throw new IllegalArgumentException("no mapper named " + name);
        }
        return (JSONObject) getMappers().get(name);
    }


    public static class ConfigurationException extends Exception {
        public ConfigurationException(String message) {
            super(message);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////
    //
    // The remaining methods are utilities that are helpful in extracting specific
    // types from configuration files.
    //
    ///////////////////////////////////////////////////////////////////////////////

    public static String requireString(Map<String, Object> params, String key) throws ConfigurationException {
        Object val = params.get(key);
        if (val == null) {
            throw new ConfigurationException("Missing configuration parameter " + key);
        }
        if (!(val instanceof String)) {
            throw new ConfigurationException("expected " + key + " to be a string, was " + val.getClass().getName());
        }
        return (String)val;
    }
    public static Integer requireInteger(Map<String, Object> params, String key) throws ConfigurationException {
        Object val = params.get(key);
        if (val == null) {
            throw new ConfigurationException("Missing configuration parameter " + key);
        }
        if (!(val instanceof Number)) {
            throw new ConfigurationException("expected " + key + " to be an integer, was " + val.getClass().getName());
        }
        return ((Number)val).intValue();
    }
    public static Double requireDouble(Map<String, Object> params, String key) throws ConfigurationException {
        Object val = params.get(key);
        if (val == null) {
            throw new ConfigurationException("Missing configuration parameter " + key);
        }
        if (!(val instanceof Number)) {
            throw new ConfigurationException("expected " + key + " to be a Double, was " + val.getClass().getName());
        }
        return ((Number)val).doubleValue();
    }
    public static boolean requireBoolean(Map<String, Object> params, String key) throws ConfigurationException {
        Object val = params.get(key);
        if (val == null) {
            throw new ConfigurationException("Missing configuration parameter " + key);
        }
        if (!(val instanceof Boolean)) {
            throw new ConfigurationException("expected " + key + " to be a Boolean, was " + val.getClass().getName());
        }
        return ((Boolean)val);
    }
    public static File requireDirectory(Map<String, Object> params, String key) throws ConfigurationException {
        File f = new File(requireString(params, key));
        if (!f.isDirectory()) {
            throw new ConfigurationException("directory for parameter " + key + " = " + f + " does not exist.");
        }
        return f;
    }
    public static File requireFile(Map<String, Object> params, String key) throws ConfigurationException {
        File f = new File(requireString(params, key));
        if (!f.isFile()) {
            throw new ConfigurationException("file for parameter " + key + " = " + f + " does not exist.");
        }
        return f;
    }
    public static List<String> requireListOfStrings(Map<String, Object> params, String key) throws ConfigurationException {
        Object val = params.get(key);
        if (val == null) {
            throw new ConfigurationException("Missing configuration parameter " + key);
        }
        if (!(val instanceof List)) {
            throw new ConfigurationException("expected " + key + " to be a list, was " + val.getClass().getName());
        }
        List lval = (List)val;
        for (Object o : lval) {
            if (!(o instanceof String)) {
                throw new ConfigurationException("expected " + o + " to be a string, was " + o.getClass().getName());

            }
        }
        return (List<String>)lval;
    }
    public static List<Integer> requireListOfIntegers(Map<String, Object> params, String key) throws ConfigurationException {
        Object val = params.get(key);
        if (val == null) {
            throw new ConfigurationException("Missing configuration parameter " + key);
        }
        if (!(val instanceof List)) {
            throw new ConfigurationException("expected " + key + " to be a list, was " + val.getClass().getName());
        }
        List lval = (List)val;
        List<Integer> result = new ArrayList<Integer>();
        for (Object o : lval) {
            if (o instanceof Number) {
                result.add(((Number)o).intValue());
            } else {
                throw new ConfigurationException("expected " + o + " to be a number, was " + o.getClass().getName());
            }

        }
        return result;
    }
}
