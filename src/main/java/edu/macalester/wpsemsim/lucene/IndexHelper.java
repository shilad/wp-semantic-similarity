package edu.macalester.wpsemsim.lucene;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides convience methods for a lucene index.
 */
public class IndexHelper {
    private static final Logger LOG = Logger.getLogger(IndexHelper.class.getName());

    private DirectoryReader reader;
    private IndexSearcher searcher;
    private File indexDir;

    /**
     * Creates a new helper for a Lucene index
     * @param indexDir The directory containing the lucene index
     * @param mmap If true, the directory is mmapp'ed
     * @throws IOException
     */
    public IndexHelper(File indexDir, boolean mmap) throws IOException {
        this.indexDir = indexDir;
        this.reader = DirectoryReader.open(
                mmap ? MMapDirectory.open(indexDir)
                        : FSDirectory.open(indexDir)
        );
        LOG.info("opening index helper for " + indexDir + " with " + reader.numDocs() + " docs");
        this.searcher = new IndexSearcher(this.reader);
    }

    /**
     * Converts a lucene id to a wikipedia id
     * @param luceneId
     * @return lucene id, or -1 if the WP id does not exist.
     * @throws IOException
     */
    public int luceneIdToWpId(int luceneId) throws IOException {
        Set<String> fields = new HashSet<String>();
        fields.add("id");
        Document d = reader.document(luceneId, fields);
        return Integer.valueOf(d.getField("id").stringValue());
    }

    /**
     * Gets all the Wikipedia IDs corresponding to "normal" pages that are not lists, redirects, etc.
     * @return
     * @throws IOException
     */
    public int[] getWpIds() throws IOException {
        if (hasField("type")) {
            Query query = new TermQuery(new Term("type", "normal"));
            ScoreDoc[] hits = searcher.search(query, null, Integer.MAX_VALUE).scoreDocs;
            int wpIds[] = new int[hits.length];
            Set<String> fields = new HashSet<String>(Arrays.asList("id"));
            for (int i = 0; i < hits.length; i++) {
                wpIds[i] = Integer.valueOf(reader.document(hits[i].doc, fields).get("id"));
            }
            return wpIds;
        } else {
            TIntList ids = new TIntArrayList();
            TermsEnum terms = MultiFields.getTerms(reader, "id").iterator(null);
            BytesRef ref;
            while((ref = terms.next()) != null) {
                ids.add(Integer.valueOf(ref.utf8ToString()));
            }
            ids.sort();
            return ids.toArray();
        }
    }

    /**
     * Returns the title for a lucene id.
     * @param luceneId
     * @return
     * @throws IOException
     */
    public String luceneIdToTitle(int luceneId) throws IOException {
        Set<String> fields = new HashSet<String>();
        fields.add("title");
        Document d = reader.document(luceneId, fields);
        return d.getField("title").stringValue();
    }

    /**
     * Retrieves the wikipedia id correspoding to a lucene id.
     * @param wpId
     * @return lucene id, or -1 if it does not exist.
     */
    public int wpIdToLuceneId(int wpId) {
        Query query = new TermQuery(new Term("id", "" + wpId));
        try {
            ScoreDoc[] hits = searcher.search(query, null, 1).scoreDocs;
            if (hits.length == 0) {
                return -1;
            } else {
                return hits[0].doc;
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "fetching lucene doc for wp id " + wpId + " failed:", e);
            return -1;
        }
    }

    /**
     * Retrieves the wikipedia id corresponding to a title.
     * @param title
     * @return Wikipedia ID, or -1 if it does not exist.
     */
    public int titleToWpId(String title) {
        try {
            int luceneId = titleToLuceneId(title);
            if (luceneId < 0) {
                return -1;
            }
            Document d = reader.document(luceneId);
            return Integer.valueOf(d.getField("id").stringValue());
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "fetching wp id for " + title + " failed:", e);
            return -1;
        }
    }

    /**
     * Retrieves the lucene id corresponding to a title.
     * @param title
     * @return Lucene Id, or -1 if it does not exist.
     */
    public int titleToLuceneId(String title) {
        title = title.replaceAll("_", " ");
        Query query = new TermQuery(new Term("title", title));
        try {
            ScoreDoc[] hits = searcher.search(query, null, 1).scoreDocs;
            if (hits.length == 0) {
                return -1;
            } else {
                return hits[0].doc;
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "fetching wp id for " + title + " failed:", e);
            return -1;
        }
    }

    /**
     * Retrieves the lucene doc corresponding to a Wikipedia ID.
     * @param wpId
     * @return Lucene Document or null if it does not exist.
     */
    public Document wpIdToLuceneDoc(int wpId) throws IOException {
        int docId = wpIdToLuceneId(wpId);
        if (docId >= 0) {
            return reader.document(docId);
        } else {
            return null;
        }
    }

    /**
     * Retrieves the lucene doc corresponding to a Wikipedia ID.
     * @param title
     * @return Lucene Document or null if it does not exist.
     * @throws IOException
     */
    public Document titleToLuceneDoc(String title) throws IOException {
        int docId = titleToLuceneId(title);
        if (docId >= 0) {
            return reader.document(docId);
        } else {
            return null;
        }
    }


    /**
     * Retrieves the title associated with a  particular Wikipedia ID.
     * @param wpId
     * @return title, or null if wpId does not exist.
     * @throws IOException
     */
    public String wpIdToTitle(int wpId) {
        Query query = new TermQuery(new Term("id", "" + wpId));
        try {
            ScoreDoc[] hits = searcher.search(query, null, 1).scoreDocs;
            if (hits.length == 0) {
                return null;
            } else {
                return searcher.doc(hits[0].doc).get("title");
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "fetching title for wp id " + wpId + " failed:", e);
            return "unknown";
        }
    }

    public DirectoryReader getReader() {
        return reader;
    }

    /**
     * This must be a
     * @param wpId
     * @return
     * @throws IOException
     */
    public TIntList getLinkedLuceneIdsForWpId(int wpId) throws IOException {
        if (!hasField(Page.FIELD_LINKS)) {
            throw new UnsupportedOperationException("index does not have a field called 'links'");
        }

        int luceneId = wpIdToLuceneId(wpId);
        if (luceneId < 0) {
            LOG.info("no lucene id associated with wpId " + wpId);
            return new TIntArrayList();
        }
        return getLinkedLuceneIdsForLuceneId(luceneId);
    }

    /**
     * Returns a list of all lucene ids that the specified id links TO.
     * Filters out any links that don't correspond to lucene ids.
     * Each lucene id appears at most once.
     * @param luceneId
     * @return
     * @throws IOException
     */
    public TIntList getLinkedLuceneIdsForLuceneId(int luceneId) throws IOException {
        TIntArrayList result = new TIntArrayList();
        Set<Integer> finished = new HashSet<Integer>();
        for (IndexableField f : reader.document(luceneId).getFields(Page.FIELD_LINKS)) {
            int wpId = Integer.valueOf(f.stringValue());
            if (finished.contains(wpId)) { continue; }
            int luceneId2 = wpIdToLuceneId(wpId);
            if (luceneId2 < 0) {
//                LOG.info("no lucene id associated with link title " + f.stringValue());
            } else {
//                System.out.println("id of " + f.stringValue() + " is " + luceneId2);
                result.add(luceneId2);
            }
            finished.add(wpId);
        }
//        System.err.println("mapped " + wpId + " to " + result);
        return result;
    }

    /**
     * Returns true if the index contains at least one document with the given field.
     * @param field
     * @return
     * @throws IOException
     */
    public boolean hasField(String field) throws IOException {
        Iterator<String> fields = MultiFields.getFields(reader).iterator();
        while (fields.hasNext()) {
            if (fields.next().equals(field)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the number of documents that have a field with the specified term.
     * @param field
     * @param term
     * @return
     * @throws IOException
     */
    public long getDocFreq(String field, String term) throws IOException {
        return reader.docFreq(new Term(field, term));
    }

    /**
     * Returns the final title, after following up to 10 redirects.
     * @param title
     * @return
     * @throws IOException
     */
    public String followRedirects(String title) throws IOException {
        for (int i = 0; i < 10; i++) {
            Document d = titleToLuceneDoc(title);
            if (d == null) {
                return null;
            }
            String redirect = d.get("redirect");
            if (StringUtils.isEmpty(redirect)) {
                break;
            }
            title = redirect;
        }
        return title;
    }

    public IndexSearcher getSearcher() {
        return searcher;
    }
}
