package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.lucene.DocBooster;
import edu.macalester.wpsemsim.lucene.Page;
import org.apache.lucene.document.Document;

public class InLinkBooster implements DocBooster {
    int numLogs;
    double pow;

    public InLinkBooster() {
        this(1, 1.0);
    }

    public InLinkBooster(int numLogs, double pow) {
        this.numLogs = numLogs;
        this.pow = pow;
    }

    public void setNumLogs(int numLogs) {
        this.numLogs = numLogs;
    }

    public void setPow(double pow) {
        this.pow = pow;
    }

    @Override
    public String[] getBoostedFields() {
        return new String[] { Page.FIELD_TEXT, Page.FIELD_INLINKS, Page.FIELD_LINKS };
    }

    @Override
    public double getBoost(Document d) {
        double value = d.getField(Page.FIELD_NINLINKS).numericValue().intValue();
        for (int i = 0; i < numLogs; i++) {
            value = Math.log(1.0 + value);
        }
        return Math.pow(value, pow);
    }
}
