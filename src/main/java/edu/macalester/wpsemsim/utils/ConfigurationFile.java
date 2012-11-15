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
