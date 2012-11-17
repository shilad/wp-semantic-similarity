package edu.macalester.wpsemsim.sim;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.concepts.DictionaryDatabase;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.matrix.SparseMatrix;
import edu.macalester.wpsemsim.matrix.SparseMatrixRow;
import edu.macalester.wpsemsim.utils.DocScoreList;
import gnu.trove.map.hash.TIntDoubleHashMap;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.surround.parser.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PairwisePhraseSimilarity extends BaseSimilarityMetric implements SimilarityMetric {
    private static final Logger LOG = Logger.getLogger(PairwisePhraseSimilarity.class.getName());
    public static final int DEFAULT_MAX_PERCENTAGE = 10;
    public static final int DEFAULT_MAX_QUERY_TERMS = 100;
    public static final int DEFAULT_MIN_TERM_FREQ = 2;
    public static final int DEFAULT_MIN_DOC_FREQ = 2;

    private int maxPercentage = DEFAULT_MAX_PERCENTAGE;
    private int maxQueryTerms = DEFAULT_MAX_QUERY_TERMS;
    private int minTermFreq = DEFAULT_MIN_TERM_FREQ;
    private int minDocFreq = DEFAULT_MIN_DOC_FREQ;
    private IndexSearcher searcher;
    private IndexHelper helper;
    private DirectoryReader reader;
    private Analyzer analyzer = new ESAAnalyzer();
    private SparseMatrix pairwise;

    public PairwisePhraseSimilarity(IndexHelper helper, SparseMatrix pairwise) {
        super(null, helper);
        this.helper = helper;
        this.reader = helper.getReader();
        this.searcher = helper.getSearcher();
        searcher.setSimilarity(new LuceneSimilarity());
        this.pairwise = pairwise;
        this.setName("pairwise-phrase-similarity");
    }

    private MoreLikeThis getMoreLikeThis() {
        MoreLikeThis mlt = new MoreLikeThis(reader); // Pass the reader reader
        mlt.setMaxDocFreqPct(maxPercentage);
        mlt.setMaxQueryTerms(maxQueryTerms);
        mlt.setMinDocFreq(minDocFreq);
        mlt.setMinTermFreq(minTermFreq);
        mlt.setAnalyzer(analyzer);
        mlt.setFieldNames(new String[]{"text"}); // specify the fields for similiarity
        return mlt;
    }

    @Override
    public double similarity(String phrase1, String phrase2) throws IOException, ParseException {
        TIntDoubleHashMap scores1 = getConceptVector(phrase1);
        TIntDoubleHashMap scores2 = getConceptVector(phrase2);
        return Math.log(cosineSimilarity(scores1, scores2) + 0.001);
    }

    private double cosineSimilarity(TIntDoubleHashMap X, TIntDoubleHashMap Y) {
        double xDotX = 0.0;
        double yDotY = 0.0;
        double xDotY = 0.0;

        for (int id : X.keys()) {
            double x = X.get(id);
            xDotX += x * x;
            if (Y.containsKey(id)) {
                xDotY += x * Y.get(id);
            }
        }
        for (int id : Y.keys()) {
            double y = Y.get(id);
            yDotY += y * y;
        }

        if (yDotY == 0.0) {
            return Double.NaN;
        } else {
            return xDotY / Math.sqrt(xDotX * yDotY);
        }

    }

    private Map<String, TIntDoubleHashMap> phraseCache = new HashMap<String, TIntDoubleHashMap>();
    public TIntDoubleHashMap getConceptVector(String phrase) throws IOException {
        synchronized (phraseCache) {
            if (phraseCache.containsKey(phrase)) {
                return phraseCache.get(phrase);
            }
        }
        QueryParser parser = new QueryParser(Version.LUCENE_40, "text", analyzer);
//        Filter filter = NumericRangeFilter.newIntRange("ninlinks", 100, Integer.MAX_VALUE, true, false);
        TopDocs docs = null;
        try {
            docs = searcher.search(parser.parse(phrase), null, 5000);
        } catch (org.apache.lucene.queryparser.classic.ParseException e) {
            LOG.log(Level.WARNING, "parsing of phrase " + phrase + " failed", e);
            return null;
        }
        pruneSimilar(docs);
        TIntDoubleHashMap result = expandScores(docs.scoreDocs);
        synchronized (phraseCache) {
            phraseCache.put(phrase, result);
        }
        return result;
    }

    private void pruneSimilar(TopDocs docs) throws IOException {
        if (docs.scoreDocs.length == 0) {
            return;
        }
        int cutoff = docs.scoreDocs.length;
        double threshold = 0.005 * docs.scoreDocs[0].score;
        for (int i = 0, j = 100; j < docs.scoreDocs.length; i++, j++) {
            float delta = docs.scoreDocs[i].score - docs.scoreDocs[j].score;
            if (delta < threshold) {
                cutoff = j;
                break;
            }
        }
        if (cutoff < docs.scoreDocs.length) {
            docs.scoreDocs = ArrayUtils.subarray(docs.scoreDocs, 0, cutoff);
        }
    }

    private TIntDoubleHashMap expandScores(ScoreDoc scores[]) throws IOException {
        TIntDoubleHashMap expanded = new TIntDoubleHashMap();
        for (ScoreDoc sd : scores) {
            int wpId = helper.luceneIdToWpId(sd.doc);
            if (wpId < 0) {
                continue;
            }
            expanded.adjustOrPutValue(wpId, sd.score, sd.score);
        }

        double alpha = 0.5;
        for (ScoreDoc sd : scores) {
            int wpId = helper.luceneIdToWpId(sd.doc);
            if (wpId < 0) {
                continue;
            }
            SparseMatrixRow row = pairwise.getRow(wpId);
            for (int i = 0; i < row.getNumCols(); i++) {
                int wpId2 = row.getColIndex(i);
                if (expanded.contains(wpId2)) {
                    float val = row.getColValue(i);
                    expanded.adjustOrPutValue(wpId2, alpha * sd.score * val, alpha * sd.score * val);
                }
            }
        }
        return expanded;
    }

    @Override
    public DocScoreList mostSimilar(int wpId, int maxResults) throws IOException {
        MoreLikeThis mlt = getMoreLikeThis();
        int luceneId = helper.wpIdToLuceneId(wpId);
        TopDocs similarDocs = searcher.search(mlt.like(luceneId), maxResults);
        DocScoreList scores = new DocScoreList(similarDocs.scoreDocs.length);
        for (int i = 0; i < similarDocs.scoreDocs.length; i++) {
            ScoreDoc sd = similarDocs.scoreDocs[i];
            scores.set(i,
                    helper.luceneIdToWpId(sd.doc),
                    similarDocs.scoreDocs[i].score);
        }
        return scores;
    }

    @Override
    public double similarity(int wpId1, int wpId2) throws IOException {
        int doc1 = helper.wpIdToLuceneId(wpId1);
        int doc2 = helper.wpIdToLuceneId(wpId2);

        MoreLikeThis mlt = getMoreLikeThis();
        TopDocs similarDocs = searcher.search(mlt.like(doc1), new FieldCacheTermsFilter("id", "" + wpId2), 1);
        if (similarDocs.scoreDocs.length == 0) {
            return 0;
        } else {
            assert(similarDocs.scoreDocs.length == 1);
            assert(similarDocs.scoreDocs[0].doc == doc2);
            return similarDocs.scoreDocs[0].score;
        }
    }

    public void setMaxPercentage(int maxPercentage) {
        this.maxPercentage = maxPercentage;
    }

    public void setMaxQueryTerms(int maxQueryTerms) {
        this.maxQueryTerms = maxQueryTerms;
    }

    public void setMinTermFreq(int minTermFreq) {
        this.minTermFreq = minTermFreq;
    }

    public void setMinDocFreq(int minDocFreq) {
        this.minDocFreq = minDocFreq;
    }

    public static class LuceneSimilarity extends DefaultSimilarity {
        @Override
        public float idf(long docFreq, long numDocs) {
            return (float) Math.log(numDocs / (double) docFreq);
        }

        @Override
        public float tf(float freq) {
            return (float) (1.0 + Math.log(freq));
        }
    }

    public static void main(String args[]) throws IOException, InterruptedException, CompressorException, ParseException, DatabaseException {
        if (args.length != 4 && args.length != 5) {
            System.err.println("usage: java " +
                    PairwisePhraseSimilarity.class.getName() +
                    " lucene-index-dir output-file num-results [num-threads]");

        }
//        IndexHelper helper = new IndexHelper(new File(args[0]), true);
//        ESASimilarity sim = new ESASimilarity(helper);
//        int cores = (args.length == 5)
//                ? Integer.valueOf(args[4])
//                : Runtime.getRuntime().availableProcessors();
//        PairwiseSimilarityWriter writer = new PairwiseSimilarityWriter(sim, new File(args[2]));
//        writer.writeSims(helper.getWpIds(), cores, Integer.valueOf(args[3]));
//        IndexHelper helper = new IndexHelper(new File("dat/lucene/esa"), true);
//        ConceptMapper mapper = new DictionaryDatabase(new File("dat/dictionary.pruned"), null, false);
//        PairwisePhraseSimilarity sim = new PairwisePhraseSimilarity(null, helper);
//        sim.similarity("Wal-Mart supply chain goes real time", "Bing");
    }
}
