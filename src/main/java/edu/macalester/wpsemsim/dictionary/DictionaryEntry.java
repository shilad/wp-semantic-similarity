package edu.macalester.wpsemsim.dictionary;

import org.apache.commons.lang3.math.Fraction;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DictionaryEntry {
    private String text;
    private float fraction;
    private String article;
    private String flags[];

    // Format: text + "\t" + probability + " " + url + " " + flags...
    private static final Pattern MATCH_ENTRY =
            Pattern.compile("([^\t]*)\t([0-9.e-]+) ([^ ]*) (.*)");

    public DictionaryEntry(String line) {
        Matcher m = MATCH_ENTRY.matcher(line);
        if (!m.matches()) {
            throw new IllegalArgumentException("invalid dictionary entry: '" + line + "'");
        }
        this.text = m.group(1);
        this.fraction = Float.valueOf(m.group(2));
        this.article = m.group(3);
        this.flags = m.group(4).split(" ");
    }

    public String getText() {
        return text;
    }

    public float getFraction() {
        return fraction;
    }

    public String getArticle() {
        return article;
    }

    public String[] getFlags() {
        return flags;
    }

    public Fraction getFractionEnglishQueries() {
        for (String f : flags) {
            if (f.startsWith("W:")) {
                return Fraction.getFraction(f.substring(2));
            }
        }
        return null;
    }
}
