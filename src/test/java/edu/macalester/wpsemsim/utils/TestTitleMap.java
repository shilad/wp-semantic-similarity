package edu.macalester.wpsemsim.utils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class TestTitleMap {

    @Test
    public void testBasicInts() {
        TitleMap<Integer> map = new TitleMap<Integer>(Integer.class);
        map.put("Lord of the rings", 2);
        map.put("Lord of the_rings", 4);
        assertEquals(map.get("Lord of_the_rings"), new Integer(4));
        map.increment("Lord_of the rings", 7);
        assertEquals(map.get("Lord of_the_rings"), new Integer(11));
        map.increment("Lord_of the Rings", 7);// different case
        assertEquals(map.get("Lord of_the_rings"), new Integer(11));
        assertEquals(map.get("Lord of_the_Rings"), new Integer(7));
    }
    @Test
    public void testBasicObjs() {
        TitleMap<List<String>> map = new TitleMap<List<String>>((Class<List<String>>) (Class<?>)ArrayList.class);
        map.get("Lord of the rings").add("foo");
        map.get("Lord of the_rings").add("bar");
        assertEquals(map.get("Lord of_the_rings").size(), 2);
        map.put("Lord_of the rings", new ArrayList<String>());
        map.get("Lord of the_rings").add("bar");
        assertEquals(map.get("Lord of_the_rings").size(), 1);
        map.get("Lord_of the Rings").add("soo");// different case
        assertEquals(map.get("Lord of_the_Rings").size(), 1);
    }
}
