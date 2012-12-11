package edu.macalester.wpsemsim.sim.category;

import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.set.TIntSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;

import java.util.PriorityQueue;

public class CategoryBfs {
    private CategoryGraph graph;
    private int startPage;
    private int maxResults;
    private TIntDoubleHashMap catDistances = new TIntDoubleHashMap();
    private TIntDoubleHashMap pageDistances = new TIntDoubleHashMap();
    private PriorityQueue<CategoryDistance> openCats = new PriorityQueue<CategoryDistance>();
    private BfsFinished finished = new BfsFinished();

    public boolean addPages = true;
    public boolean exploreChildren = true;
    private TIntSet validWpIds;

    public CategoryBfs(CategoryGraph graph, Document start, int maxResults, TIntSet validWpIds) {
        this.startPage = Integer.valueOf(start.getField("id").stringValue());
        this.maxResults = maxResults;
        this.graph = graph;
        this.validWpIds = validWpIds;
        pageDistances.put(startPage, 0.000000);
        for (IndexableField f : start.getFields("cats")) {
            int ci = graph.getCategoryIndex(f.stringValue());
            openCats.add(new CategoryDistance(ci, graph.cats[ci], graph.catCosts[ci], (byte)+1));
        }
    }

    public void setAddPages(boolean addPages) {
        this.addPages = addPages;
    }

    public void setExploreChildren(boolean exploreChildren) {
        this.exploreChildren = exploreChildren;
    }

    public boolean hasMoreResults() {
        return openCats.size() > 0 && pageDistances.size() < maxResults;
    }

    public BfsFinished step() {
        finished.clear();
        if (!hasMoreResults()) {
            return finished;
        }
        CategoryDistance cs;
        do {
            cs = openCats.poll();
        } while (hasMoreResults() && catDistances.contains(cs.getCatIndex()));

        finished.cats.put(cs.getCatIndex(), cs.getDistance());
        catDistances.put(cs.getCatIndex(), cs.getDistance());
//        System.out.println("visited " + cs.toString());

        // add directly linked pages
        if (addPages) {
            for (int i : graph.catPages[cs.getCatIndex()]) {
                if (validWpIds != null && !validWpIds.contains(i)) {
                    continue;
                }
                if (!pageDistances.containsKey(i) || pageDistances.get(i) > cs.getDistance()) {
                    pageDistances.put(i, cs.getDistance());
                    finished.pages.put(i, cs.getDistance());
                }
                if (pageDistances.size() >= maxResults) {
                    break;  // may be an issue for huge categories
                }
            }
        }

        // next steps downwards
        if (exploreChildren) {
            for (int i : graph.catChildren[cs.getCatIndex()]) {
                if (!catDistances.containsKey(i)) {
                    double d = cs.getDistance() + graph.catCosts[i];
                    openCats.add(new CategoryDistance(i, graph.cats[i], d, (byte)-1));
                }
            }
        }

        // next steps upwards (if still possible)
        if (cs.getDirection() == +1) {
            for (int i : graph.catParents[cs.getCatIndex()]) {
                if (!catDistances.containsKey(i)) {
                    double d = cs.getDistance() + graph.catCosts[i];
                    openCats.add(new CategoryDistance(i, graph.cats[i], d, (byte)+1));
                }
            }
        }

        return finished;
    }

    public TIntDoubleHashMap getPageDistances() {
        return pageDistances;
    }
    public boolean hasPageDistance(int pageId) {
        return pageDistances.containsKey(pageId);
    }
    public double getPageDistance(int pageId) {
        return pageDistances.get(pageId);
    }
    public boolean hasCategoryDistance(int categoryId) {
        return catDistances.containsKey(categoryId);
    }
    public double getCategoryDistance(int categoryId) {
        return catDistances.get(categoryId);
    }

    public class BfsFinished {
        TIntDoubleHashMap pages = new TIntDoubleHashMap();
        TIntDoubleHashMap cats = new TIntDoubleHashMap();
        public void clear() { pages.clear(); cats.clear(); }
        public double maxPageDistance() { return max(pages.values()); }
        public double maxCatDistance() { return max(cats.values()); }
    }

    private double max(double []A) {
        double max = Double.NEGATIVE_INFINITY;
        for (double x : A) {
            if (x > max) max = x;
        }
        return max;
    }
}
