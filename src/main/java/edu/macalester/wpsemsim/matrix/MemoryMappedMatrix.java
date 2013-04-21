package edu.macalester.wpsemsim.matrix;

import gnu.trove.map.hash.TIntLongHashMap;
import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * A wrapper around a file channel that contains a matrix in row major order.
 * Returns rows at a particular offset in the form of ByteBuffers backed by a memory mapped file.
 */
public class MemoryMappedMatrix {
    public static final Logger LOG = Logger.getLogger(MemoryMappedMatrix.class.getName());

    private int maxPageSize;
    private boolean loadAllPages = true;
    private TIntLongHashMap rowOffsets = new TIntLongHashMap();
    private FileChannel channel;
    protected List<MappedBufferWrapper> buffers = new ArrayList<MappedBufferWrapper>();
    private File path;

    public MemoryMappedMatrix(File path, FileChannel channel,TIntLongHashMap rowOffsets,
                              boolean loadAllPages, int maxPageSize) throws IOException {
        this.path = path;
        this.channel = channel;
        this.rowOffsets = rowOffsets;
        this.loadAllPages = loadAllPages;
        this.maxPageSize = maxPageSize;
        pageInRows();
    }

    private void pageInRows() throws IOException {
        int rowIds[] = getRowIdsInOrder();

        // tricky: pages must align with row boundaries
        long startPos = rowOffsets.get(rowIds[0]);
        long lastPos = startPos;

        for (int i = 1; i < rowIds.length; i++) {
            long pos = rowOffsets.get(rowIds[i]);
            if (pos - startPos > maxPageSize) {
                assert(lastPos != startPos);
                addBuffer(startPos, lastPos);
                startPos = lastPos;
            }
            lastPos = pos;
        }
        addBuffer(startPos, channel.size());
    }


    private void addBuffer(long startPos, long endPos) throws IOException {
        long length = endPos - startPos;
        info("adding page at " + startPos + " of length " + length);
        buffers.add(new MappedBufferWrapper(channel, startPos, endPos));
    }

    public ByteBuffer getRow(int rowId) throws IOException {
        if (!rowOffsets.containsKey(rowId)) {
            return null;
        }
        long targetOffset = rowOffsets.get(rowId);
        ByteBuffer row = null;
        for (int i = 0; i < buffers.size(); i++) {
            MappedBufferWrapper wrapper = buffers.get(i);
            if (wrapper.start <= targetOffset && targetOffset < wrapper.end) {
                row = wrapper.get(targetOffset);
            } else if (!loadAllPages) {
                wrapper.close();
            }
        }
        if (row == null) {
            throw new IllegalArgumentException("did not find row " + rowId + " with offset " + targetOffset);
        } else {
            return row;
        }
    }

    static class MappedBufferWrapper {
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

    private int[] getRowIdsInOrder() {
        Integer rowIds[] = ArrayUtils.toObject(rowOffsets.keys());
        Arrays.sort(rowIds, new Comparator<Integer>() {
            @Override
            public int compare(Integer r1, Integer r2) {
                return new Long(rowOffsets.get(r1)).compareTo(rowOffsets.get(r2));
            }
        });
        return ArrayUtils.toPrimitive(rowIds);
    }

    private void info(String message) {
        LOG.log(Level.INFO, "sparse matrix " + path + ": " + message);
    }

}
