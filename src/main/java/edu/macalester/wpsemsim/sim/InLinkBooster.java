package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.lucene.DocBooster;
import edu.macalester.wpsemsim.lucene.Page;
import org.apache.lucene.document.Document;

public class InLinkBooster implements DocBooster {
    @Override
    public String[] getBoostedFields() {
        return new String[] { Page.FIELD_TEXT, Page.FIELD_INLINKS, Page.FIELD_LINKS };
    }

    @Override
    public double getBoost(Document d) {
        return Math.log(Math.log(d.getField(Page.FIELD_NINLINKS).numericValue().intValue()));
    }
}
