package edu.macalester.wpsemsim.concepts;

import com.sleepycat.je.*;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * An implementation of a concept mapper that the Google wikipedia link dataset
 * available at: http://www-nlp.stanford.edu/pubs/crosswikis-data.tar.bz2/
 *
 * The database maps a string to a set of concepts associated with the string.
 * Each concept is a Wikipedia page, plus a set of URLs.
 * @see DictionaryEntry
 */
public class DictionaryMapper implements ConceptMapper {
    private static final Logger LOG = Logger.getLogger(DictionaryMapper.class.getName());
    private Environment env;
    private Database db;
    private IndexHelper helper;

    public DictionaryMapper(File path, IndexHelper helper) throws IOException, DatabaseException {
        this(path, helper, false);
    }

    /**
     * Creates a new dictionary mapper.
     * @param path Path to the directory containing the dictionary.
     * @param helper The indexhelper, used to retrieve metadata associated with specific titles
     * @param isNew If true, resets the mapper database.
     * @throws IOException
     * @throws DatabaseException
     */
    public DictionaryMapper(File path, IndexHelper helper, boolean isNew) throws IOException, DatabaseException {
        this.helper = helper;
        if (helper == null || !helper.hasField("redirect")) {
            LOG.warning("Helper not specified for concept mapper; will not be able to resolve redirects.");
        }
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

    /**
     * Add some entries to the concept dictionary. Replace anything that exists.
     * @param entries
     * @throws DatabaseException
     */
    public void put(List<DictionaryEntry> entries) throws DatabaseException {
        put(entries, true);
    }

    /**
     * Adds some entries to the concept dictionary.
     * @param entries
     * @param merge If true, merge entries with any current entries for the concept.
     * @throws DatabaseException
     */
    public void put(List<DictionaryEntry> entries, boolean merge) throws DatabaseException {
        put(new Record(entries), merge);
    }

    /**
     * Adds some entries to the concept dictionary.
     * @param record
     * @param merge If true, merge entries with any current entries for the concept.
     * @throws DatabaseException
     */
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

    /**
     * Adds a single entry to the concept dictionary.
     * @param entry
     * @param merge If true, merge entries with any current entries for the concept.
     * @throws DatabaseException
     */
    public void put(DictionaryEntry entry, boolean merge) throws DatabaseException {
        this.put(new ArrayList<DictionaryEntry>(Arrays.asList(entry)), merge);
    }

    /**
     * Gets the list of dictionary entries associated with a phrase.
     * @param text
     * @return
     * @throws DatabaseException
     */
    public List<DictionaryEntry> get(String text) throws DatabaseException {
        DatabaseEntry current = new DatabaseEntry();
        DatabaseEntry key = new DatabaseEntry(DictionaryEntry.normalize(text).getBytes());
        OperationStatus status = db.get(null, key, current, null);
        if (status.equals(OperationStatus.NOTFOUND)) {
            return new ArrayList<DictionaryEntry>();
        } else {
            Record r = new Record(current);
            r.sort();
            return r.entries;
        }
    }

    /**
     * Close and flush the concept database.
     * @throws DatabaseException
     */
    public void close() throws DatabaseException {
        this.db.close();
        this.env.close();
    }

    @Override
    public LinkedHashMap<String, Float> map(String text, int maxConcepts) {
        try {
            long sum = 0;  // total number of english links that contain the phrase.
            final Map<String, Float> s = new HashMap<String, Float>();
            for (DictionaryEntry e : get(text)) {
                String title = e.getArticle();
                try {
                    title = helper.followRedirects(title);
                } catch (IOException e1) {
                    LOG.log(Level.SEVERE, "error while following redirects: ", e1);
                }
                sum += e.getNumberEnglishLinks();
                if (title == null) {
                    continue;   // no article with that title found
                }
                if (s.containsKey(title)) {
                    s.put(title, s.get(title) + e.getNumberEnglishLinks());
                } else {
                    s.put(title, 1.0f * e.getNumberEnglishLinks());
                }
            }

            List<String> keys = new ArrayList<String>(s.keySet());
            Collections.sort(keys, new Comparator<String>() {
                @Override
                public int compare(String s1, String s2) {
                    return -1 * s.get(s1).compareTo(s.get(s2));
                }
            } );

            // normalize so that all entries sum to 1.0
            LinkedHashMap < String, Float > result = new LinkedHashMap<String, Float>();
            if (sum > 0) {
                for (String article : keys) {
                    if (result.size() >= maxConcepts) {
                        break;
                    }
                    result.put(article, s.get(article) / sum);
                }
            }
            return result;
        } catch (DatabaseException e) {
            LOG.log(Level.SEVERE, "concept mapping for '" + text + "' failed:", e);
            return null;
        }
    }

    /**
     * Convenience class that captures a record corresponding
     * to a single phrase in the database
     */
    public static class Record {
        List<DictionaryEntry> entries = new ArrayList<DictionaryEntry>();
        String text;

        Record(List<DictionaryEntry> entries) {
            assert(entries.size() > 0);
            this.entries = entries;
            text = entries.get(0).getNormalizedText();
        }
        Record(DictionaryEntry entry) {
            text = entry.getNormalizedText();
            this.add(entry);
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
        void sort() {
            Collections.sort(entries);
            Collections.reverse(entries);
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

    public static void main(String args[]) throws IOException, DatabaseException {
        IndexHelper helper = new IndexHelper(new File(args[1]), true);
        DictionaryMapper db = new DictionaryMapper(new File(args[0]), helper, false);
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("Enter phrase: ");
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            line = line.trim();

            Map<String, List<DictionaryEntry>> articlesToEntries = new HashMap<String, List<DictionaryEntry>>();
            for (DictionaryEntry entry : db.get(line)) {
                String article = entry.getArticle();
                String canonical = helper.followRedirects(article);
                if (!articlesToEntries.containsKey(canonical)) {
                    articlesToEntries.put(canonical, new ArrayList<DictionaryEntry>());
                }
                articlesToEntries.get(canonical).add(entry);
            }

            System.out.println("results for '" + line + "'");
            Map<String, Float> mapping = db.map(line, 500);
            System.out.println("top articles for '" + line + "'");
            for (String article : mapping.keySet()) {
                System.out.println("\t" + mapping.get(article) + ": " + article);
                List<DictionaryEntry> entries = articlesToEntries.get(article);
                Collections.sort(entries);
                Collections.reverse(entries);
                for (DictionaryEntry entry : entries) {
                    System.out.println("\t\t" + entry.toString());
                }
            }
        }
    }
}
