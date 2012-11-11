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

public class FieldsIndexGenerator extends BaseIndexGenerator {
    private static Logger LOG = Logger.getLogger(BaseIndexGenerator.class.getName());

    // By default only include main namespace
    private int namespaces[] = new int[] { 0 };

    // Include all fields below
    private String fields[];
    private int minLinks;
    private int minWords;
    private int titleMultiplier;
    private boolean addInLinksToText;

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

    public FieldsIndexGenerator setTitleMultiplier(int multiplier) {
        this.titleMultiplier = multiplier;
        return this;
    }

    public FieldsIndexGenerator setAddInLinksToText(boolean add) {
        this.addInLinksToText = add;
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
            if (fieldName.toLowerCase().equals("inlinks")) {
                // do it later
            } else {
                for (IndexableField f : source.getFields(fieldName)) {
                    pruned.add(f);
                }
            }
        }

        if (addInLinksToText) {
            for (String l : p.getTextOfAnchors()) {
                pruned.add(new StringField("linktext", l, Field.Store.YES));
            }
        }
        if (titleMultiplier > 0) {
            String text = pruned.get("text");
            for (int i = 0; i < titleMultiplier; i++) {
                text += "\n" + source.get("title");
            }
            pruned.removeFields("text");
            pruned.add(new TextField("text", text, Field.Store.YES));
        }

        addDocument(pruned);
    }

    @Override
    public void close() throws IOException {
        writer.commit();

        TLongObjectHashMap<StringBuffer> inLinkText = new TLongObjectHashMap<StringBuffer>();
        if (addInLinksToText) {
            LOG.info("collecting inlinks");
            IndexReader reader = DirectoryReader.open(writer, false);
            Bits live = MultiFields.getLiveDocs(reader);
            for (int i = 0; i < reader.numDocs(); i++) {
                if (live != null && !live.get(i)) {
                    continue;
                }
                Document d = reader.document(i);
                IndexableField[] links = d.getFields("links");
                IndexableField[] texts = d.getFields("linktext");
                if (links.length != texts.length) {
                    LOG.info("lengths of links and text off by " + (links.length - texts.length));
                    continue;
                }
                for (int j = 0; j < links.length; j++) {
                    long hash = titleHash(links[j].stringValue());
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

        if (minLinks > 0) {
            IndexReader reader = DirectoryReader.open(writer, false);
            LOG.info(getName() + " had " + writer.numDocs() + " docs before inlink pruning");
            Bits live = MultiFields.getLiveDocs(reader);
            for (int i = 0; i < reader.numDocs(); i++) {
                if (live != null && !live.get(i)) {
                    continue;
                }
                Document d = reader.document(i);
                if (getInLinks(d.get("title")) < minLinks) {
                    writer.deleteDocuments(new Term("id", d.get("id")));
                }
            }
            reader.close();
            writer.commit();
            writer.forceMergeDeletes(true);
            LOG.info(getName() + " had " + writer.numDocs() + " docs after inlink pruning");
        }

        if (addInLinksToText || ArrayUtils.contains(fields, "inlinks")) {
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
                    long hash = titleHash(d.get("title"));
                    if (addInLinksToText) {
                        if (inLinkText.containsKey(hash)) {
                            String text = d.get("text") + inLinkText.get(hash);
                            d.removeFields("text");
                            d.add(new TextField("text", text, Field.Store.YES));
                        }
                        d.removeFields("linktext");
                    }
                    if (ArrayUtils.contains(fields, "inlinks")) {
                        int l = getInLinks(hash);
                        d.add(new IntField("inlinks", l, Field.Store.YES));
                    }
                    writer.updateDocument(new Term("id", d.get("id")), d);
                }
            }
            LOG.info("finished adding inlinks text to " + n + " docs");
            writer.commit();
            reader.close();
        }

        super.close();
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
