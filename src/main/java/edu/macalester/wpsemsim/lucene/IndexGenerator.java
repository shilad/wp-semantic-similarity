package edu.macalester.wpsemsim.lucene;

import edu.macalester.wpsemsim.utils.TitleMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Generator for a single Lucene index.
 *
 * Lifecycle:
 * <ol>
 *     <li>Create an index generator, possibly via a configuration file.</li>
 *     <li>open() the index.</li>
 *     <li>Add pages to the index using storePage().</li>
 * </ol>
 * Closing an index can be very slow, because it requires up to three passes over the index
 * to accumulate information, prune the information, and finalize the documents.
 *
 * @see AllIndexBuilder
 */
public class IndexGenerator {
    private static Logger LOG = Logger.getLogger(IndexGenerator.class.getName());

    /**
     * A name used to identify the index.
     */
    private String name;

    /**
     * Namespaces that should be included in the index.
     *  By default only include main namespace
     */
    private int namespaces[] = new int[] { 0 };

    /**
     * Fields that should be indexed.
     */
    private String fields[];

    /**
     * The minimum number of in or outlinks required for a page to
     * appear in the index.  Can be used to filter out unpopular pages.
     */
    private int minLinks = 0;

    /**
     * The minimum number of <b>unique</b> words that are required for a
     * page to appear in the index. Useful for removing stubs.
     */
    private int minWords = 0;

    /**
     * Append this many instances of the title of the article to the text
     * field of a page. Useful for boosting the likelihood of finding a
     * page based on its title.
     */
    private int titleMultiplier = 0;

    /**
     * Whether the index should contain disambiguation pages, redirects, and lists.
     */
    private boolean skipDabs = true;
    private boolean skipRedirects = true;
    private boolean skipLists = true;

    /**
     * If true, add the text displayed in inlinks to page text.
     */
    private boolean addInLinksToText = false;

    /**
     * Accumulated text of inbound links.
     */
    TitleMap<StringBuffer> inLinkText = new TitleMap<StringBuffer>(StringBuffer.class);

    /**
     * The number of documents that have already been counted.
     */
    protected AtomicInteger numDocs = new AtomicInteger();

    /**
     * Lucene components associated with the index.
     */
    protected IndexWriter writer;
    protected Directory dir;
    protected Similarity similarity;
    private Analyzer analyzer;
    protected File indexDir;
    private DocBooster booster;

    /**
     * Information shared across several index generators.
     */
    protected PageInfo info;



    public IndexGenerator(PageInfo info, String... fields) {
        this.info = info;
        this.name = fields[0];
        this.fields = fields;
        if (doField(Page.FIELD_REDIRECT)) {
            skipRedirects = false;
        }
        if (doField(Page.FIELD_DAB)) {
            skipDabs = false;
        }
    }

    public IndexGenerator setMinLinks(int minLinks) {
        this.minLinks = minLinks;
        return this;
    }

    public IndexGenerator setMinWords(int minWords) {
        this.minWords = minWords;
        return this;
    }

    public IndexGenerator setBooster(DocBooster booster) {
        this.booster = booster;
        return this;
    }

    public IndexGenerator setAddInLinksToText(boolean b) {
        this.addInLinksToText = b;
        return this;
    }

    public IndexGenerator setTitleMultiplier(int multiplier) {
        this.titleMultiplier = multiplier;
        return this;
    }


    public String getName() {
        return name;
    }

    public IndexGenerator setSimilarity(Similarity sim) {
        this.similarity = sim;
        return this;
    }

    public IndexGenerator setAnalyzer(Analyzer analyzer) {
        this.analyzer = analyzer;
        return this;
    }

    public boolean shouldInclude(Page p) {
        if (!ArrayUtils.contains(namespaces, p.getNs())) {
            return false;
        } else if (skipLists && p.isList()) {
            return false;
        } else if (skipRedirects && p.isRedirect()) {
            return false;
        } else if (skipDabs && p.isDisambiguation()) {
            return false;
        } else if (minWords > 0 && p.getNumUniqueWordsInText() < minWords) {
            return false;
        }  else if (minLinks > 0 && new HashSet<String>(p.getAnchorLinks()).size() < minLinks) {
            return false;
        } else {
            return true;
        }
    }

    public IndexGenerator setNamespaces(int ...namespaces) {
        this.namespaces = namespaces;
        return this;
    }


    /**
     * Opens an index for writing with a specific number of MBs of memory.
     * @param indexDir
     * @param bufferMB
     * @throws IOException
     */
    public void open(File indexDir, int bufferMB) throws IOException {
        this.indexDir = indexDir;
        FileUtils.deleteDirectory(indexDir);
        indexDir.mkdirs();
        this.dir = FSDirectory.open(indexDir);
        Analyzer analyzer = (this.analyzer == null) ? new StandardAnalyzer(Version.LUCENE_40) : this.analyzer;
        IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_40, analyzer);
        iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        iwc.setRAMBufferSizeMB(bufferMB);
        if (this.similarity != null) {
            iwc.setSimilarity(similarity);
        }
        this.writer = new IndexWriter(dir, iwc);
    }

    /**
     * Stores a page in the index if it should be stored.
     * @param p
     * @throws IOException
     */
    public void storePage(Page p) throws IOException {
        if (!shouldInclude(p)) {
            return;
        }
        Document source = p.toLuceneDoc();
        Document pruned = new Document();  // only contains relevant fields

        for (String fieldName : fields) {
            for (IndexableField f : source.getFields(fieldName)) {
                pruned.add(f);
            }
        }

        // add linktext to outbound pages.
        if (addInLinksToText && !doField(Page.FIELD_LINKTEXT)) {
            for (IndexableField f : source.getFields(Page.FIELD_LINKTEXT)) {
                pruned.add(f);
            }
        }

        // add copies of the title to the text
        if (titleMultiplier > 0) {
            String text = pruned.get(Page.FIELD_TEXT);
            for (int i = 0; i < titleMultiplier; i++) {
                text += "\n" + source.get(Page.FIELD_TITLE);
            }
            pruned.removeFields(Page.FIELD_TEXT);
            pruned.add(new TextField(Page.FIELD_TEXT, text, Field.Store.YES));
        }

        storeLuceneDoc(pruned);
    }

    /**
     * Returns true if the index should contain the specified field.
     * @param field
     * @return
     */
    public boolean doField(String field) {
        return ArrayUtils.contains(fields, field);
    }

    /**
     * Prune and finalize all documents, then close the index.
     * @throws IOException
     */
    public void close() throws IOException {
        writer.commit();

        accumulate();
        prune();
        updateDocs();

        LOG.info(getName() + " wrote " + writer.numDocs() + " documents");
        writer.commit();
        writer.forceMergeDeletes();
        this.writer.close();
    }


    /**
     * First pass in close:
     * Accumulates information that will be used in updating and pruning documents.
     * @throws IOException
     */
    private void accumulate() throws IOException {
        if (!(addInLinksToText || doField(Page.FIELD_INLINKS) || doField(Page.FIELD_LINKS))) {
            return; // nothing else to accumulate for now.
        }

        LOG.info("accumulating document info");
        IndexReader reader = DirectoryReader.open(writer, false);
        Bits live = MultiFields.getLiveDocs(reader);
        for (int i = 0; i < reader.numDocs(); i++) {
            if (live != null && !live.get(i)) {
                continue;
            }
            Document d = reader.document(i);
            int wpId = Integer.valueOf(d.get("id"));
            IndexableField[] links = d.getFields(Page.FIELD_LINKS);
            IndexableField[] texts = d.getFields(Page.FIELD_LINKTEXT);
            if ((addInLinksToText || doField(Page.FIELD_LINKTEXT)) && (links.length != texts.length)) {
                LOG.info("lengths of links and text off by " + (links.length - texts.length));
                continue;
            }
            for (int j = 0; j < links.length; j++) {
                String link = links[j].stringValue();
                if (minLinks > 0 && info.getInLinks(link).size() < minLinks) {
                    continue;
                }
                if (addInLinksToText) {
                    inLinkText.get(link).append("\n" + texts[j].stringValue());
                }
            }
        }
        reader.close();
        writer.commit();
    }

    /**
     * Second stage of closing an index.
     * Prunes documents that don't meet necessary criteria. Right now the
     * only criterion is that the documents has at least minlinks inbound links.
     * Outbound links would have already been checked in shouldInclude()
     *
     * @throws IOException
     */
    private void prune() throws IOException {
        // the only post hoc pruning we do is minLinks pruning
        if (minLinks == 0) {
            return;
        }
        IndexReader reader = DirectoryReader.open(writer, false);
        LOG.info(getName() + " had " + writer.numDocs() + " docs before pruning");
        Bits live = MultiFields.getLiveDocs(reader);
        for (int i = 0; i < reader.numDocs(); i++) {
            if (live != null && !live.get(i)) {
                continue;
            }
            Document d = reader.document(i);
            int wpId = Integer.valueOf(d.get("id"));
            if (info.getInLinks(d.get(Page.FIELD_TITLE)).size() < minLinks) {
                writer.deleteDocuments(new Term("id", ""+wpId));
            }
        }
        reader.close();
        writer.commit();
        writer.forceMergeDeletes(true);
        LOG.info(getName() + " had " + writer.numDocs() + " docs after pruning");
    }

    /**
     * Pass three:
     * Adds accumulated information to the lucene index for each document.
     * @throws IOException
     */
    private void updateDocs() throws IOException {
        if (!doField(Page.FIELD_NINLINKS)
        &&  !doField(Page.FIELD_INLINKS)
        &&  !doField(Page.FIELD_LINKTEXT)
        &&  !doField(Page.FIELD_LINKS)
        &&  booster == null) {
            return;
        }
        LOG.info("adding inlink counts and text to article text");
        IndexReader reader = DirectoryReader.open(writer, false);
        Bits live = MultiFields.getLiveDocs(reader);
        int n = 0;
        for (int i = 0; i < reader.numDocs(); i++) {
            if (live != null && !live.get(i)) {
                continue;
            }
            n++;
            Document d = reader.document(i);
            String title = d.get(Page.FIELD_TITLE);
            if (doField(Page.FIELD_LINKTEXT)) {
                if (inLinkText.containsKey(title)) {
                    String text = d.get(Page.FIELD_TEXT) + inLinkText.get(title);
                    d.removeFields(Page.FIELD_TEXT);
                    d.add(new TextField(Page.FIELD_TEXT, text, Field.Store.YES));
                }
                if (addInLinksToText && !ArrayUtils.contains(fields, Page.FIELD_LINKTEXT)) {
                    d.removeFields(Page.FIELD_LINKTEXT);
                }
            }
            if (doField(Page.FIELD_NINLINKS)) {
                int l = info.getInLinks(title).size();
                d.add(new IntField(Page.FIELD_NINLINKS, l, Field.Store.YES));
            }
            if (doField(Page.FIELD_INLINKS)) {
                for (int wpId : info.getInLinks(title).toArray()) {
                    d.add(new NormedStringField(Page.FIELD_INLINKS, ""+wpId, Field.Store.YES));
                }
            }
            if (doField(Page.FIELD_LINKS)) {
                IndexableField links[] = d.getFields(Page.FIELD_LINKS);
                d.removeFields(Page.FIELD_LINKS);
                for (IndexableField l : links) {
                    int wpId = info.getPageId(l.stringValue());
                    if (wpId > 0) {
                        d.add(new NormedStringField(Page.FIELD_LINKS, ""+wpId, Field.Store.YES));
                    }
                }
            }
            if (booster != null) {
                double boost = booster.getBoost(d);
                for (String sf : booster.getBoostedFields()) {
                    Field f = (Field) d.getField(sf);
                    if (f != null) {
                        f.setBoost((float)boost);
                    }
                }
            }
            writer.updateDocument(new Term("id", d.get("id")), Page.correctMetadata(d));
        }
        LOG.info("finished updating fields in " + n + " docs");
        writer.commit();
        reader.close();
    }
    public IndexGenerator setName(String name) {
        this.name = name;
        return this;
    }

    protected void storeLuceneDoc(Document d) throws IOException {
        numDocs.incrementAndGet();
        this.writer.addDocument(d);
    }

    public IndexWriter getWriter() {
        return writer;
    }

    public Directory getDir() {
        return dir;
    }

    public void setSkipLists(boolean skipLists) {
        this.skipLists = skipLists;
    }

    public void setSkipDabs(boolean skipDabs) {
        this.skipDabs = skipDabs;
    }

    public void setSkipRedirects(boolean skipRedirects) {
        this.skipRedirects = skipRedirects;
    }

    /**
     * TODO: shift to this interface.
     */
    interface Generator {
        boolean prePruneAccumulateNeeded();
        boolean postPruneAccumulateNeeded();
        boolean pruneNeeded();
        boolean updateNeeded();

        // returning false indicates document should be skipped / deleted
        boolean process(Document d);
        boolean prePruneAccumulate(Document d);
        boolean prune();

        void postPruneAccumulate(Document d);
        void update(Document d);
    }
}
