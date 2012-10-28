package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.lucene.IndexHelper;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexableField;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CategoryGraph {

    private static final Logger LOG = Logger.getLogger(CategoryGraph.class.getName());

    protected DirectoryReader reader;

    protected Map<String,Integer> catIndexes;
    protected Set<Integer> topLevelCategories;

    protected double[] catCosts;  // the cost of travelling through each category
    protected int[][] catParents;
    protected int[][] catPages;
    protected int[][] catChildren;
    protected String[] cats;
    protected double minCost = -1;
    protected IndexHelper helper;

    public CategoryGraph(IndexHelper helper) throws IOException {
        this.helper = helper;
        this.reader = helper.getReader();
    }

    public void init() throws IOException {
        loadCategories();
        buildGraph();
        calculateTopLevelCategories();
        computePageRanks();
    }

    private void loadCategories() throws IOException {
        LOG.info("loading categories...");
        this.catIndexes = new HashMap<String, Integer>();
        List<String> catList = new ArrayList<String>();
        for (int i=0; i < reader.maxDoc(); i++) {
            Document d = reader.document(i);
            if (isCat(d)) {
                String cat = cleanTitle(d);
                if (!catIndexes.containsKey(cat)) {
                    catIndexes.put(cat, catIndexes.size());
                    catList.add(cat);
                }
            }
//            StringBuffer buff = new StringBuffer();
            for (IndexableField f : d.getFields("cats")) {
                String cat = cleanTitle(f.stringValue());
//                buff.append(", " + cat);
                if (!catIndexes.containsKey(cat)) {
                    catIndexes.put(cat, catIndexes.size());
                    catList.add(cat);
                }
            }
        }
        cats = catList.toArray(new String[0]);
        LOG.info("finished loading " + cats.length + " categories");
    }

    public boolean isCat(Document d) {
        return d.getField("ns").stringValue().equals("14");
    }

    public final String cleanTitle(Document d) {
        return cleanTitle(d.get("title"));
    }

    public final String cleanTitle(String title) {
        String s = title.replaceAll("_", " ").toLowerCase();
        if (s.startsWith("category:")) {
            s = s.substring("category:".length());
        }
        return s;
    }

    private void buildGraph() throws IOException {
        LOG.info("building category graph");
        this.catPages = new int[catIndexes.size()][];
        this.catParents = new int[catIndexes.size()][];
        this.catChildren = new int[catIndexes.size()][];
        this.catCosts = new double[catIndexes.size()];

        Arrays.fill(catPages, new int[0]);
        Arrays.fill(catParents, new int[0]);
        Arrays.fill(catChildren, new int[0]);

        // count reverse edges
        int totalEdges = 0;
        int numCatChildren[] = new int[catIndexes.size()];
        int numCatPages[] = new int[catIndexes.size()];
        for (int i = 0; i < reader.maxDoc(); i++) {
            Document d = reader.document(i);
            int catId1 = -1;
            if (isCat(d)) {
                catId1 = getCategoryIndex(d);
            }
            for (IndexableField f : d.getFields("cats")) {
                int catId2 = getCategoryIndex(f.stringValue());
                if (catId1 >= 0) {
                    numCatChildren[catId2]++;
                } else {
                    numCatPages[catId2]++;
                }
                totalEdges++;
            }
        }

        // allocate space
        for (int i = 0; i < catIndexes.size(); i++) {
            catPages[i] = new int[numCatPages[i]];
            catChildren[i] = new int[numCatChildren[i]];
        }

        // fill it

        for (int i = 0; i < reader.maxDoc(); i++) {
            Document d = reader.document(i);
            IndexableField[] catFields = d.getFields("cats");
            int pageId = Integer.valueOf(d.getField("id").stringValue());
            int catId1 = -1;
            if (isCat(d)) {
                catId1 = getCategoryIndex(d);
                catParents[catId1] = new int[catFields.length];
            }
            for (int j = 0; j < catFields.length; j++) {
                int catId2 = getCategoryIndex(catFields[j].stringValue());
                if (catId1 >= 0) {
                    catChildren[catId2][--numCatChildren[catId2]] = catId1;
                    catParents[catId1][j] = catId2;
                } else {
                    catPages[catId2][--numCatPages[catId2]] = pageId;
                }
            }
        }
        for (int n : numCatChildren) { assert(n == 0); }
        for (int n : numCatPages) { assert(n == 0); }
        LOG.info("loaded " + totalEdges + " edges in category graph");
//        for (int i = 0; i < catChildren.length; i+= 10000) {
//            System.err.println("info for cat " + i + " " + cats[i]);
//            System.err.println("\tparents:" + Arrays.toString(catParents[i]));
//            System.err.println("\tchildren:" + Arrays.toString(catChildren[i]));
//            System.err.println("\tpages:" + Arrays.toString(catPages[i]));
//        }
    }

    public void computePageRanks() {
        LOG.info("computing category page ranks...");

        // initialize page rank
        long sumCredits = catPages.length;    // each category gets 1 credit to start
        for (int i = 0; i < catPages.length; i++) {
            sumCredits += catPages[i].length; // one more credit per page that references it.
        }
        for (int i = 0; i < catPages.length; i++) {
            catCosts[i] = (1.0 + catPages[i].length) / sumCredits;
        }

        for (int i = 0; i < 20; i++) {
            LOG.log(Level.INFO, "performing page ranks iteration {0}.", i);
            double error = onePageRankIteration();
            LOG.log(Level.INFO, "Error for iteration is {0}.", error);
            if (error == 0) {
                break;
            }
        }
        Integer sortedIndexes[] = new Integer[catCosts.length];
        for (int i = 0; i < catParents.length; i++) {
            catCosts[i] = 1.0/-Math.log(catCosts[i]);
            sortedIndexes[i] = i;
        }
        LOG.info("finished computing page ranks...");
        Arrays.sort(sortedIndexes, new Comparator<Integer>() {
            @Override
            public int compare(Integer i1, Integer i2) {
                Double pr1 = catCosts[i1];
                Double pr2 = catCosts[i2];
                return -1 * pr1.compareTo(pr2);
            }
        });

        StringBuffer b = new StringBuffer();
        for (int i = 0; i < 20; i++) {
            int j = sortedIndexes[i];
            b.append("" + i + ". " + cats[j] + "=" + catCosts[j]);
            b.append(", ");
        }
        minCost = catCosts[sortedIndexes[sortedIndexes.length - 1]];

        LOG.info("Min cat cost: " + minCost);
        LOG.info("Top cat costs: " + b.toString());
    }

    public void dump(BufferedWriter writer) throws IOException {

        writer.write("\n\nNon-orphaned category hierarchy:\n");
        for (int i = 0; i < cats.length; i++) {
            if (isUsefulCat(i)) {
                writer.write(
                        "id=" + i + ", " + cats[i] +
                        ", parents=" + catIndexesToString(catParents[i]) +
                        ", children=" + catIndexesToString(catChildren[i]) + "\n");
            }
        }

        Integer sortedIndexes[] = new Integer[catCosts.length];
        double nonUsefulPageRank = Double.MAX_VALUE;
        for (int i = 0; i < catParents.length; i++) {
            sortedIndexes[i] = i;
            if (!isUsefulCat(i)) {
                if (nonUsefulPageRank == Double.MAX_VALUE) {
                    nonUsefulPageRank = catCosts[i];
                } else {
                    assert(nonUsefulPageRank == catCosts[i]);
                }
            }
        }
        writer.write("\n\nPage ranks of non-useful cats: " + nonUsefulPageRank + "\n");
        Arrays.sort(sortedIndexes, new Comparator<Integer>() {
            @Override
            public int compare(Integer i1, Integer i2) {
                Double pr1 = catCosts[i1];
                Double pr2 = catCosts[i2];
                return -1 * pr1.compareTo(pr2);
            }
        });
        writer.write("\n\nPage ranks of useful cats:\n");
        for (int i = 0; i < sortedIndexes.length; i++) {
            int j = sortedIndexes[i];
            if (isUsefulCat(j)) {
                writer.write("" + i + ". " + " i=" + j + ", " +
                        cats[j] + "=" + catCosts[j] + "\n");
            }
        }

        writer.write("\n\nPages to non-orphaned categories\n");
        TIntObjectHashMap<TIntArrayList> pagesToCats = new TIntObjectHashMap<TIntArrayList>();
        for (int i = 0; i < catPages.length; i++) {
            if (isUsefulCat(i)) {
                for (int j : catPages[i]) {
                    if (!pagesToCats.containsKey(j)) {
                        pagesToCats.put(j, new TIntArrayList());
                    }
                    pagesToCats.get(j).add(i);
                }
            }
        }

        int pageIds[] = pagesToCats.keys();
        Arrays.sort(pageIds);
        for (int wpId : pageIds) {
            writer.write(helper.wpIdToTitle(wpId) +
                    " (id=" + wpId + ") " +
                    ": " + catIndexesToString(pagesToCats.get(wpId).toArray())
                    + "\n");
        }
    }

    private boolean isUsefulCat(int i) {
        return (catParents[i].length > 0 || catChildren[i].length > 0 || catPages[i].length > 1);
    }

    private String catIndexesToString(int indexes[]) {
        StringBuffer sb = new StringBuffer("[");
        for (int i : indexes) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(cats[i]);
            sb.append(" (id=");
            sb.append(""+i);
            sb.append(")");
        }
        return sb.append("]").toString();
    }

    private static final double DAMPING_FACTOR = 0.85;
    protected double onePageRankIteration() {
        double nextRanks [] = new double[catCosts.length];
        Arrays.fill(nextRanks, (1.0 - DAMPING_FACTOR) / catCosts.length);
        for (int i = 0; i < catParents.length; i++) {
            int d = catParents[i].length;   // degree
            double pr = catCosts[i];    // current page-rank
            for (int j : catParents[i]) {
                nextRanks[j] += DAMPING_FACTOR * pr / d;
            }
        }
        double diff = 0.0;
        for (int i = 0; i < catParents.length; i++) {
            diff += Math.abs(catCosts[i] - nextRanks[i]);
        }
        catCosts = nextRanks;
        return diff;
    }

    public int getCategoryIndex(Document d) {
        return getCategoryIndex(d.get("title"));
    }

    public int getCategoryIndex(String cat) {
        cat = cleanTitle(cat);
        if (catIndexes.containsKey(cat)) {
            return catIndexes.get(cat);
        } else {
            return -1;
        }
    }

    private void calculateTopLevelCategories() {
        LOG.info("marking top level categories off-limits.");
        int numSecondLevel = 0;
        topLevelCategories = new HashSet<Integer>();
        for (String name : TOP_LEVEL_CATS) {
            int index = getCategoryIndex(name);
            if (index >= 0) {
                topLevelCategories.add(index);
                for (int ci : catChildren[index]) {
//                    topLevelCategories.add(ci);
                    numSecondLevel++;
                }
            }
        }
        LOG.log(Level.INFO, "marked {0} top-level and {1} second-level categories.",
                new Object[] {TOP_LEVEL_CATS.length, numSecondLevel} );
    }

    public static String [] TOP_LEVEL_CATS = {
            "Agriculture", "Applied Sciences", "Arts", "Belief", "Business", "Chronology", "Computers",
            "Culture", "Education", "Environment", "Geography", "Health", "History", "Humanities",
            "Language", "Law", "Life", "Mathematics", "Nature", "People", "Politics", "Science", "Society",

            "Concepts", "Life", "Matter", "Society",

    };
}
