package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.lucene.Page;
import edu.macalester.wpsemsim.utils.DocScoreList;
import gnu.trove.map.TIntLongMap;
import gnu.trove.map.hash.TIntLongHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Terms;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.IOException;

/**
 * }
 */
public class LinkSimilarity extends BaseSimilarityMetric{
    private String field;

    public static enum SimFn {
        TFIDF,
        GOOGLE,
        LOGODDS,
        JACARD
    };

    private IndexHelper linkHelper;
    private int minDocFreq = 0;
    private SimFn similarity = SimFn.GOOGLE;

    public LinkSimilarity(ConceptMapper mapper, IndexHelper linkHelper, IndexHelper mainHelper, String field) {
        super(mapper, mainHelper);
        this.field = field;
        this.linkHelper = linkHelper;
    }

    public void setMinDocFreq(int n) {
        this.minDocFreq = n;
    }

    public void setSimilarity(SimFn fn) {
        this.similarity = fn;
    }

    @Override
    public double similarity(int wpId1, int wpId2) throws IOException {
        TIntSet A = getLinks(wpId1);
        TIntSet B = getLinks(wpId2);
        if (A == null || B == null) {
            return Double.NaN;
        }
        TIntSet I = new TIntHashSet(A); I.retainAll(B); // intersection
        TIntSet U = new TIntHashSet(A); U.addAll(B);    // union
        if (I.size() == 0) {
            return 0;
        }

        double val;
        if (similarity == SimFn.GOOGLE) {
            val = googleDistance(A, B, I);
        } else if (similarity == SimFn.TFIDF) {
            val = tfidf(A, B, I);
        } else if (similarity == SimFn.LOGODDS) {
            val = logOdds(A, B, I);
        } else if (similarity == SimFn.JACARD) {
            val = jacard(A, B, I, U);
        } else {
            throw new IllegalStateException("" + similarity);
        }

//        System.err.println("val is " + val);
        return val;
    }

    private double jacard(TIntSet A, TIntSet B, TIntSet I, TIntSet U) {
        return 1.0 * I.size() / (U.size() + 1);
    }
    private double googleDistance(TIntSet A, TIntSet B, TIntSet I) throws IOException {
        int numArticles = linkHelper.getReader().numDocs();
        double distance = (Math.log(Math.max(A.size(), B.size())) - Math.log(I.size()))
                /   (Math.log(numArticles) - Math.log(Math.min(A.size(), B.size())));
        if (distance > 0.5) {
            double x = 10 * (distance - 0.5);   // starts at 0, grows quickly
            distance = 1.0 / (1 + Math.exp(-x)); // sigmoid
        }
        return 1 - distance;
    }

    private double logOdds(TIntSet A, TIntSet B, TIntSet I) throws IOException {
        long n = linkHelper.getReader().numDocs();
        double val = 0.0;
        for (int id : I.toArray()) {
            long d = getDocFreq(id);
            double pz = (1.0 * d / n);
            double px = (1.0 / A.size());
            double py = (1.0 / B.size());
            val += Math.log(px) + Math.log(py) - 2 * Math.log(pz);
        }
        val = Math.log(1 + val);
        return Math.min(1.0, val / 7.0);
    }

    private double tfidf(TIntSet A, TIntSet B, TIntSet I) throws IOException {
        double dot = 0.0;
        for (int id : I.toArray()) {
            dot += Math.pow(getIdf(id), 2.0);   // all other elements are 0
        }
        return (10 + Math.log(dot / Math.sqrt(norm(A) * norm(B)))) / 10.0;
    }

    TIntLongMap docFreqCache = new TIntLongHashMap();
    private long getDocFreq(int wpId) throws IOException {
        synchronized (docFreqCache) {
            if (docFreqCache.containsKey(wpId)) {
                return docFreqCache.get(wpId);
            }
        }
        long freq = linkHelper.getDocFreq(field, "" + wpId);
        synchronized (docFreqCache) {
            docFreqCache.put(wpId, freq);
        }
        return freq;
    }

    private double getIdf(int wpId) throws IOException {
        return 1.0 / Math.sqrt(Math.max(2, getDocFreq(wpId)));
    }

    private double norm(TIntSet X) throws IOException {
        double norm = 0.0;
        for (int id : X.toArray()) {
            norm += Math.pow(getIdf(id), 2.0);
        }
        return norm;
    }

    private TIntSet getLinks(int wpId) throws IOException {
        Document d = linkHelper.wpIdToLuceneDoc(wpId);
        if (d == null) {
            return null;
        }
        TIntSet links = new TIntHashSet();
        for (IndexableField f : d.getFields(field)) {
            int wpId2 = Integer.valueOf(f.stringValue());
            if (getDocFreq(wpId2) >= minDocFreq) {
                links.add(wpId2);
            } else {
//                System.out.println("skipping " + wpId2);
            }
        }
        return links;
    }

    @Override
    public DocScoreList mostSimilar(int wpId1, int maxResults) throws IOException {
        throw new NotImplementedException();
    }
}
