package edu.macalester.wpsemsim.concepts;

import com.sleepycat.je.DatabaseException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class TestDictionaryDatabase {
    private DictionaryDatabase db;
    private File dbPath;

    @Before
    public void setUp() throws IOException, DatabaseException {
        this.dbPath = File.createTempFile("concepts-db", null);
        dbPath.deleteOnExit();
        db = new DictionaryDatabase(dbPath, true);
        db.put(new DictionaryEntry("foo bar BLAH hi\t0.1 aa blah ah "), true);
        db.put(new DictionaryEntry(" Foo bar $$BLAH hi\t0.2 bbbb blah ah "), true);
        db.put(new DictionaryEntry(" Foo bar BLAH hi\t0.7 cc blah ah "), true);
        db.put(new DictionaryEntry(" BAZ \t0.7 cc blah ah "), true);
    }

    @After
    public void tearDown() throws DatabaseException {
        db.close();
        dbPath.delete();
    }

    @Test
    public void testBasic() throws DatabaseException {
        List<DictionaryEntry> entries = db.get("Foo BAR BLAH$ hi ");
        assertEquals(entries.size(), 3);
        entries = db.get("baz");
        assertEquals(entries.size(), 1);
    }
}
