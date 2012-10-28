package edu.macalester.wpsemsim.lucene;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;

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
        this.searcher = new IndexSearcher(this.reader);
    }

    public int luceneIdToWpId(int luceneId) throws IOException {
        Set<String> fields = new HashSet<String>();
        fields.add("id");
        Document d = reader.document(luceneId, fields);
        return Integer.valueOf(d.getField("id").stringValue());
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
            Document d = reader.document(titleToLuceneId((title)));
            return Integer.valueOf(d.getField("id").stringValue());
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "fetching wp id for " + title + " failed:", e);
            return -1;
        }
    }
    public int titleToLuceneId(String title) {
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

    public IndexSearcher getSearcher() {
        return searcher;
    }
}
