package edu.macalester.wpsemsim.sim.esa;

import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.lucene.Page;
import edu.macalester.wpsemsim.sim.BaseSimilarityMetric;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.sim.TextSimilarity;
import edu.macalester.wpsemsim.sim.pairwise.PairwiseSimilarityWriter;
import edu.macalester.wpsemsim.sim.utils.SimUtils;
import edu.macalester.wpsemsim.utils.DocScoreList;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.set.TIntSet;
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
import java.io.StringReader;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ESASimilarity extends BaseSimilarityMetric implements SimilarityMetric {
    private static final Logger LOG = Logger.getLogger(ESASimilarity.class.getName());
    public static final int DEFAULT_MAX_PERCENTAGE = 10;
    public static final int DEFAULT_MAX_QUERY_TERMS = 100;
    public static final int DEFAULT_MIN_TERM_FREQ = 2;
    public static final int DEFAULT_MIN_DOC_FREQ = 2;

    private int maxPercentage = DEFAULT_MAX_PERCENTAGE;
    private int maxQueryTerms = DEFAULT_MAX_QUERY_TERMS;
    private int minTermFreq = DEFAULT_MIN_TERM_FREQ;
    private int minDocFreq = DEFAULT_MIN_DOC_FREQ;
    private IndexSearcher searcher;
    private IndexHelper esaHelper;
    private IndexHelper textHelper;
    private DirectoryReader reader;
    private Analyzer analyzer = new ESAAnalyzer();

    public ESASimilarity(IndexHelper helper) {
        this(null, helper);
    }

    public ESASimilarity(ConceptMapper mapper, IndexHelper helper) {
        super(mapper, helper);
        this.esaHelper = helper;
        this.reader = helper.getReader();
        this.searcher = helper.getSearcher();
        searcher.setSimilarity(new LuceneSimilarity());
        this.setName("esa-similarity");
    }

    public void setTextHelper(IndexHelper textHelper) {
        this.textHelper = textHelper;
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
        TIntDoubleHashMap scores1 = getConceptVector(phrase1, null);
        TIntDoubleHashMap scores2 = getConceptVector(phrase2, null);
        double sim = SimUtils.cosineSimilarity(scores1, scores2);
        //sim = 10 + Math.log(0.0001 + sim);
        return normalize(sim);
    }

    private Map<String, TIntDoubleHashMap> phraseCache = new HashMap<String, TIntDoubleHashMap>();
    public TIntDoubleHashMap getConceptVector(String phrase, TIntSet validIds) throws IOException {
        synchronized (phraseCache) {
            if (phraseCache.containsKey(phrase)) {
                return phraseCache.get(phrase);
            }
        }
        QueryParser parser = new QueryParser(Version.LUCENE_42, "text", analyzer);
        TopDocs docs = null;
        try {
            docs = searcher.search(parser.parse(phrase), esaHelper.getWpIdFilter(validIds), 5000);
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
//        System.out.println("top docs for " + phrase + " are:");
//        for (int i = 0; i < 50 && i < docs.scoreDocs.length; i++) {
//            ScoreDoc sd = docs.scoreDocs[i];
//            Document d = reader.document(sd.doc);
//
//            System.out.println("\t" + sd.score + ": " +
//                    d.get("title") + ", " + d.get("text").split("\\s+").length +
//                    ", " + d.get("inlinks"));
//        }
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
//            LOG.info("pruned results from " + docs.scoreDocs.length + " to " + cutoff);
            docs.scoreDocs = ArrayUtils.subarray(docs.scoreDocs, 0, cutoff);
        }
    }

    private TIntDoubleHashMap expandScores(ScoreDoc scores[]) throws IOException {
        TIntDoubleHashMap expanded = new TIntDoubleHashMap();
        for (ScoreDoc sd : scores) {
            expanded.adjustOrPutValue(sd.doc, sd.score, sd.score);
        }
        return expanded;
    }

    @Override
    public DocScoreList mostSimilar(int wpId, int maxResults, TIntSet validIds) throws IOException {
        if (hasCachedMostSimilar(wpId)) {
            return getCachedMostSimilar(wpId, maxResults, validIds);
        }
        MoreLikeThis mlt = getMoreLikeThis();
        int luceneId = esaHelper.wpIdToLuceneId(wpId);
        Query query;
        if (luceneId >= 0) {
            query = mlt.like(luceneId);
        } else if (textHelper != null && textHelper.wpIdToLuceneId(wpId) >= 0) {
            Document d = textHelper.wpIdToLuceneDoc(wpId);
            String text = d.get(Page.FIELD_TEXT);
            query = mlt.like(new StringReader(text), Page.FIELD_TEXT);
        } else {
            return null;
        }
        TopDocs similarDocs = searcher.search(query, esaHelper.getWpIdFilter(validIds), maxResults);
        pruneSimilar(similarDocs);
        DocScoreList scores = new DocScoreList(similarDocs.scoreDocs.length);
        for (int i = 0; i < similarDocs.scoreDocs.length; i++) {
            ScoreDoc sd = similarDocs.scoreDocs[i];
            scores.set(i,
                    esaHelper.luceneIdToWpId(sd.doc),
                    similarDocs.scoreDocs[i].score);
        }
        return normalize(scores);
    }

    @Override
    public DocScoreList mostSimilar(String phrase, int maxResults, TIntSet validIds) throws IOException {
        final TIntDoubleHashMap scores = getConceptVector(phrase, validIds);
        Integer luceneIds[] = ArrayUtils.toObject(scores.keys());
        Arrays.sort(luceneIds, new Comparator<Integer>() {
            @Override
            public int compare(Integer id1, Integer id2) {
                return -1 * new Double(scores.get(id1)).compareTo(scores.get(id2));
            }
        });
        DocScoreList result = new DocScoreList(Math.min(luceneIds.length, maxResults));
        for (int i = 0; i < result.numDocs(); i++) {
            result.set(i,
                    esaHelper.luceneIdToWpId(luceneIds[i]),
                    scores.get(luceneIds[i]));
        }
        return normalize(result);
    }

    @Override
    public double similarity(int wpId1, int wpId2) throws IOException {
        int doc1 = esaHelper.wpIdToLuceneId(wpId1);
        int doc2 = esaHelper.wpIdToLuceneId(wpId2);

        if (doc1 <  0 || doc2 < 0) {
            return normalize(0.0);
        }

        MoreLikeThis mlt = getMoreLikeThis();
        TopDocs similarDocs = searcher.search(mlt.like(doc1), new FieldCacheTermsFilter("id", "" + wpId2), 1);
        if (similarDocs.scoreDocs.length == 0) {
            return normalize(0);
        } else {
            assert(similarDocs.scoreDocs.length == 1);
            assert(similarDocs.scoreDocs[0].doc == doc2);
            return normalize(similarDocs.scoreDocs[0].score);
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

    public static void main(String args[]) throws IOException, InterruptedException, CompressorException {
        if (args.length != 4 && args.length != 5) {
            System.err.println("usage: java " +
                    TextSimilarity.class.getName() +
                    " field lucene-text-index-dir output-file num-results [num-threads]");

        }
        IndexHelper helper = new IndexHelper(new File(args[1]), true);
        ESASimilarity sim = new ESASimilarity(null, helper);
        if (args[0].equals("links")) {
            sim.setMinTermFreq(1);  // HACK!
        }
        int cores = (args.length == 5)
                ? Integer.valueOf(args[4])
                : Runtime.getRuntime().availableProcessors();
        PairwiseSimilarityWriter writer = new PairwiseSimilarityWriter(sim, new File(args[2]));
        writer.writeSims(helper.getWpIds(), cores, Integer.valueOf(args[3]));
    }
}
