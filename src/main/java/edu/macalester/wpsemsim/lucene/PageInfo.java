package edu.macalester.wpsemsim.lucene;

import edu.macalester.wpsemsim.utils.TitleMap;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntLongHashMap;

public class PageInfo {
    TitleMap<TIntArrayList> inLinks = new TitleMap<TIntArrayList>(TIntArrayList.class);
    TitleMap<Integer> pageIds = new TitleMap<Integer>(Integer.class);
    TIntLongHashMap wpIdsToHashes = new TIntLongHashMap();
    TIntLongHashMap redirects = new TIntLongHashMap();

    public void update(Page p) {
        synchronized (pageIds) {
            pageIds.put(p.getTitle(), p.getId());
        }
        synchronized (wpIdsToHashes) {
            wpIdsToHashes.put(p.getId(), getTitleHash(p.getTitle()));
        }
        for (String link : p.getAnchorLinks()) {
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
