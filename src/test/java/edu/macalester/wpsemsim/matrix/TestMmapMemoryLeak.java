package edu.macalester.wpsemsim.matrix;


import edu.macalester.wpsemsim.utils.MemoryLeakVerifier;
import edu.macalester.wpsemsim.utils.TestUtils;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.MappedByteBuffer;

public class TestMmapMemoryLeak {
    static final int NUM_ROWS = 10000;
    static final int MAX_ROW_LENGTH = 1000;

    @Test
    public void testOnlyOnePageLoaded() throws IOException {
        SparseMatrix m = TestUtils.createTestMatrix(NUM_ROWS, MAX_ROW_LENGTH, false, NUM_ROWS * 20, false);
        SparseMatrixRow first = m.getRow(m.getRowIds()[0]); // force the first page to load.
        first = null;
        MappedByteBuffer buffer = m.buffers.get(0).buffer;
        MemoryLeakVerifier verifier = new MemoryLeakVerifier(buffer);
        assertNotNull(buffer);
        int i = 0;
        for (SparseMatrixRow row : m) {
            i++;
        }
        assertEquals(i, NUM_ROWS);
        buffer = m.buffers.get(0).buffer;
        assertNull(buffer); // only the last page should be loaded
        verifier.assertGarbageCollected("mapped byte buffer");
    }
}
