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
    private boolean redirect = false;
    private String strippedText;

    public Page(int ns, int id, boolean redirect, String title, String text) {
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
        return redirect;
    }

    public Document toLuceneDoc() {
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
        return d;
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
}
