package edu.macalester.wpsemsim.lucene;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexableField;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Page {
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

    public synchronized Document toLuceneDoc() {
        if (this.luceneDoc == null) {
            Document d = new Document();
            d.add(new StringField("title", title, Field.Store.YES));
            d.add(new StringField("id", ""+id, Field.Store.YES));
            d.add(new StringField("ns", ""+ns, Field.Store.YES));
            d.add(new TextField("text", text, Field.Store.YES));
            for (String l : getAnchorLinks()) {
                d.add(new NormedStringField("links", l, Field.Store.YES));
            }
            for (String l : getTextOfAnchors()) {
                d.add(new StringField("linktext", l, Field.Store.YES));
            }
            for (String c : getCategories()) {
                d.add(new StringField("cats", c, Field.Store.YES));
            }
            String type;
            if (redirect != null) {
                type = "redirect";
                d.add(new StringField("redirect", redirect, Field.Store.YES));
            } else if (isDisambiguation()) {
                type = "dab";
                for (String l : getDisambiguationLinks()) {
                    d.add(new StringField("dab", l, Field.Store.YES));
                }
            } else if (isList()) {
                type = "list";
            } else {
                type = "normal";
            }
            d.add(new StringField("type", type, Field.Store.YES));
            this.luceneDoc = d;
        }
        return this.luceneDoc   ;
    }



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
