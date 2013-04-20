package edu.macalester.wpsemsim.concepts;

import java.util.LinkedHashMap;
import java.util.List;


/**
 * A mapper that wraps a list of delegate mappers.
 * When given a phrase, it queries the delegate mappers and
 * returns the result from the first delegate with a non-null mapping.
 */
public class HierarchicalMapper implements ConceptMapper {
    private List<ConceptMapper> mappers;

    public HierarchicalMapper(List<ConceptMapper> mappers) {
        this.mappers = mappers;
    }

    @Override
    public LinkedHashMap<String, Float> map(String text, int maxConcepts) {
        for (ConceptMapper mapper : mappers) {
            LinkedHashMap<String, Float> result = mapper.map(text, maxConcepts);
            if (result != null && !result.isEmpty()) {
                return result;
            }
        }
        return null;
    }
}
