package edu.macalester.wpsemsim;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntLongHashMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SparseMatrix {
    public static final Logger LOG = Logger.getLogger(SparseMatrix.class.getName());

    public static int MAX_PAGE_SIZE = Integer.MAX_VALUE;

    public static final int FILE_HEADER = 0xabcdef;
    public static final byte ROW_PADDING = Byte.MIN_VALUE;

    private TIntLongHashMap rowOffsets = new TIntLongHashMap();
    private int rowIds[];
    private FileChannel channel;
    private File path;

    private List<MappedByteBuffer> buffers = new ArrayList<MappedByteBuffer>();
    private List<Long> bufferOffsets = new ArrayList<Long>();

    public SparseMatrix(File path) throws IOException {
        this.path = path;
        info("initializing sparse matrix with file length " + FileUtils.sizeOf(path));
        this.channel = (new FileInputStream(path)).getChannel();
        readOffsets();
        pageInRows();
    }

    private void readOffsets() throws IOException {
        long size = Math.min(channel.size(), MAX_PAGE_SIZE);
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
            if (pos - startPos > MAX_PAGE_SIZE) {
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
        buffers.add(channel.map(FileChannel.MapMode.READ_ONLY, startPos, length));
        bufferOffsets.add(startPos);
    }

    public SparseMatrixRow getRow(int rowId) {
        long targetOffset = rowOffsets.get(rowId);
        for (int i = 0; i < buffers.size(); i++) {
            MappedByteBuffer buffer = buffers.get(i);
            long bufferOffset = bufferOffsets.get(i);
            if (targetOffset < bufferOffset + buffer.limit()) {
                buffer.position((int) (targetOffset - bufferOffset));
                return new SparseMatrixRow(buffer.slice());
            }
        }
        throw new AssertionError("did not find row " + rowId + " with offset " + targetOffset);
    }

    public int[] getRowIds() {
        return rowIds;
    }

    public int getNumRows() {
        return rowIds.length;
    }

    public static void write(File file, Iterator<SparseMatrixRow> rows) throws IOException {
        LOG.info("writing rows to " + file);
        TIntLongHashMap rowOffsets = new TIntLongHashMap();
        TIntArrayList rowIndexes = new TIntArrayList();

        // write tmp matrix file
        File tmp = File.createTempFile("matrix", null);
//        tmp.deleteOnExit();
        BufferedOutputStream w = new BufferedOutputStream(new FileOutputStream(tmp));

        long offset = 0;
        while (rows.hasNext()) {
            SparseMatrixRow row = rows.next();
            rowOffsets.put(row.getRowIndex(), offset);
            rowIndexes.add(row.getRowIndex());

            row.getBuffer().rewind();
            byte[] bytes = new byte[row.getBuffer().remaining()];
            row.getBuffer().get(bytes, 0, bytes.length);
            w.write(bytes);
            offset += bytes.length;

            // pad rows to 8 byte offsets to speed things up.
            while (offset % 8 != 0) {
                offset++;
                w.write(ROW_PADDING);
            }
        }
        w.close();

        // write offset file
        int sizeHeader = 8 + rowOffsets.size() * 12;
        w = new BufferedOutputStream(new FileOutputStream(file));
        w.write(intToBytes(FILE_HEADER));
        w.write(intToBytes(rowOffsets.size()));
        for (int i = 0; i < rowIndexes.size(); i++) {
            int rowIndex = rowIndexes.get(i);
            long rowOffset = rowOffsets.get(rowIndex);
            w.write(intToBytes(rowIndex));
            w.write(longToBytes(rowOffset + sizeHeader));
        }

        InputStream r = new FileInputStream(tmp);

        // append other file
        IOUtils.copyLarge(r, w);
        r.close();
        w.flush();
        w.close();

        LOG.info("wrote " + FileUtils.sizeOf(file) + " bytes to " + file);
    }

    public void dump() {
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


    private void info(String message) {
        LOG.log(Level.INFO, "sparse matrix " + path + ": " + message);
    }

    private void debug(String message) {
        LOG.log(Level.FINEST, "sparse matrix " + path + ": " + message);
    }

    private static byte[] intToBytes(int i) {
        return ByteBuffer.allocate(4).putInt(i).array();
    }

    private static byte[] longToBytes(long i) {
        return ByteBuffer.allocate(8).putLong(i).array();
    }
}
