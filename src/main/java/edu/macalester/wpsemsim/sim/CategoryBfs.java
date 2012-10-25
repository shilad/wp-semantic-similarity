package edu.macalester.wpsemsim.sim;

import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.set.hash.TIntHashSet;
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
    private TIntHashSet closedCats = new TIntHashSet();
    private BfsDiscoveries discoveries = new BfsDiscoveries();

    public boolean addPages = true;
    public boolean exploreChildren = true;

    public CategoryBfs(CategoryGraph graph, Document start, int maxResults) {
        this.startPage = Integer.valueOf(start.getField("id").stringValue());
        this.maxResults = maxResults;
        this.graph = graph;
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

    public BfsDiscoveries bfsIteration() {
        discoveries.clear();
        if (!hasMoreResults()) {
            return discoveries;
        }
        CategoryDistance cs;
        do {
            cs = openCats.poll();
        } while (hasMoreResults() && closedCats.contains(cs.getCatIndex()));

        closedCats.add(cs.getCatIndex());

        // add directly linked pages
        if (addPages) {
            for (int i : graph.catPages[cs.getCatIndex()]) {
                if (!pageDistances.containsKey(i) || pageDistances.get(i) > cs.getDistance()) {
                    pageDiscovered(i, cs.getDistance());
                }
                if (pageDistances.size() >= maxResults) {
                    break;  // may be an issue for huge categories
                }
            }
        }

        // next steps downwards
        if (exploreChildren) {
            for (int i : graph.catChildren[cs.getCatIndex()]) {
                if (!closedCats.contains(i) && !catDistances.containsKey(i)) {
                    double d = cs.getDistance() + graph.catCosts[cs.getCatIndex()];
                    categoryDiscovered(i, d, -1);
                }
            }
        }

        // next steps upwards (if still possible)
        if (cs.getDirection() == +1) {
            for (int i : graph.catParents[cs.getCatIndex()]) {
                double d = cs.getDistance() + graph.catCosts[cs.getCatIndex()];
                if (!closedCats.contains(i) && (!catDistances.containsKey(i) || catDistances.get(i) > d)) {
                    categoryDiscovered(i, d, +1);
                }
            }
        }

        return discoveries;
    }

    public void pageDiscovered(int pageId, double distance) {
        pageDistances.put(pageId, distance);
        discoveries.pages.put(pageId, distance);
    }

    public void categoryDiscovered(int catIndex, double distance, int direction) {
        assert(direction == +1 || direction == -1);
        discoveries.cats.put(catIndex, distance);
        catDistances.put(catIndex, distance);
        openCats.add(new CategoryDistance(catIndex, graph.cats[catIndex], distance, (byte)direction));
    }

    public TIntDoubleHashMap getPageDistances() {
        return pageDistances;
    }

    public boolean hasCategoryDistance(int categoryId) {
        return catDistances.containsKey(categoryId);
    }
    public double getCategoryDistance(int categoryId) {
        return catDistances.get(categoryId);
    }

    public class BfsDiscoveries {
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
