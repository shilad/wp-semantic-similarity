package edu.macalester.wpsemsim.lucene;

import org.apache.lucene.document.Document;

public interface DocBooster {
    String [] getBoostedFields();
    double getBoost(Document d);
}
