package edu.macalester.wpsemsim.concepts;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.lucene.IndexHelper;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

/**
 * Maps concepts by searching for a phrase in the "text" field of a lucene index.
 */
public class TitleMapper extends DictionaryMapper {
    private static final Logger LOG = Logger.getLogger(TitleMapper.class.getName());

    public TitleMapper(File path, IndexHelper helper) throws IOException, DatabaseException {
        super(path, helper);
    }

    @Override
    public LinkedHashMap<String, Float> map(String text, int maxConcepts) {
        // the top entry SHOULD be the title.
        LinkedHashMap<String, Float> result = super.map(text, 1);
//        if (!result.isEmpty()) LOG.info("mapped " + text + " to " + result.keySet().iterator().next());
        return result;
    }
}
