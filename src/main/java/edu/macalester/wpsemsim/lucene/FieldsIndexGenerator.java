package edu.macalester.wpsemsim.lucene;

import gnu.trove.map.hash.TLongIntHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.util.Bits;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class FieldsIndexGenerator extends BaseIndexGenerator<FieldsIndexGenerator> {
    private static Logger LOG = Logger.getLogger(BaseIndexGenerator.class.getName());
    public static final String FIELD_LINKTEXT = "linktext";
    public static final String FIELD_INLINKS = "inlinks";
    public static final String FIELD_TEXT = "text";
    public static final String FIELD_TITLE = "title";

    // By default only include main namespace
    private int namespaces[] = new int[] { 0 };

    // Include all fields below
    private String fields[];
    private int minLinks;
    private int minWords;
    private int titleMultiplier;
    private boolean addInLinksToText = false;

    private DocBooster booster;
    TLongObjectHashMap<StringBuffer> inLinkText = new TLongObjectHashMap<StringBuffer>();
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

    public FieldsIndexGenerator setBooster(DocBooster booster) {
        this.booster = booster;
        return this;
    }

    public FieldsIndexGenerator setAddInLinksToText(boolean b) {
        this.addInLinksToText = b;
        return this;
    }

    public FieldsIndexGenerator setTitleMultiplier(int multiplier) {
        this.titleMultiplier = multiplier;
        return this;
    }

    public boolean shouldInclude(Page p) {
        if (!ArrayUtils.contains(namespaces, p.getNs())) {
            return false;
        } else if (p.isRedirect() || p.isDisambiguation()) {
            return false;
        } else if (minWords > 0 && p.getNumUniqueWordsInText() < minWords) {
            return false;
        }  else if (minLinks > 0 && new HashSet<String>(p.getAnchorLinks()).size() < minLinks) {
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
        if (minLinks > 0) {
            for (String l : new HashSet<String>(p.getAnchorLinks())) {
                incrInLinks(l);
            }
        }
        Document source = p.toLuceneDoc();
        Document pruned = new Document();

        for (String fieldName : fields) {
            if (fieldName.toLowerCase().equals(FIELD_INLINKS)) {
                // do it later
            } else {
                for (IndexableField f : source.getFields(fieldName)) {
                    pruned.add(f);
                }
            }
        }

        // do we still need to add the linktext?
        if (addInLinksToText && !doField(FIELD_LINKTEXT)) {
            for (IndexableField f : source.getFields(FIELD_LINKTEXT)) {
                pruned.add(f);
            }
        }

        if (titleMultiplier > 0) {
            String text = pruned.get(FIELD_TEXT);
            for (int i = 0; i < titleMultiplier; i++) {
                text += "\n" + source.get(FIELD_TITLE);
            }
            pruned.removeFields(FIELD_TEXT);
            pruned.add(new TextField(FIELD_TEXT, text, Field.Store.YES));
        }

        addDocument(pruned);
    }

    private boolean doField(String field) {
        return ArrayUtils.contains(fields, field);
    }

    @Override
    public void close() throws IOException {
        writer.commit();

        accumulate();
        prune();
        updateDocs();

        super.close();
    }

    private void updateDocs() throws IOException {
        if (!doField(FIELD_INLINKS) && !doField(FIELD_LINKTEXT) && booster != null) {
            return;
        }
        LOG.info("adding inlink counts and text to article text");
        IndexReader reader = DirectoryReader.open(writer, false);
        Bits live = MultiFields.getLiveDocs(reader);
        int n = 0;
        for (int i = 0; i < reader.numDocs(); i++) {
            if (live != null && !live.get(i)) {
                // already deleted
            } else {
                n++;
                Document d = reader.document(i);
                long hash = titleHash(d.get(FIELD_TITLE));
                if (doField(FIELD_LINKTEXT)) {
                    if (inLinkText.containsKey(hash)) {
                        String text = d.get(FIELD_TEXT) + inLinkText.get(hash);
                        d.removeFields(FIELD_TEXT);
                        d.add(new TextField(FIELD_TEXT, text, Field.Store.YES));
                    }
                    if (addInLinksToText && !ArrayUtils.contains(fields, FIELD_LINKTEXT)) {
                        d.removeFields(FIELD_LINKTEXT);
                    }
                }
                if (doField(FieldsIndexGenerator.FIELD_INLINKS)) {
                    int l = getInLinks(hash);
                    d.add(new IntField(FieldsIndexGenerator.FIELD_INLINKS, l, Field.Store.YES));
                }
                if (booster != null) {
                    double boost = booster.getBoost(d);
                    for (String f : booster.getBoostedFields()) {
                        ((Field)d.getField(f)).setBoost((float)boost);
                    }
                }
                writer.updateDocument(new Term("id", d.get("id")), d);
            }
        }
        LOG.info("finished adding inlinks text to " + n + " docs");
        writer.commit();
        reader.close();
    }

    private void prune() throws IOException {
        // the only post hoc pruning we do is minLinks pruning
        if (minLinks == 0) {
            return;
        }

        IndexReader reader = DirectoryReader.open(writer, false);
        LOG.info(getName() + " had " + writer.numDocs() + " docs before inlink pruning");
        Bits live = MultiFields.getLiveDocs(reader);
        for (int i = 0; i < reader.numDocs(); i++) {
            if (live != null && !live.get(i)) {
                continue;
            }
            Document d = reader.document(i);
            if (getInLinks(d.get(FIELD_TITLE)) < minLinks) {
                writer.deleteDocuments(new Term("id", d.get("id")));
            }
        }
        reader.close();
        writer.commit();
        writer.forceMergeDeletes(true);
        LOG.info(getName() + " had " + writer.numDocs() + " docs after inlink pruning");
    }

    private void accumulate() throws IOException {
        if (!addInLinksToText) {
            return; // nothing else to accumulate for now.
        }

        LOG.info("collecting inlinks");
        IndexReader reader = DirectoryReader.open(writer, false);
        Bits live = MultiFields.getLiveDocs(reader);
        for (int i = 0; i < reader.numDocs(); i++) {
            if (live != null && !live.get(i)) {
                continue;
            }
            Document d = reader.document(i);
            IndexableField[] links = d.getFields("links");
            IndexableField[] texts = d.getFields(FIELD_LINKTEXT);
            if (links.length != texts.length) {
                LOG.info("lengths of links and text off by " + (links.length - texts.length));
                continue;
            }
            for (int j = 0; j < links.length; j++) {
                String linkText = links[j].stringValue();
                long hash = titleHash(linkText);
                if (minLinks > 0 && getInLinks(hash) < minLinks) {
                    continue;
                }
                if (!inLinkText.containsKey(hash)) {
                    inLinkText.put(hash, new StringBuffer());
                }
                inLinkText.get(hash).append("\n" + texts[j].stringValue());
            }
        }
        reader.close();
        writer.commit();
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

    private void incrInLinks(String title) {
        long h = titleHash(title);
        synchronized (numInLinks) {
            numInLinks.adjustOrPutValue(h, 1, 1);
        }
    }

    public int getInLinks(String title) {
        long h = titleHash(title);
        synchronized (numInLinks) {
            return numInLinks.containsKey(h) ? numInLinks.get(h) : 0;
        }
    }

    public  int getInLinks(long h) {
        synchronized (numInLinks) {
            return numInLinks.containsKey(h) ? numInLinks.get(h) : 0;
        }
    }
}
