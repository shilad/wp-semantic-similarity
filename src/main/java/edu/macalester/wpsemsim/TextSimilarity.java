package edu.macalester.wpsemsim;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.util.Version;

import java.io.*;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TextSimilarity {
    private static final Logger LOG = Logger.getLogger(TextSimilarity.class.getName());
    private AtomicInteger counter = new AtomicInteger();
    private DirectoryReader reader;
    private IndexSearcher searcher;
    private BufferedOutputStream output;

    public void openIndex(File indexDir, boolean mmap) throws IOException {
        this.reader = DirectoryReader.open(
                            mmap ? MMapDirectory.open(indexDir)
                                 : FSDirectory.open(indexDir)
                        );
        this.searcher = new IndexSearcher(this.reader);
    }

    public void openOutput(File outputFile) throws FileNotFoundException, CompressorException {
        this.output = new BufferedOutputStream(
                new CompressorStreamFactory()
                .createCompressorOutputStream(CompressorStreamFactory.GZIP,
                        new BufferedOutputStream(new FileOutputStream(outputFile))));

    }

    public void calculatePairwiseSims(final int threads) throws IOException, InterruptedException {
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                final int i2 = i;
                exec.submit(new Runnable() {
                    public void run() {
                    try {
                        calculatePairwiseSims(threads, i2);
                    } catch (IOException e) {
                        LOG.log(Level.SEVERE, "error processing split " + i2, e);
                    }
                    }
                });
            }
        } finally {
            exec.shutdown();
            exec.awaitTermination(60, TimeUnit.HOURS);
        }
    }

    private void calculatePairwiseSims(int mod, int offset) throws IOException {
        // do something with docId here...
        MoreLikeThis mlt = new MoreLikeThis(reader); // Pass the reader reader
        mlt.setMaxDocFreqPct(10);
        mlt.setMaxQueryTerms(100);
        mlt.setAnalyzer(new StandardAnalyzer(Version.LUCENE_40));
        mlt.setFieldNames(new String[] {"text"}); // specify the fields for similiarity

        int simDocIds[] = new int[2000];
        float simDocScores[] = new float[simDocIds.length];
        for (int docId=offset; docId< reader.maxDoc(); docId += mod) {
            Query query = mlt.like(docId); // Pass the doc id
            TopDocs similarDocs = searcher.search(query, simDocIds.length); // Use the searcher
            if (counter.incrementAndGet() % 100 == 0) {
                System.err.println("" + new Date() + ": finding matches for doc " + counter.get());
            }
            if (counter.get() > 5000) {
                break;
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

    public int getWikipediaId(int luceneId) throws IOException {
        Set<String> fields = new HashSet<String>();
        fields.add("id");
        Document d = reader.document(luceneId, fields);
        return d.getField("id").numericValue().intValue();
    }

    public void writeOutput(int targetDocId, int simDocIds[], float simDocScores[]) throws IOException {
        StringBuilder buff = new StringBuilder();
        buff.append(targetDocId);
        DecimalFormat df = new DecimalFormat("#.####");
        for (int i = 0; i < simDocIds.length; i++) {
            if (simDocIds[i] < 0) {
                break;
            }
            buff.append('\t');
            buff.append(simDocIds[i]);
            buff.append('=');
            buff.append(df.format(simDocScores[i]));
        }
        buff.append('\n');
        output.write(buff.toString().getBytes("UTF-8"));
    }

    public static void main(String args[]) throws IOException, InterruptedException, CompressorException {
        TextSimilarity dss = new TextSimilarity();
        dss.openIndex(new File("wp-semantic-similarity/dat/lucene/text"), false);
        int cores = (args.length == 4)
                ? Integer.valueOf(args[3])
                : Runtime.getRuntime().availableProcessors();
        dss.openOutput(new File("wp-semantic-similarity/dat/lucene/text.sims.gz"));
        dss.calculatePairwiseSims(cores);
    }
}
