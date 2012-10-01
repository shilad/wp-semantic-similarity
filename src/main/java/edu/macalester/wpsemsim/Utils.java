package edu.macalester.wpsemsim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: shilad
 */
public class Utils {
    public static final Set<String> STOP_WORDS;
    static {
        STOP_WORDS = new HashSet<String>();
        STOP_WORDS.add("I"); STOP_WORDS.add("a"); STOP_WORDS.add("about");
        STOP_WORDS.add("an"); STOP_WORDS.add("are"); STOP_WORDS.add("as");
        STOP_WORDS.add("at"); STOP_WORDS.add("be"); STOP_WORDS.add("by");
        STOP_WORDS.add("com"); STOP_WORDS.add("de"); STOP_WORDS.add("en");
        STOP_WORDS.add("for"); STOP_WORDS.add("from"); STOP_WORDS.add("how");
        STOP_WORDS.add("in"); STOP_WORDS.add("is"); STOP_WORDS.add("it");
        STOP_WORDS.add("la"); STOP_WORDS.add("of"); STOP_WORDS.add("on");
        STOP_WORDS.add("or"); STOP_WORDS.add("that"); STOP_WORDS.add("the");
        STOP_WORDS.add("this"); STOP_WORDS.add("to"); STOP_WORDS.add("was");
        STOP_WORDS.add("what"); STOP_WORDS.add("when"); STOP_WORDS.add("where");
        STOP_WORDS.add("who"); STOP_WORDS.add("will"); STOP_WORDS.add("with");
        STOP_WORDS.add("and"); STOP_WORDS.add("the"); STOP_WORDS.add("www");
        STOP_WORDS.add("td"); STOP_WORDS.add("br"); STOP_WORDS.add("nbsp");
        STOP_WORDS.add("tr"); STOP_WORDS.add("p"); STOP_WORDS.add("nbsp");
    }

    public static String[] tokenize(String text, boolean stem) {
        Pattern pattern = Pattern.compile("\\w+");
        Matcher m = pattern.matcher(text);
        List<String> words = new ArrayList<String>();
        while (m.find()) {
            String word = m.group().toLowerCase();
            if (Utils.STOP_WORDS.contains(word)) {
                continue;
            }
            if (stem) {
                word = stem(word);
            }
            words.add(word);
        }
        return words.toArray(new String[0]);
    }

    public static String stem(String word) {
        Stemmer s = new Stemmer();
        s.add(word.toCharArray(), word.length());
        s.stem();
        return s.toString();
    }

}
