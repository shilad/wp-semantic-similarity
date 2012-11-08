package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.utils.DocScoreList;
import gnu.trove.map.hash.TIntDoubleHashMap;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.surround.parser.ParseException;
import org.apache.lucene.search.FieldCacheTermsFilter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.IOException;
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
    private IndexHelper textHelper;
    private IndexHelper linkHelper;
    private DirectoryReader reader;

    public ESASimilarity(IndexHelper textHelper, IndexHelper linkHelper) {
        this(null, textHelper, linkHelper);
    }

    public ESASimilarity(ConceptMapper mapper, IndexHelper textHelper, IndexHelper linkHelper) {
        super(mapper, textHelper);
        this.textHelper = textHelper;
        this.linkHelper = linkHelper;
        this.reader = textHelper.getReader();
        this.searcher = textHelper.getSearcher();
        this.setName("esa-similarity");
    }

    private MoreLikeThis getMoreLikeThis() {
        MoreLikeThis mlt = new MoreLikeThis(reader); // Pass the reader reader
        mlt.setMaxDocFreqPct(maxPercentage);
        mlt.setMaxQueryTerms(maxQueryTerms);
        mlt.setMinDocFreq(minDocFreq);
        mlt.setMinTermFreq(minTermFreq);
        mlt.setAnalyzer(new StandardAnalyzer(Version.LUCENE_40));
        mlt.setFieldNames(new String[]{"text"}); // specify the fields for similiarity
        return mlt;
    }

    @Override
    public double similarity(String phrase1, String phrase2) throws IOException, ParseException {
        Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_40);
        QueryParser parser = new QueryParser(Version.LUCENE_40, "text", analyzer);

        TopDocs similarDocs1 = null;
        TopDocs similarDocs2 = null;

        try {
            similarDocs1 = searcher.search(parser.parse(phrase1), 200);
            similarDocs2 = searcher.search(parser.parse(phrase2), 200);
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
//                        textHelper.luceneIdToTitle(similarDocs1.scoreDocs[i].doc));
//        }
//        System.out.println("top docs for " + phrase2 + " are:");
//        for (int i = 0; i < 10; i++) {
//            System.out.println("\t" + similarDocs2.scoreDocs[i].score + ": " +
//                    textHelper.luceneIdToTitle(similarDocs2.scoreDocs[i].doc));
//        }

        double xDotX = 0.0;
        double yDotY = 0.0;
        double xDotY = 0.0;

        TIntDoubleHashMap scores1 = expandScores(similarDocs1.scoreDocs);
        TIntDoubleHashMap scores2 = expandScores(similarDocs2.scoreDocs);

        for (int id : scores1.keys()) {
            double x = scores1.get(id);
            xDotX += x * x;
            if (scores2.containsKey(id)) {
                xDotY += x * scores2.get(id);
            }
        }
        for (int id : scores2.keys()) {
            double y = scores2.get(id);
            yDotY += y * y;
        }

        if (yDotY == 0.0) {
            return Double.NaN;
        } else {
            return xDotY / Math.sqrt(xDotX * yDotY);
        }
    }

    private TIntDoubleHashMap expandScores(ScoreDoc scores[]) throws IOException {
        TIntDoubleHashMap expanded = new TIntDoubleHashMap();
        double alpha = 0.5;
        for (ScoreDoc sd : scores) {
            expanded.adjustOrPutValue(sd.doc, sd.score, sd.score);
            for (int luceneId : linkHelper.getLinkedLuceneIds(sd.doc).toArray()) {
                expanded.adjustOrPutValue(luceneId, alpha*sd.score, alpha*sd.score);
            }
        }
        return expanded;
    }

    @Override
    public DocScoreList mostSimilar(int wpId, int maxResults) throws IOException {
        MoreLikeThis mlt = getMoreLikeThis();
        int luceneId = textHelper.wpIdToLuceneId(wpId);
        TopDocs similarDocs = searcher.search(mlt.like(luceneId), maxResults);
        DocScoreList scores = new DocScoreList(similarDocs.scoreDocs.length);
        for (int i = 0; i < similarDocs.scoreDocs.length; i++) {
            ScoreDoc sd = similarDocs.scoreDocs[i];
            scores.set(i,
                    textHelper.luceneIdToWpId(sd.doc),
                    similarDocs.scoreDocs[i].score);
        }
//        System.err.println(
//                "wpId=" + wpId +
//                " links=" + reader.document(luceneId).get(field) +
//                " luceneId=" + luceneId +
//                " maxResults=" + maxResults +
//                " results=" + similarDocs.scoreDocs.length);
        return scores;
    }

    @Override
    public double similarity(int wpId1, int wpId2) throws IOException {
        int doc1 = textHelper.wpIdToLuceneId(wpId1);
        int doc2 = textHelper.wpIdToLuceneId(wpId2);

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

    public static void main(String args[]) throws IOException, InterruptedException, CompressorException {
        if (args.length != 4 && args.length != 5) {
            System.err.println("usage: java " +
                    ESASimilarity.class.getName() +
                    " lucene-text-index-dir lucene-link-index-dir output-file num-results [num-threads]");

        }
        IndexHelper helper = new IndexHelper(new File(args[0]), true);
        IndexHelper linkHelper = new IndexHelper(new File(args[1]), true);
        ESASimilarity sim = new ESASimilarity(helper, linkHelper);
        int cores = (args.length == 5)
                ? Integer.valueOf(args[4])
                : Runtime.getRuntime().availableProcessors();
        PairwiseSimilarityWriter writer = new PairwiseSimilarityWriter(sim, new File(args[2]));
        writer.writeSims(helper.getWpIds(), cores, Integer.valueOf(args[3]));
    }
}
