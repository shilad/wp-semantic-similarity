package edu.macalester.wpsemsim.lucene;

import gnu.trove.map.hash.TLongIntHashMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class FieldsIndexGenerator extends BaseIndexGenerator {
    private static Logger LOG = Logger.getLogger(BaseIndexGenerator.class.getName());

    // By default only include main namespace
    private int namespaces[] = new int[] { 0 };

    // Include all fields below
    private String fields[];
    private int minLinks;
    private int minWords;

    private TLongIntHashMap numInLinks = new TLongIntHashMap();

    public FieldsIndexGenerator(String ... fields) {
        super(fields[0]);
        this.fields = fields;
    }

    public FieldsIndexGenerator setMinLinks(int minLinks) {
        this.minLinks = minLinks;
        return this;
    }

    public FieldsIndexGenerator setMinWords(int minWords) {
        this.minWords = minWords;
        return this;
    }

    public boolean shouldInclude(Page p) {
        if (!ArrayUtils.contains(namespaces, p.getNs())) {
            return false;
        } else if (p.isRedirect() || p.isDisambiguation()) {
            return false;
        } else {
            return true;
        }
    }

    public FieldsIndexGenerator setNamespaces(int ...namespaces) {
        this.namespaces = namespaces;
        return this;
    }

    @Override
    public void storePage(Page p) throws IOException {
        if (!shouldInclude(p)) {
            return;
        }
        if (minWords > 0 && p.getNumWordsInText() < minWords) {
            return;
        }
        if (minLinks > 0) {
            List<String> links = p.getAnchorLinksWithoutFragments();
            if (links.size() < minLinks) {
                return;
            }
            for (String l : links) {
                incrInLinks(l);
            }
        }
        Document source = p.toLuceneDoc();
        Document pruned = new Document();

        for (String fieldName : fields) {
            for (IndexableField f : source.getFields(fieldName)) {
                pruned.add(f);
            }
        }
        addDocument(pruned);
    }

    @Override
    public void close() throws IOException {
        writer.commit();
        if (minLinks > 0) {
            LOG.info(getName() + " had " + writer.numDocs() + " docs before inlink pruning");
            int n = 0;
            IndexReader reader = DirectoryReader.open(writer, true);
            for (int i = 0; i < reader.numDocs(); i++) {
                Document d = reader.document(i);
                if (getInLinks(d.get("title")) < minLinks) {
                    writer.deleteDocuments(new Term("id", d.get("id")));
                    n++;
                }
            }
            // force a delete. is there a better way?
            writer.commit();
            DirectoryReader.open(writer, true).close();
        }
        super.close();
    }


    private boolean doStatsPruning() {
        return minLinks > 0 || minWords > 0;
    }

    private static long titleHash(String string) {
        string = string.replaceAll("_", " ").toLowerCase();
        long h = 1125899906842597L; // prime
        int len = string.length();

        for (int i = 0; i < len; i++) {
            h = 31*h + string.charAt(i);
        }
        return h;
    }

    private synchronized void incrInLinks(String title) {
        long h = titleHash(title);
        synchronized (numInLinks) {
            numInLinks.adjustOrPutValue(h, 1, 1);
        }
    }

    public synchronized int getInLinks(String title) {
        long h = titleHash(title);
        synchronized (numInLinks) {
            return numInLinks.containsKey(h) ? numInLinks.get(h) : 0;
        }
    }
}
