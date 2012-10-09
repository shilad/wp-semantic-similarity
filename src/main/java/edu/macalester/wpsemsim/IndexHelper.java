package edu.macalester.wpsemsim;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.util.Version;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IndexHelper {
    private static final Logger LOG = Logger.getLogger(IndexHelper.class.getName());

    private DirectoryReader reader;
    private IndexSearcher searcher;

    public IndexHelper(DirectoryReader reader) {
        this.reader = reader;
        this.searcher = new IndexSearcher(this.reader);
    }

    public int luceneIdToWpId(int luceneId) throws IOException {
        Set<String> fields = new HashSet<String>();
        fields.add("id");
        Document d = reader.document(luceneId, fields);
        return d.getField("id").numericValue().intValue();
    }

    public String luceneIdToTitle(int luceneId) throws IOException {
        Set<String> fields = new HashSet<String>();
        fields.add("title");
        Document d = reader.document(luceneId, fields);
        return d.getField("title").stringValue();
    }


    public String wpIdToTitle(int wpId) {
        Query query = NumericRangeQuery.newIntRange("id", wpId, wpId, true, true);
        try {
            ScoreDoc[] hits = searcher.search(query, null, 1).scoreDocs;
            if (hits.length == 0) {
                return null;
            } else {
                return searcher.doc(hits[0].doc).get("title");
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "fetching title for wp id " + wpId + " failed:", e);
        }
        return "unknown";
    }
}
