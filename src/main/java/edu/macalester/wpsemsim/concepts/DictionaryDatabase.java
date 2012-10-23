package edu.macalester.wpsemsim.concepts;

import com.sleepycat.je.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DictionaryDatabase implements ConceptMapper {
    private static final Logger LOG = Logger.getLogger(DictionaryDatabase.class.getName());
    private Environment env;
    private Database db;

    public DictionaryDatabase(File path) throws IOException, DatabaseException {
        this(path, false);
    }
    public DictionaryDatabase(File path, boolean isNew) throws IOException, DatabaseException {
        if (isNew) {
            if (path.isDirectory()) {
                FileUtils.deleteDirectory(path);
            } else if (path.isFile()) {
                path.delete();
            }
            path.mkdirs();
        }
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setTransactional(false);
        envConfig.setAllowCreate(true);
        this.env = new Environment(path, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        this.db = env.openDatabase(null,
                FilenameUtils.getName(path.toString()),
                dbConfig);
    }

    public void put(List<DictionaryEntry> entries) throws DatabaseException {
        put(entries, true);
    }

    public void put(List<DictionaryEntry> entries, boolean merge) throws DatabaseException {
        put(new Record(entries), merge);
    }

    public void put(Record record, boolean merge) throws DatabaseException {
        if (merge) {
            DatabaseEntry current = new DatabaseEntry();
            OperationStatus status = db.get(null, record.getDatabaseKey(), current, null);
            if (status != OperationStatus.NOTFOUND) {
                assert(status == OperationStatus.SUCCESS);
                record.merge(current);
            }
        }

        db.put(null, record.getDatabaseKey(), record.getDatabaseValue());
    }

    public void put(DictionaryEntry entry, boolean merge) throws DatabaseException {
        ArrayList<DictionaryEntry> entries = new ArrayList<DictionaryEntry>();
        entries.add(entry);
        this.put(entries, merge);
    }

    public List<DictionaryEntry> get(String text) throws DatabaseException {
        DatabaseEntry current = new DatabaseEntry();
        DatabaseEntry key = new DatabaseEntry(DictionaryEntry.normalize(text).getBytes());
        System.err.println("in get key is " + new String(key.getData()));
        OperationStatus status = db.get(null, key, current, null);
        if (status.equals(OperationStatus.NOTFOUND)) {
            return new ArrayList<DictionaryEntry>();
        } else {
            return new Record(current).entries;
        }
    }

    public void close() throws DatabaseException {
        this.db.close();
        this.env.close();
    }

    @Override
    public LinkedHashMap<String, Float> map(String text) {
        LinkedHashMap<String, Float> mapping = new LinkedHashMap<String, Float>();
        try {
            List<DictionaryEntry> entries = get(text);
            Collections.sort(entries);
            Collections.reverse(entries);
            for (DictionaryEntry e : entries) {
                mapping.put(e.getArticle(), e.getFraction());
            }
        } catch (DatabaseException e) {
            LOG.log(Level.SEVERE, "concept mapping for '" + text + "' failed:", e);
        }
        return mapping;
    }

    public static class Record {
        List<DictionaryEntry> entries = new ArrayList<DictionaryEntry>();
        String text;

        Record(List<DictionaryEntry> entries) {
            assert(entries.size() > 0);
            this.entries = entries;
            text = entries.get(0).getNormalizedText();
        }
        Record(DictionaryEntry entry) {
            this.add(entry);
            text = entries.get(0).getNormalizedText();
        }
        Record(DatabaseEntry entry) {
            for (String line : new String(entry.getData()).split("\n")) {
                entries.add(new DictionaryEntry(line));
            }
            text = entries.get(0).getNormalizedText();
        }
        void add(DictionaryEntry entry) {
            assert(shouldContainEntry(entry));
            this.entries.add(entry);
        }
        boolean shouldContainEntry(DictionaryEntry entry) {
            return (entry.getNormalizedText().equals(text));
        }
        void merge(DatabaseEntry entry) {
            for (DictionaryEntry e : new Record(entry).entries) {
                this.add(e);
            }
        }
        DatabaseEntry getDatabaseKey() {
            return new DatabaseEntry(text.getBytes());
        }
        DatabaseEntry getDatabaseValue() {
            StringBuffer buff = new StringBuffer();
            for (DictionaryEntry entry : entries) {
                if (buff.length() > 0) {
                    buff.append("\n");
                }
                buff.append(entry.toString());
            }
            return new DatabaseEntry(buff.toString().getBytes());
        }
    }
}
