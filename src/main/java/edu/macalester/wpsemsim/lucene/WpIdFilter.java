package edu.macalester.wpsemsim.lucene;

import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.Filter;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.DocIdBitSet;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;


/**
 * A lucene filter that only includes a specific set of Wikipedia ids.
 * The constructor is EXPENSIVE, so they should be reused.
 * TODO: Perform a search when there are relatively few wpIds.
 */
public class WpIdFilter extends Filter {
    private static final Logger LOG = Logger.getLogger(WpIdFilter.class.getName());
    private int luceneIds[];

    public WpIdFilter(IndexHelper helper, int wpIds[]) throws IOException {
        LOG.info("building WpId filter for " + wpIds.length + " ids with hash " + Arrays.hashCode(wpIds));
        TIntSet wpIdSet = new TIntHashSet(wpIds);
        TIntSet luceneIdSet = new TIntHashSet();
        Set<String> fields = new HashSet<String>(Arrays.asList(Page.FIELD_WPID));
        for (int i = 0; i < helper.getReader().numDocs(); i++) {
            Document d = helper.getReader().document(i, fields);
            int wpId = Integer.valueOf(d.get(Page.FIELD_WPID));
            if (wpIdSet.contains(wpId)) {
                luceneIdSet.add(i);
            }
        }
        luceneIds = luceneIdSet.toArray();
        LOG.info("WpId filter matched " + luceneIds.length + " ids.");

    }

    @Override
    public DocIdSet getDocIdSet(AtomicReaderContext context, Bits acceptDocs) throws IOException {
        BitSet bits = new BitSet();
        int i = 0;
        for (int id : luceneIds) {
            if (acceptDocs == null || acceptDocs.get(id)) {
                bits.set(id);
                i++;
            }
        }
        int n = 0;
        for (int id = 0; id < bits.length(); id++) {
            if (bits.get(id)) n++;
        }
//        LOG.info("bit size=" + bits.size() + " set=" + n + " compared to " + luceneIds.length);
        return new DocIdBitSet(bits);
    }
}
