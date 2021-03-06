package edu.macalester.wpsemsim.concepts;

import org.apache.commons.lang3.math.Fraction;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestDictionaryEntry {

    @Test
    public void testParser() {
        DictionaryEntry e1 = new DictionaryEntry(" band\t0 Pru_(Thai_band) KB RWB W08 W09 WDB f");
        System.err.println("flags are: " + Arrays.toString(e1.getFlags()));
        assertEquals(e1.getText(), " band");
        assertEquals(e1.getFlags().length, 6);
        assertEquals(e1.getFlags()[1], "RWB");
        assertEquals(e1.getFraction(), 0.0, 0.00001);
        assertEquals(e1.getArticle(), "Pru_(Thai_band)");
        assertNull(e1.getFractionEnglishLinks());


        DictionaryEntry e2 = new DictionaryEntry("Personal life\t1.39776e-05 Paul_Kariya KB W:1/71421 W08 W09 WDB");
        assertEquals(e2.getText(), "Personal life");
        assertEquals(e2.getFlags().length, 5);
        assertEquals(e2.getFlags()[1], "W:1/71421");
        assertEquals(e2.getFraction(), 1.39776e-05, 0.00001);
        assertEquals(e2.getArticle(), "Paul_Kariya");
        assertEquals(e2.getFractionEnglishLinks(), Fraction.getFraction(1, 71421));
    }

    @Test
    public void testNormalizer() {
        DictionaryEntry e1 = new DictionaryEntry("foo bar baz!! BLAH hi\t0 $$ blah ah ");
        DictionaryEntry e2 = new DictionaryEntry(" fOo bar   baz BLaH hi\t0 $$ blah ah ");
        DictionaryEntry e3 = new DictionaryEntry("!fOo  bar baz BLaH hi\t0 $$ blah ah ");
        assertEquals(e1.getNormalizedText(), e2.getNormalizedText());
        assertEquals(e1.getNormalizedText(), e3.getNormalizedText());
    }


}
