package edu.macalester.wpsemsim.utils;

import gnu.trove.map.hash.TLongIntHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;

/**
 * A very space efficient map between article names and some other data.
 * Presumes that 8 byte hashes of article names are unique (they are, as of 2012).
 * Has a special case for handling ints that is backed by a trove long int map.
 */
public class TitleMap<V> {
    private boolean foldCase = true;
    private Class<V> valType;
    private TLongObjectHashMap<V> omap = null;
    private TLongIntHashMap imap = null;

    public TitleMap(Class<V> valType) {
        this(valType, false);
    }
    public TitleMap(Class<V> valType, boolean foldCase) {
        this.foldCase = foldCase;
        this.valType = valType;
        if (valType == Integer.class) {
            imap = new TLongIntHashMap();
        } else {
            omap = new TLongObjectHashMap();
        }
    }

    public synchronized V get(String title) {
        long h = titleHash(title);
        if (imap != null) {
            if (imap.containsKey(h)) {
                return (V)new Integer(imap.get(h));
            } else {
                imap.put(h, 0);
                return (V)new Integer(0);
            }
        } else {
            assert(omap != null);
            if (!omap.containsKey(h)) {
                try {
                    omap.put(h, valType.newInstance());
                } catch (InstantiationException e) {
                    throw new IllegalStateException(e);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
            return omap.get(h);
        }
    }


    public synchronized boolean containsKey(String title) {
        long h = titleHash(title);
        if (imap != null) {
            return (imap.containsKey(h));
        } else {
            assert(omap != null);
            return (omap.containsKey(h));
        }
    }

    public synchronized void put(String title, V val) {
        long h = titleHash(title);
        if (imap != null) {
            imap.put(h, ((Integer)val).intValue());
        } else {
            assert(omap != null);
            omap.put(h, val);
        }
    }

    public synchronized int increment(String title) {
        return increment(title, 1);
    }

    public synchronized int increment(String title, int amount) {
        assert(imap != null);
        long h = titleHash(title);
        return imap.adjustOrPutValue(h, amount, amount);
    }

    private long titleHash(String string) {
        string = string.replaceAll("_", " ");
        if (foldCase)
            string = string.toLowerCase();
        long h = 1125899906842597L; // prime
        int len = string.length();

        for (int i = 0; i < len; i++) {
            h = 31*h + string.charAt(i);
        }
        return h;
    }
}
