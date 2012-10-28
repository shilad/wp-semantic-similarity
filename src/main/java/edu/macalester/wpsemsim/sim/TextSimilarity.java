package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.utils.DocScoreList;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.search.*;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class TextSimilarity implements SimilarityMetric {
    private static final Logger LOG = Logger.getLogger(TextSimilarity.class.getName());
    public static final int DEFAULT_MAX_PERCENTAGE = 10;
    public static final int DEFAULT_MAX_QUERY_TERMS = 100;

    private AtomicInteger counter = new AtomicInteger();
    private String field;
    private int maxPercentage = DEFAULT_MAX_PERCENTAGE;
    private int maxQueryTerms = DEFAULT_MAX_QUERY_TERMS;
    private IndexSearcher searcher;
    private IndexHelper helper;
    private DirectoryReader reader;

    public TextSimilarity(IndexHelper helper, String field) {
        this.helper = helper;
        this.reader = helper.getReader();
        this.searcher = helper.getSearcher();
        this.field = field;
    }

    private MoreLikeThis getMoreLikeThis() {
        MoreLikeThis mlt = new MoreLikeThis(reader); // Pass the reader reader
        mlt.setMaxDocFreqPct(maxPercentage);
        mlt.setMaxQueryTerms(maxQueryTerms);
        mlt.setAnalyzer(new StandardAnalyzer(Version.LUCENE_40));
        mlt.setFieldNames(new String[]{field}); // specify the fields for similiarity
        return mlt;
    }

    @Override
    public DocScoreList mostSimilar(int wpId, int maxResults) throws IOException {
        MoreLikeThis mlt = getMoreLikeThis();
        int luceneId = helper.wpIdToLuceneId(wpId);
        Query query = mlt.like(luceneId);
        TopDocs similarDocs = searcher.search(query, maxResults);
        DocScoreList scores = new DocScoreList(similarDocs.scoreDocs.length);
        for (int j = 0; j < similarDocs.scoreDocs.length; j++) {
            ScoreDoc sd = similarDocs.scoreDocs[j];
            scores.set(j,
                    helper.luceneIdToWpId(sd.doc),
                    similarDocs.scoreDocs[j].score);
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

    public static void main(String args[]) throws IOException, InterruptedException, CompressorException {
        if (args.length != 4 && args.length != 5) {
            System.err.println("usage: java " +
                    TextSimilarity.class.getName() +
                    " field lucene-text-index-dir output-file num-results [num-threads]");

        }
        IndexHelper helper = new IndexHelper(new File(args[1]), true);
        TextSimilarity sim = new TextSimilarity(helper, args[0]);
        int cores = (args.length == 5)
                ? Integer.valueOf(args[4])
                : Runtime.getRuntime().availableProcessors();
        PairwiseSimilarityWriter writer = new PairwiseSimilarityWriter(sim, new File(args[2]));
        writer.writeSims(helper.getWpIds(), cores, Integer.valueOf(args[3]));
    }
}
