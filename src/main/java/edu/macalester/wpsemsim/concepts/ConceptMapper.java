package edu.macalester.wpsemsim.concepts;

import java.util.LinkedHashMap;

public interface ConceptMapper {

    /**
     * Maps text to Wikipedia articles (keys).
     * Each article is assigned a score (values).
     * The iteration order must be highest concept scores first.
     * @param text
     * @return
     */
    public LinkedHashMap<String, Float> map(String text, int maxConcepts);
}
