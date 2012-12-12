package edu.macalester.wpsemsim.lucene;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexableField;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data corresponding to a single Wikipedia page.
 */
public final class Page {
    /**
     * Field names used in the mapping to a lucene document.
     * TODO: change to an enum.
     */
    public static final String FIELD_LINKTEXT = "linktext";
    public static final String FIELD_NINLINKS = "ninlinks";
    public static final String FIELD_INLINKS = "inlinks";
    public static final String FIELD_LINKS = "links";
    public static final String FIELD_TEXT = "text";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_WPID = "id";
    public static final String FIELD_DAB = "dab";
    public static final String FIELD_REDIRECT = "redirect";
    public static final String FIELD_CATS = "cats";

    private int id;
    private String title;
    private String text;
    private int ns = 0;
    private String redirect = null;
    private String strippedText = null;
    private Document luceneDoc;

    public Page(int ns, int id, String redirect, String title, String text) {
        this.ns = ns;
        this.id = id;
        this.title = title;
        this.redirect = redirect;
        this.text = text;
    }

    public int getId() {
        return id;
    }

    /**
     * Gets the text of the Wikipedia source by trying to strip of WikiMarkup tags, etc.
     * This is a relatively expensive operation, so it is cached.
     * @return Textual content of wiki markup
     */
    public synchronized String getStrippedText() {
        if (this.strippedText == null) {
            this.strippedText = MarkupStripper.stripEverything(text);
        }
        return this.strippedText;
    }

    public String getTitle() {
        return title;
    }

    public String getText() {
        return text;
    }

    public int getNs() {
        return ns;
    }

    /**
     * Determines whether a page is a List page (i.e. "List of United States Presidents").
     * @return
     */
    public boolean isList() {
        String words[] = title.toLowerCase().split("[^a-zA-Z0-9]+");
        return (Collections.indexOfSubList(
                Arrays.asList(words), Arrays.asList("list", "of")) >= 0);
    }

    public boolean isRedirect() {
        return redirect != null;
    }

    public String getRedirect() {
        return redirect;
    }

    /**
     * Convert the page to a lucene document.
     * @return
     */
    public synchronized Document toLuceneDoc() {
        if (this.luceneDoc == null) {
            Document d = new Document();
            d.add(new StringField(FIELD_TITLE, title, Field.Store.YES));
            d.add(new StringField(FIELD_WPID, ""+id, Field.Store.YES));
            d.add(new StringField("ns", ""+ns, Field.Store.YES));
            d.add(new TextField(FIELD_TEXT, text, Field.Store.YES));
            for (String l : getAnchorLinks()) {
                d.add(new NormedStringField(FIELD_LINKS, l, Field.Store.YES));
            }
            for (String l : getTextOfAnchors()) {
                d.add(new StringField(FIELD_LINKTEXT, l, Field.Store.YES));
            }
            for (String c : getCategories()) {
                d.add(new StringField(FIELD_CATS, c, Field.Store.YES));
            }
            String type;
            if (redirect != null) {
                type = "redirect";
                d.add(new StringField(FIELD_REDIRECT, redirect, Field.Store.YES));
            } else if (isDisambiguation()) {
                type = "dab";
                for (String l : getDisambiguationLinks()) {
                    d.add(new StringField(FIELD_DAB, l, Field.Store.YES));
                }
            } else if (isList()) {
                type = "list";
            } else {
                type = "normal";
            }
            d.add(new StringField(FIELD_TYPE, type, Field.Store.YES));
            this.luceneDoc = d;
        }
        return this.luceneDoc   ;
    }

    /**
     * Given a lucene document, ensure that the fields have correct Metadata.
     * This is useful because when documents are retrieved using index.document(),
     * all metadata is lost.
     * @param d
     * @return New document with corrected metadata.
     */
    public static Document correctMetadata(Document d) {
        Document d2 = new Document();
        for (IndexableField f : d.getFields()) {
            IndexableField f2;
            if (f.name().equals(Page.FIELD_TEXT)) {
                f2 = new TextField(f.name(), f.stringValue(), Field.Store.YES);
            } else if (f.name().equals(Page.FIELD_NINLINKS)) {
                f2 = new IntField(f.name(), f.numericValue().intValue(), Field.Store.YES);
            } else if (f.name().equals(Page.FIELD_INLINKS) || f.name().equals(Page.FIELD_LINKS)) {
                f2 = new NormedStringField(f.name(), f.stringValue(), Field.Store.YES);
            } else {
                f2 = new StringField(f.name(), f.stringValue(), Field.Store.YES);
            }
            d2.add(f2);
        }
        return d2;
    }

    public static List<String> removeFragments(List<String> links) {
        List<String> result = new ArrayList<String>();
        for (String link : links) {
            int i = link.indexOf("#");
            if (i < 0) {
                result.add(link);
            } else {
                result.add(link.substring(0, i));
            }
        }
        return result;
    }
    public List<String> getAnchorLinks() {
        return removeFragments(getAnchorLinksWithFragments(text));
    }

    /**
     * Get the categories referenced by a page.
     * @return
     */
    public List<String> getCategories() {
        List<String> cats = new ArrayList<String>();
        for (String link : getAnchorLinks()) {
            if (link.toLowerCase().startsWith("category:")) {
                cats.add(link.substring("category:".length()));
            }
        }
        return cats;
    }

    private static final Pattern LINK_PATTERN = Pattern.compile("\\[\\[([^\\]]+?)\\]\\]");

    public static ArrayList<String> getAnchorLinksWithFragments(String text) {
        ArrayList<String> anchorLinks = new ArrayList<String>();
        Matcher linkMatcher;
        linkMatcher = LINK_PATTERN.matcher(text);
        while (linkMatcher.find()) {
            String addition = linkMatcher.group(1);
            if (addition.contains("|")) {
                addition = addition.substring(0, addition.indexOf("|"));
            }
            addition = addition.trim().replaceAll("\\s+", "_");
            if (!addition.contains("Image:")) {
                anchorLinks.add(addition);
            }
        }
        return anchorLinks;
    }

    public List<String> getTextOfAnchors() {
        ArrayList<String> descriptions = new ArrayList<String>();
        Matcher linkMatcher;
        linkMatcher = LINK_PATTERN.matcher(text);
        while (linkMatcher.find()) {
            String addition = linkMatcher.group(1);
            String description = addition;
            if (addition.contains("|")) {
                description = addition.substring(addition.indexOf("|") + 1);
                addition = addition.substring(0, addition.indexOf("|"));
            }
            addition = addition.trim().replaceAll("\\s+", "_");
            if (!addition.contains("Image:")) {
                descriptions.add(description);
            }
        }
        return descriptions;

    }

    private static final String DAB_BLACKLIST [] = {
            "disambiguation",
            "disambig",
            "dab",
            "disamb",
            "dmbox",
            "geodis",
            "numberdis",
            "dmbox",
            "mathdab",
            "hndis",
            "hospitaldis",
            "mathdab",
            "mountainindex",
            "roaddis",
            "schooldis",
            "shipindex"
    };

    private static final String DAB_WHITELIST [] = {
            "dablink",
            "disambiguation needed",
    };

    static final Pattern DAB_REGEX;
    static {
        String regex = "\\{\\s*\\{\\s*(";

        // whitelist
        regex += "(?!";
        regex += StringUtils.join(DAB_WHITELIST, "|");
        regex += ")";

        // blacklist (greedy, so that whitelist has a chance to match)
        regex += "(";
        regex += StringUtils.join(DAB_BLACKLIST, "|");
        regex += ".*))";
        System.out.println(regex);
        DAB_REGEX = Pattern.compile(regex);
    }

    public boolean isDisambiguation() {
        return isDisambiguation(text);
    }

    public static boolean isDisambiguation(String s) {
        return DAB_REGEX.matcher(s.toLowerCase()).find();
    }

    public int getNumUniqueWordsInText() {
        return new HashSet<String>(Arrays.asList(text.split("\\s+"))).size();
    }


    private static final Pattern DAB_LINK_PATTERN = Pattern.compile("\\*(?:[ \"']*)?\\s*\\[\\[([^\\]]+?)\\]\\](?:[ '\"]*)?");

    public List<String> getDisambiguationLinksWithFragments() {
        ArrayList<String> anchorLinks = new ArrayList<String>();
        Matcher linkMatcher;
        linkMatcher = DAB_LINK_PATTERN.matcher(text);
        int earliest = -1;
        while (linkMatcher.find()) {
            if (earliest == -1) earliest = linkMatcher.start();
            String addition = linkMatcher.group(1);
            if (addition.contains("|")) {
                addition = addition.substring(0, addition.indexOf("|"));
            }
            if (!addition.contains("Image:")) {
                anchorLinks.add(addition);
            }
        }
        // Add any links that appear before the first bulleted DAB link
        if (earliest > 0) {
            anchorLinks.addAll(0,
                    removeFragments(getAnchorLinksWithFragments(
                            text.substring(0, earliest))));
        }
        return anchorLinks;
    }

    public List<String> getDisambiguationLinks() {
        return removeFragments(getDisambiguationLinksWithFragments());
    }
}
