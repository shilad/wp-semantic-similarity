package edu.macalester.wpsemsim;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DocSimScorer {
    private static final Logger LOG = Logger.getLogger(DocSimScorer.class.getName());
    private AtomicInteger counter = new AtomicInteger();

    public void calculatePairwiseSims(File indexDir) throws IOException, InterruptedException {
        final IndexReader reader = DirectoryReader.open(FSDirectory.open(indexDir));
//        final IndexReader reader = DirectoryReader.open(MMapDirectory.open(indexDir));
        final IndexSearcher searcher = new IndexSearcher(reader);

        calculatePairwiseSims(reader, searcher, 1, 0);
                                                    /*
        ExecutorService exec = Executors.newFixedThreadPool(8);
        try {
            for (int i = 0; i < 8; i++) {
                final int j = i;
                exec.submit(new Runnable() {
                    public void run() {
                        try {
                            calculatePairwiseSims(reader, searcher, 8, j);
                        } catch (IOException e) {
                            LOG.log(Level.SEVERE, "pairwise sim thread " + j + " failed:", e);
                        }
                    }
                });
            }
        } catch (Exception e) {
            exec.shutdown();
            exec.awaitTermination(10, TimeUnit.DAYS);
        }
        */
    }

    private void calculatePairwiseSims(IndexReader reader, IndexSearcher searcher, int mod, int offset) throws IOException {
        // do something with docId here...
        MoreLikeThis mlt = new MoreLikeThis(reader); // Pass the index reader
        mlt.setMaxDocFreqPct(10);
        mlt.setMaxQueryTerms(100);
        mlt.setAnalyzer(new StandardAnalyzer(Version.LUCENE_40));
        mlt.setFieldNames(new String[] {"text"}); // specify the fields for similiarity

        for (int docId=offset; docId<reader.maxDoc(); docId += mod) {
            Query query = mlt.like(docId); // Pass the doc id
            TopDocs similarDocs = searcher.search(query, 2000); // Use the searcher
            Set<String> fields = new HashSet<String>();
            fields.add("title");
            if (counter.incrementAndGet() % 100 == 0) {
                System.err.println("" + new Date() + ": finding matches for doc " + counter.get());
            }
//            for (int j = 0; j < similarDocs.scoreDocs.length && j < 10; j++) {
//                int id2 = similarDocs.scoreDocs[j].doc;
//                Document d = reader.document(id2, fields);
//                System.out.println("   " + j + ". " + d.getField("title"));
//            }
        }
    }

    public void createIndex(final File inputDir, File outputDir) throws IOException, InterruptedException {

        Directory dir = FSDirectory.open(outputDir);
        Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_40);
        IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_40, analyzer);
        iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        iwc.setRAMBufferSizeMB(5000.0);

        final IndexWriter writer = new IndexWriter(dir, iwc);
        final AtomicInteger numDocs = new AtomicInteger();
        ExecutorService exec = Executors.newFixedThreadPool(8);

        try {
            for (final String path : inputDir.list()) {
                if (!path.endsWith(".bz2")) {
                    continue;
                }
                exec.submit(new Runnable() {
                    @Override
                    public void run() {
                        File f = new File(inputDir, path);
                        try {
                            LOG.info("reading input file " + f);
                            for (Page d : new PageReader(f)) {
                                if (d.isRedirect()) {
                                    continue;
                                }
                                writer.addDocument(d.toLuceneDoc());
                                if (numDocs.incrementAndGet() % 1000 == 0) {
                                    LOG.info("read doc " + numDocs + " from " + f + ": " + d.getTitle());
                                }
                            }
                        } catch (Exception e) {
                            LOG.log(Level.SEVERE, "error processing " + f, e);
                        }
                    }
                });
            }
        } finally {
            exec.shutdown();
            exec.awaitTermination(60, TimeUnit.HOURS);
        }

        writer.close();
    }

    public static void main(String args[]) throws IOException, InterruptedException {
        DocSimScorer dss = new DocSimScorer();
//        dss.createIndex(new File("/Users/shilad/Documents/IntelliJ/NbrViz/Macademia/dat/wikipedia"), new File("lucene-index"));
        dss.calculatePairwiseSims(new File("lucene-index"));
    }
}
