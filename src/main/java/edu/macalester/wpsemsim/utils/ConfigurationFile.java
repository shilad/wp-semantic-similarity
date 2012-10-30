package edu.macalester.wpsemsim.utils;

import org.apache.commons.io.FileUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ConfigurationFile {
    private JSONObject conf;
    private File file;

    public ConfigurationFile(File file) throws IOException, ConfigurationException {
        this.file = file;
        String content = FileUtils.readFileToString(file);
        Object obj= null;
        try {
            obj = JSONValue.parseWithException(content);
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

    public JSONObject get(String key) {
        return (JSONObject) conf.get(key);
    }

    public Set<String> getKeys() {
        return conf.keySet();
    }

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

    public boolean isSimilarityMetric(String key) {
        if (!(conf.get(key) instanceof JSONObject)) {
            return false;
        }
        if (!get(key).containsKey("type")) {
            return false;
        }
        return get(key).get("type") instanceof String;
    }

    public JSONObject get() {
        return conf;
    }

    public File getPath() {
        return file;
    }


    public static class ConfigurationException extends Exception {
        public ConfigurationException(String message) {
            super(message);
        }
    }
}
