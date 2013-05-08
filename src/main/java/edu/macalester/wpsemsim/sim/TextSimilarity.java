package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.sim.pairwise.PairwiseSimilarityWriter;
import edu.macalester.wpsemsim.utils.DocScoreList;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.set.TIntSet;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.surround.parser.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TextSimilarity extends BaseSimilarityMetric implements SimilarityMetric {
    private static final Logger LOG = Logger.getLogger(TextSimilarity.class.getName());
    public static final int DEFAULT_MAX_PERCENTAGE = 10;
    public static final int DEFAULT_MAX_QUERY_TERMS = 100;
    public static final int DEFAULT_MIN_TERM_FREQ = 2;
    public static final int DEFAULT_MIN_DOC_FREQ = 2;

    private String field;
    private int maxPercentage = DEFAULT_MAX_PERCENTAGE;
    private int maxQueryTerms = DEFAULT_MAX_QUERY_TERMS;
    private int minTermFreq = DEFAULT_MIN_TERM_FREQ;
    private int minDocFreq = DEFAULT_MIN_DOC_FREQ;
    private IndexSearcher searcher;
    private IndexHelper helper;
    private DirectoryReader reader;
    private boolean useInternalMapper = false;

    public TextSimilarity(IndexHelper helper, String field) {
        this(null, helper, field);
    }

    public TextSimilarity(ConceptMapper mapper, IndexHelper helper, String field) {
        super(mapper, helper);
        this.helper = helper;
        this.reader = helper.getReader();
        this.searcher = helper.getSearcher();
        this.field = field;
        this.setName("text-similarity (field=" + field + ")");
    }

    private MoreLikeThis getMoreLikeThis() {
        MoreLikeThis mlt = new MoreLikeThis(reader); // Pass the reader reader
        mlt.setMaxDocFreqPct(maxPercentage);
        mlt.setMaxQueryTerms(maxQueryTerms);
        mlt.setMinDocFreq(minDocFreq);
        mlt.setMinTermFreq(minTermFreq);
        mlt.setAnalyzer(new StandardAnalyzer(Version.LUCENE_42));
        mlt.setFieldNames(new String[]{field}); // specify the fields for similiarity
        return mlt;
    }

    @Override
    public double similarity(String phrase1, String phrase2) throws IOException, ParseException {
        if (!useInternalMapper) {
            return super.similarity(phrase1, phrase2);
        }
        Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_42);
        QueryParser parser = new QueryParser(Version.LUCENE_42, field, analyzer);

        TopDocs similarDocs1 = null;
        TopDocs similarDocs2 = null;

        try {
            similarDocs1 = searcher.search(parser.parse(phrase1), 100000);
            similarDocs2 = searcher.search(parser.parse(phrase2), 100000);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "parsing of phrase " + phrase1 + " failed", e);
            return Double.NaN;
        } catch (org.apache.lucene.queryparser.classic.ParseException e) {
            LOG.log(Level.WARNING, "parsing of phrase " + phrase1 + " failed", e);
            return Double.NaN;
        }

//        System.out.println("top docs for " + phrase1 + " are:");
//        for (int i = 0; i < 10; i++) {
//            System.out.println("\t" + similarDocs1.scoreDocs[i].score + ": " +
//                        helper.luceneIdToTitle(similarDocs1.scoreDocs[i].doc));
//        }
//        System.out.println("top docs for " + phrase2 + " are:");
//        for (int i = 0; i < 10; i++) {
//            System.out.println("\t" + similarDocs2.scoreDocs[i].score + ": " +
//                    helper.luceneIdToTitle(similarDocs2.scoreDocs[i].doc));
//        }

        double xDotX = 0.0;
        double yDotY = 0.0;
        double xDotY = 0.0;

        TIntDoubleHashMap scores1 = new TIntDoubleHashMap();
        for (int i = 0; i < similarDocs1.scoreDocs.length; i++) {
            int id = similarDocs1.scoreDocs[i].doc;
            double x = similarDocs1.scoreDocs[i].score;
            scores1.put(id, x);
            xDotX += x * x;
        }
        for (int i = 0; i < similarDocs2.scoreDocs.length; i++) {
            int id = similarDocs2.scoreDocs[i].doc;
            double y= similarDocs2.scoreDocs[i].score;
            yDotY += y * y;
            if (scores1.containsKey(id)) {
                xDotY += scores1.get(id) * y;
            }
        }

        if (yDotY == 0.0) {
            return Double.NaN;
        } else {
            return normalize(xDotY / Math.sqrt(xDotX * yDotY));
        }
    }

    @Override
    public DocScoreList mostSimilar(int wpId, int maxResults, TIntSet validIds) throws IOException {
        if (hasCachedMostSimilar(wpId)) {
            return getCachedMostSimilar(wpId, maxResults, validIds);
        }
        MoreLikeThis mlt = getMoreLikeThis();
        int luceneId = helper.wpIdToLuceneId(wpId);
        if (luceneId < 0) {
            return null;
        }
        TopDocs similarDocs = searcher.search(mlt.like(luceneId),
                helper.getWpIdFilter(validIds), maxResults);
        DocScoreList scores = new DocScoreList(similarDocs.scoreDocs.length);
        for (int i = 0; i < similarDocs.scoreDocs.length; i++) {
            ScoreDoc sd = similarDocs.scoreDocs[i];
            scores.set(i,
                    helper.luceneIdToWpId(sd.doc),
                    similarDocs.scoreDocs[i].score);
        }
//        System.err.println(
//                "wpId=" + wpId +
//                " links=" + reader.document(luceneId).get(field) +
//                " luceneId=" + luceneId +
//                " maxResults=" + maxResults +
//                " results=" + similarDocs.scoreDocs.length);
        return normalize(scores);
    }

    @Override
    public double similarity(int wpId1, int wpId2) throws IOException {
        int doc1 = helper.wpIdToLuceneId(wpId1);
        int doc2 = helper.wpIdToLuceneId(wpId2);
        if (doc1 < 0 || doc2 < 0) {
            System.out.println("missing docs!");
            return Double.NaN;
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

    public void setUseInternalMapper(boolean useInternalMapper) {
        this.useInternalMapper = useInternalMapper;
    }

    public static void main(String args[]) throws IOException, InterruptedException, CompressorException {
        if (args.length != 4 && args.length != 5) {
            System.err.println("usage: java " +
                    TextSimilarity.class.getName() +
                    " field lucene-text-index-dir output-file num-results [num-threads]");

        }
        IndexHelper helper = new IndexHelper(new File(args[1]), true);
        TextSimilarity sim = new TextSimilarity(helper, args[0]);
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
