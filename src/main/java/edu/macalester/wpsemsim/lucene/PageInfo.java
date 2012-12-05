package edu.macalester.wpsemsim.lucene;

import edu.macalester.wpsemsim.utils.TitleMap;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntLongHashMap;

import java.util.HashSet;

/**
 * Information about pages that may be needed by multiple index generators.
 * Only one instance of this object is created, and it is shared by all index
 * generators. Save memory by putting shared information here.
 */
public class PageInfo {
    /**
     * Wikipedia titles to a list of inbound Wikipedia page ids.
     * Page ids in each inbound list are unique.
     */
    TitleMap<TIntArrayList> inLinks= new TitleMap<TIntArrayList>(TIntArrayList.class);

    /**
     * Map from titles to wikipedia ids.
     */
    TitleMap<Integer> pageIds = new TitleMap<Integer>(Integer.class);

    /**
     * Map from titles to hashes of titles.
     */
    TIntLongHashMap wpIdsToHashes = new TIntLongHashMap();

    TIntLongHashMap redirects = new TIntLongHashMap();

    public PageInfo() {
    }

    public void update(Page p) {
        synchronized (pageIds) {
            pageIds.put(p.getTitle(), p.getId());
        }
        synchronized (wpIdsToHashes) {
            wpIdsToHashes.put(p.getId(), getTitleHash(p.getTitle()));
        }
        for (String link : new HashSet<String>(p.getAnchorLinks())) {
            synchronized (inLinks) {
                inLinks.get(link).add(p.getId());
            }
        }
//        if (p.isRedirect()) {
//            synchronized (redirects) {
//                redirects.put(p.getId(), getTitleHash(p.getRedirect()));
//            }
//        }
    }

    public synchronized TIntList getInLinks(String title) {
        return inLinks.get(title);
    }

    public synchronized TIntList getInLinks(int wpId) {
        long h = wpIdsToHashes.get(wpId);
        return inLinks.get(h);
    }

    public synchronized int getPageId(String title) {
        return pageIds.get(title);
    }

    private long getTitleHash(String title) {
        return pageIds.titleHash(title);
    }
}
