package edu.macalester.wpsemsim;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.Version;

import java.io.*;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class TextSimilarity extends SimilarityMetric {
    private static final Logger LOG = Logger.getLogger(TextSimilarity.class.getName());
    private AtomicInteger counter = new AtomicInteger();

    protected void calculatePairwiseSims(int mod, int offset, int maxSimsPerDoc) throws IOException {
        // do something with docId here...
        MoreLikeThis mlt = new MoreLikeThis(reader); // Pass the reader reader
        mlt.setMaxDocFreqPct(10);
        mlt.setMaxQueryTerms(100);
        mlt.setAnalyzer(new StandardAnalyzer(Version.LUCENE_40));
        mlt.setFieldNames(new String[] {"text"}); // specify the fields for similiarity

        int simDocIds[] = new int[maxSimsPerDoc];
        float simDocScores[] = new float[maxSimsPerDoc];
        for (int docId=offset; docId< reader.maxDoc(); docId += mod) {
            Query query = mlt.like(docId);
            TopDocs similarDocs = searcher.search(query, maxSimsPerDoc);
            if (counter.incrementAndGet() % 100 == 0) {
                System.err.println("" + new Date() + ": finding matches for doc " + counter.get());
            }
            Arrays.fill(simDocIds, -1);
            Arrays.fill(simDocScores, -1.0f);
            for (int j = 0; j < similarDocs.scoreDocs.length; j++) {
                ScoreDoc sd = similarDocs.scoreDocs[j];
                simDocIds[j] = getWikipediaId(sd.doc);
                simDocScores[j] = similarDocs.scoreDocs[j].score;
            }
            writeOutput(getWikipediaId(docId), simDocIds, simDocScores);
        }
    }

    public static void main(String args[]) throws IOException, InterruptedException, CompressorException {
        if (args.length != 3 && args.length != 4) {
            System.err.println("usage: java " +
                    TextSimilarity.class.getName() +
                    " lucene-text-index-dir output-file num-results [num-threads]");

        }
        SimilarityMetric dss = new TextSimilarity();
        dss.openIndex(new File(args[0]), true);
        int cores = (args.length == 4)
                ? Integer.valueOf(args[3])
                : Runtime.getRuntime().availableProcessors();
        dss.openOutput(new File(args[1]));
        dss.calculatePairwiseSims(cores, Integer.valueOf(args[2]));
    }
}
