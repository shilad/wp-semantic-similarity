package edu.macalester.wpsemsim.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import sun.util.LocaleServiceProviderPool;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public abstract class BaseIndexGenerator {
    private Logger LOG = Logger.getLogger(BaseIndexGenerator.class.getName());

    protected AtomicInteger numDocs = new AtomicInteger();
    protected IndexWriter writer;
    protected Directory dir;
    protected String name;

    public BaseIndexGenerator(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void openIndex(File indexDir, int bufferMB) throws IOException {
        indexDir.mkdirs();
        this.dir = FSDirectory.open(indexDir);
        Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_40);
        IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_40, analyzer);
        iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        iwc.setRAMBufferSizeMB(bufferMB);
        this.writer = new IndexWriter(dir, iwc);
    }

    public abstract void storePage(Page p) throws IOException;

    public void close() throws IOException {
        LOG.info(getName() + " wrote " + numDocs.get() + " documents");
        writer.commit();
        this.writer.close();
    }

    public BaseIndexGenerator setName(String name) {
        this.name = name;
        return this;
    }

    protected void addDocument(Document d) throws IOException {
        numDocs.incrementAndGet();
        this.writer.addDocument(d);
    }

    public IndexWriter getWriter() {
        return writer;
    }

    public Directory getDir() {
        return dir;
    }
}
