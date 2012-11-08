package edu.macalester.wpsemsim.lucene;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.search.similarities.TFIDFSimilarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IndexHelper {
    private static final Logger LOG = Logger.getLogger(IndexHelper.class.getName());

    private DirectoryReader reader;
    private IndexSearcher searcher;
    private File indexDir;

    public IndexHelper(File indexDir, boolean mmap) throws IOException {
        this.indexDir = indexDir;
        this.reader = DirectoryReader.open(
                mmap ? MMapDirectory.open(indexDir)
                        : FSDirectory.open(indexDir)
        );
        LOG.info("opening index helper for " + indexDir + " with " + reader.numDocs() + " docs");
        this.searcher = new IndexSearcher(this.reader);
//        searcher.setSimilarity(new ESASimilarity());
    }

    public int luceneIdToWpId(int luceneId) throws IOException {
        Set<String> fields = new HashSet<String>();
        fields.add("id");
        Document d = reader.document(luceneId, fields);
        return Integer.valueOf(d.getField("id").stringValue());
    }

    public int[] getWpIds() throws IOException {
        TIntList ids = new TIntArrayList();
        TermsEnum terms = MultiFields.getTerms(reader, "id").iterator(null);
        BytesRef ref;
        while((ref = terms.next()) != null) {
            ids.add(Integer.valueOf(ref.utf8ToString()));
        }
        ids.sort();
        return ids.toArray();
    }

    public String luceneIdToTitle(int luceneId) throws IOException {
        Set<String> fields = new HashSet<String>();
        fields.add("title");
        Document d = reader.document(luceneId, fields);
        return d.getField("title").stringValue();
    }

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
    public TIntList getLinkedLuceneIds(int wpId) throws IOException {
        if (!hasField("links")) {
            throw new UnsupportedOperationException("index does not have a field called 'links'");
        }

        int luceneId = wpIdToLuceneId(wpId);
        if (luceneId < 0) {
//            LOG.info("no lucene id associated with wpId " + wpId);
            return new TIntArrayList();
        }
        TIntArrayList result = new TIntArrayList();
        for (IndexableField f : reader.document(luceneId).getFields("links")) {
            int luceneId2 = titleToLuceneId(f.stringValue());
            if (luceneId2 < 0) {
//                LOG.info("no lucene id associated with link title " + f.stringValue());
            } else {
//                System.out.println("id of " + f.stringValue() + " is " + luceneId2);
                result.add(luceneId2);
            }
        }
//        System.err.println("mapped " + wpId + " to " + result);
        return result;
    }

    public boolean hasField(String field) throws IOException {
        FieldsEnum fields = MultiFields.getFields(reader).iterator();
        while (true) {
            String f = fields.next();
            if (f == null) {
                return false;
            }
            if (f.equals(field)) {
                return true;
            }
        }
    }

    public IndexSearcher getSearcher() {
        return searcher;
    }
    public class ESASimilarity extends DefaultSimilarity {
        @Override
        public float idf(long docFreq, long numDocs) {
            return (float) Math.log(numDocs / (double) docFreq);
        }

        @Override
        public float tf(float freq) {
            return (float) (1.0 + Math.log(freq));
        }
    }
}
