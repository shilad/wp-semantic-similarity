package edu.macalester.wpsemsim.dictionary;

import org.apache.commons.io.FileUtils;

import java.io.*;
import java.util.List;

/**
 * Searches a dictionary as created by Google for the closest concept (WP page).
 *
 * See:
 * http://www-nlp.stanford.edu/pubs/crosswikis-data.tar.bz2/READ_ME.txt
 * http://www-nlp.stanford.edu/pubs/crosswikis-data.tar.bz2/
 *
 */
public class DictionarySearcher {
    private int bufferSize = 65536;
    private RandomAccessFile file;
    private long size;

    public DictionarySearcher(File file) throws FileNotFoundException {
        this.size = FileUtils.sizeOf(file);
        this.file = new RandomAccessFile(file, "r");
    }

    /**
     * Find the lines that start with the given key.
     * Adapted from http://blog.sarah-happy.ca/2010/04/binary-search-of-sorted-text-file.html
     * @param query
     * @return
     * @throws IOException
     */
    public synchronized List<String> find(String query) throws IOException {
        byte buffer[] = new byte[bufferSize];
        long beg = 0;
        long end = size;

        // find A line with the key.
        while (beg < end) {
            long mid = (end - beg) / 2;
            file.seek(mid);
            long len = Math.min(size - end, buffer.length);
        }

        /*
        * because we read the second line after each seek there is no way the
        * binary search will find the first line, so check it first.
        file.seek(0);
        String line = file.readLine();
        if (line == null || line.compareTo(target) >= 0) {
            return 0;
        }

        long beg = 0;
        long end = file.length();
        while (beg <= end) {
            long mid = beg + (end - beg) / 2;
            file.seek(mid);
            file.readLine();
            line = file.readLine();

            if (line == null || line.compareTo(target) >= 0) {
                end = mid - 1;
            } else {
                beg = mid + 1;
            }
        }

        file.seek(beg);
        file.readLine();
        return file.getFilePointer();
    */
        return null;
    }
}
