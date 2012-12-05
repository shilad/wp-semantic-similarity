package edu.macalester.wpsemsim.lucene;

import org.apache.lucene.document.Document;

/**
 * Boosts fields in documents, increasing their score in lucene search results.
 */
public interface DocBooster {
    String [] getBoostedFields();
    double getBoost(Document d);
}
