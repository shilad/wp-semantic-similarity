package edu.macalester.wpsemsim.concepts;

import java.util.LinkedHashMap;

public interface ConceptMapper {

    /**
     * Maps text to Wikipedia articles (keys).
     * Each article is assigned a score (values).
     * @param text
     * @return
     */
    public LinkedHashMap<String, Float> map(String text, int maxConcepts);
}
