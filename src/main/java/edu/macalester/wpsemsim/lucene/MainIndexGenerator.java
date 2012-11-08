package edu.macalester.wpsemsim.lucene;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;

import java.io.IOException;

public class MainIndexGenerator extends BaseIndexGenerator {

    private static String fields[] = new String[] {
            "title", "id", "type", "dab", "redirect"
    };

    public MainIndexGenerator() {
        super("main");
    }

    public boolean shouldInclude(Page p) {
        return (p.getNs() == 0);
    }

    @Override
    public void storePage(Page p) throws IOException {
        if (!shouldInclude(p)) {
            return;
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
}
