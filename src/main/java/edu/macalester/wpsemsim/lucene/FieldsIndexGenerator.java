package edu.macalester.wpsemsim.lucene;

import edu.macalester.wpsemsim.utils.TitleMap;
import gnu.trove.list.array.TIntArrayList;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.util.Bits;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class FieldsIndexGenerator extends BaseIndexGenerator<FieldsIndexGenerator> {
    private static Logger LOG = Logger.getLogger(BaseIndexGenerator.class.getName());

    // By default only include main namespace
    private int namespaces[] = new int[] { 0 };

    // Include all fields below
    private String fields[];
    private int minLinks = 0;
    private int minWords = 0;
    private int titleMultiplier = 0;
    private boolean addInLinksToText = false;

    private DocBooster booster;

    TitleMap<StringBuffer> inLinkText = new TitleMap<StringBuffer>(StringBuffer.class);
    TitleMap<TIntArrayList> inLinks = new TitleMap<TIntArrayList>(TIntArrayList.class);
    TitleMap<Integer> numInLinks = new TitleMap<Integer>(Integer.class);
    TitleMap<Integer> pageIds = new TitleMap<Integer>(Integer.class);

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
        } else if (p.isList() || p.isRedirect() || p.isDisambiguation()) {
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
                numInLinks.increment(l);
            }
        }
        Document source = p.toLuceneDoc();
        Document pruned = new Document();

        for (String fieldName : fields) {
            for (IndexableField f : source.getFields(fieldName)) {
                pruned.add(f);
            }
        }

        // do we still need to add the linktext?
        if (addInLinksToText && !doField(Page.FIELD_LINKTEXT)) {
            for (IndexableField f : source.getFields(Page.FIELD_LINKTEXT)) {
                pruned.add(f);
            }
        }

        if (titleMultiplier > 0) {
            String text = pruned.get(Page.FIELD_TEXT);
            for (int i = 0; i < titleMultiplier; i++) {
                text += "\n" + source.get(Page.FIELD_TITLE);
            }
            pruned.removeFields(Page.FIELD_TEXT);
            pruned.add(new TextField(Page.FIELD_TEXT, text, Field.Store.YES));
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
            if (doField(Page.FIELD_LINKS)) {
                pageIds.put(d.get("title"), wpId);
            }
            for (int j = 0; j < links.length; j++) {
                String link = links[j].stringValue();
                if (minLinks > 0 && numInLinks.get(link) < minLinks) {
                    continue;
                }
                if (doField(Page.FIELD_LINKTEXT) || addInLinksToText) {
                    inLinkText.get(link).append("\n" + texts[j].stringValue());
                }
                if (doField(Page.FIELD_INLINKS)) {
                    inLinks.get(link).add(wpId);
                }
            }
        }
        reader.close();
        writer.commit();
    }

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
            if (numInLinks.get(d.get(Page.FIELD_TITLE)) < minLinks) {
                writer.deleteDocuments(new Term("id", ""+wpId));
            }
        }
        reader.close();
        writer.commit();
        writer.forceMergeDeletes(true);
        LOG.info(getName() + " had " + writer.numDocs() + " docs after pruning");
    }

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
                int l = numInLinks.get(title);
                d.add(new IntField(Page.FIELD_NINLINKS, l, Field.Store.YES));
            }
            if (doField(Page.FIELD_INLINKS) && inLinks.containsKey(title)) {
                for (int wpId : inLinks.get(title).toArray()) {
                    d.add(new StringField(Page.FIELD_INLINKS, ""+wpId, Field.Store.YES));
                }
            }
            if (doField(Page.FIELD_LINKS)) {
                IndexableField links[] = d.getFields(Page.FIELD_LINKS);
                d.removeFields(Page.FIELD_LINKS);
                for (IndexableField l : links) {
                    int wpId = pageIds.get(l.stringValue());
                    if (wpId > 0) {
                        d.add(new StringField(Page.FIELD_LINKS, ""+wpId, Field.Store.YES));
                    }
                }
            }
            if (booster != null) {
                double boost = booster.getBoost(d);
                for (String f : booster.getBoostedFields()) {
                    ((Field)d.getField(f)).setBoost((float)boost);
                }
            }
            writer.updateDocument(new Term("id", d.get("id")), Page.correctMetadata(d));
        }
        LOG.info("finished updating fields in " + n + " docs");
        writer.commit();
        reader.close();
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
