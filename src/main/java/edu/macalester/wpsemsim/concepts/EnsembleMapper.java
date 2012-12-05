package edu.macalester.wpsemsim.concepts;

import gnu.trove.map.hash.TObjectDoubleHashMap;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps concepts by adding up the concept mapping scores from
 * several underlying concept mappers.
 */
public class EnsembleMapper implements ConceptMapper {
    private ConceptMapper[] mappers;

    public EnsembleMapper(ConceptMapper ... mappers) {
        this.mappers = mappers;
    }
    @Override
    public LinkedHashMap<String, Float> map(String text, int maxConcepts) {
        final TObjectDoubleHashMap<String> scores = new TObjectDoubleHashMap<String>();
        for (ConceptMapper mapper : mappers) {
            for (Map.Entry<String, Float> e : mapper.map(text, maxConcepts * 5).entrySet()) {
                scores.adjustOrPutValue(e.getKey(), e.getValue(), e.getValue());
            }
        }
        String articles[] = scores.keys(new String[0]);
        Arrays.sort(articles, new Comparator<String>() {
            @Override
            public int compare(String a1, String a2) {
                return -1 * new Double(scores.get(a1)).compareTo(scores.get(a2));
            }
        });
        LinkedHashMap<String, Float> result = new LinkedHashMap<String, Float>();
        for (int i = 0; i < maxConcepts && i < articles.length; i++)  {
            result.put(articles[i], (float)scores.get(i));
        }
        return result;
    }
}
