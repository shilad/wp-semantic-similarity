package edu.macalester.wpsemsim.lucene;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Page {
    private int id;
    private String title;
    private String text;
    private int ns = 0;
    private String redirect = null;
    private String strippedText;
    private Document luceneDoc;

    public Page(int ns, int id, String redirect, String title, String text) {
        this.ns = ns;
        this.id = id;
        this.title = title;
        this.redirect = redirect;
        this.text = text;
        this.strippedText = MarkupStripper.stripEverything(text);
    }

    public int getId() {
        return id;
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
            d.add(new TextField("text", strippedText, Field.Store.YES));
            for (String l : getAnchorLinksWithoutFragments()) {
                d.add(new StringField("links", l, Field.Store.YES));
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
                for (String l : getDisambiguationLinksWithoutFragments()) {
                    d.add(new StringField("dab", l, Field.Store.YES));
                }
            } else {
                type = "normal";
            }
            d.add(new StringField("type", type, Field.Store.YES));
            this.luceneDoc = d;
        }
        return this.luceneDoc   ;
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
    public List<String> getAnchorLinksWithoutFragments() {
        return removeFragments(getAnchorLinks(text));
    }

    public List<String> getCategories() {
        List<String> cats = new ArrayList<String>();
        for (String link : getAnchorLinksWithoutFragments()) {
            if (link.toLowerCase().startsWith("category:")) {
                cats.add(link.substring("category:".length()));
            }
        }
        return cats;
    }

    private static final Pattern LINK_PATTERN = Pattern.compile("\\[\\[([^\\]]+?)\\]\\]");

    public static ArrayList<String> getAnchorLinks(String text) {
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
    public boolean isDisambiguation() {
        // look for dab phrases
        for (String p : DAB_BLACKLIST) {
            // TODO: accept whitespace around within curlies
            int i = text.toLowerCase().indexOf("{{" + p);

            // is this part of a phrase that is not a dab phrase?
            if (i >= 0) {
                for (String p2 : DAB_WHITELIST) {
                    if (text.substring(i).startsWith("{{" + p2)) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    public int getNumWordsInText() {
        return text.split("\\s+").length;
    }


    private static final Pattern DAB_LINK_PATTERN = Pattern.compile("\\*(?:[ \"']*)?\\s*\\[\\[([^\\]]+?)\\]\\](?:[ '\"]*)?");

    public List<String> getDisambiguationLinks() {
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
                    removeFragments(getAnchorLinks(
                            text.substring(0, earliest))));
        }
        return anchorLinks;
    }

    public List<String> getDisambiguationLinksWithoutFragments() {
        return removeFragments(getDisambiguationLinks());
    }
}
