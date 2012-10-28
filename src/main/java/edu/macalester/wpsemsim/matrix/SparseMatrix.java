package edu.macalester.wpsemsim.matrix;

import gnu.trove.map.hash.TIntLongHashMap;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SparseMatrix implements Iterable<SparseMatrixRow> {
    public static final Logger LOG = Logger.getLogger(SparseMatrix.class.getName());

    public static int DEFAULT_MAX_PAGE_SIZE = Integer.MAX_VALUE;
    public static boolean DEFAULT_LOAD_ALL_PAGES = true;

    public static final int FILE_HEADER = 0xabcdef;

    public int maxPageSize = DEFAULT_MAX_PAGE_SIZE;
    private boolean loadAllPages = true;
    private TIntLongHashMap rowOffsets = new TIntLongHashMap();
    private int rowIds[];
    private FileChannel channel;
    private File path;

    protected List<MappedBufferWrapper> buffers = new ArrayList<MappedBufferWrapper>();

    public SparseMatrix(File path) throws IOException {
        this(path, DEFAULT_LOAD_ALL_PAGES, DEFAULT_MAX_PAGE_SIZE);
    }

    public SparseMatrix(File path, boolean loadAllPages, int maxPageSize) throws IOException {
        this.path = path;
        this.loadAllPages = loadAllPages;
        this.maxPageSize = maxPageSize;
        info("initializing sparse matrix with file length " + FileUtils.sizeOf(path));
        this.channel = (new FileInputStream(path)).getChannel();
        readOffsets();
        pageInRows();
    }

    private void readOffsets() throws IOException {
        long size = Math.min(channel.size(), maxPageSize);
        MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, size);
        if (buffer.getInt(0) != FILE_HEADER) {
            throw new IOException("invalid file header: " + buffer.getInt(0));
        }
        int numRows = buffer.getInt(4);
        info("reading offsets for " + numRows + " rows");
        rowIds = new int[numRows];
        for (int i = 0; i < numRows; i++) {
            int pos = 8 + 12 * i;
            int rowIndex = buffer.getInt(pos);
            long rowOffset = buffer.getLong(pos + 4);
            rowOffsets.put(rowIndex, rowOffset);
//            debug("adding row index " + rowIndex + " at offset " + rowOffset);
            rowIds[i] = rowIndex;
        }
        info("read " + numRows + " offsets");
    }

    private void pageInRows() throws IOException {
        // tricky: pages must align with row boundaries
        long startPos = rowOffsets.get(rowIds[0]);
        long lastPos = startPos;

        for (int i = 1; i < getNumRows(); i++) {
            long pos = rowOffsets.get(rowIds[i]);
            if (pos - startPos > maxPageSize) {
                assert(lastPos != startPos);
                addBuffer(startPos, lastPos);
                startPos = lastPos;
            }
            lastPos = pos;
        }
        addBuffer(startPos, FileUtils.sizeOf(path));
    }

    private void addBuffer(long startPos, long endPos) throws IOException {
        long length = endPos - startPos;
        info("adding page at " + startPos + " of length " + length);
        buffers.add(new MappedBufferWrapper(channel, startPos, endPos));
    }

    public SparseMatrixRow getRow(int rowId) throws IOException {
        SparseMatrixRow row = null;
        long targetOffset = rowOffsets.get(rowId);
        for (int i = 0; i < buffers.size(); i++) {
            MappedBufferWrapper wrapper = buffers.get(i);
            if (wrapper.start <= targetOffset && targetOffset < wrapper.end) {
                row = new SparseMatrixRow(wrapper.get(targetOffset));
            } else if (!loadAllPages) {
                wrapper.close();
            }
        }
        if (row == null) {
            throw new AssertionError("did not find row " + rowId + " with offset " + targetOffset);
        } else {
            return row;
        }
    }

    public int[] getRowIds() {
        return rowIds;
    }

    public int getNumRows() {
        return rowIds.length;
    }

    public void dump() throws IOException {
        for (int id : rowIds) {
            System.out.print("" + id + ": ");
            SparseMatrixRow row = getRow(id);
            for (int i = 0; i < row.getNumCols(); i++) {
                int id2 = row.getColIndex(i);
                float val = row.getColValue(i);
                System.out.print(" " + id2 + "=" + val);
            }
            System.out.println();
        }
    }

    @Override
    public Iterator<SparseMatrixRow> iterator() {
        return new SparseMatrixIterator();
    }

    public class SparseMatrixIterator implements Iterator<SparseMatrixRow> {
        private int i = 0;
        @Override
        public boolean hasNext() {
            return i < rowIds.length;
        }
        @Override
        public SparseMatrixRow next() {
            try {
                return getRow(rowIds[i++]);
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "getRow failed", e);
                return null;
            }
        }
        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    class MappedBufferWrapper {
        FileChannel channel;
        MappedByteBuffer buffer;
        long start;
        long end;

        public MappedBufferWrapper(FileChannel channel, long start, long end) {
            this.channel = channel;
            this.start = start;
            this.end = end;
        }
        public synchronized ByteBuffer get(long position) throws IOException {
            if (buffer == null) {
                buffer = channel.map(FileChannel.MapMode.READ_ONLY, start, end - start);
            }
            buffer.position((int) (position - start));
            return buffer.slice();
        }
        public synchronized void close() {
            buffer = null;
        }
    }


    private void info(String message) {
        LOG.log(Level.INFO, "sparse matrix " + path + ": " + message);
    }

    private void debug(String message) {
        LOG.log(Level.FINEST, "sparse matrix " + path + ": " + message);
    }

}
