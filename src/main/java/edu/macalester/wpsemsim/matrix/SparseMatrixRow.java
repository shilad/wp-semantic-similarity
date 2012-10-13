package edu.macalester.wpsemsim.matrix;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

public final class SparseMatrixRow {
    Logger LOG = Logger.getLogger(SparseMatrixRow.class.getName());
    public static final Float MIN_SCORE = -1.1f;
    public static final Float MAX_SCORE = 1.1f;

    public static final Float SCORE_RANGE = (MAX_SCORE - MIN_SCORE);
    public static final int BYTE_RANGE = (Byte.MAX_VALUE - Byte.MIN_VALUE);

    public static final int HEADER = 0xfefefefe;

    ByteBuffer buffer;

    public SparseMatrixRow(int rowIndex, LinkedHashMap<Integer, Float> row) {
        buffer = ByteBuffer.allocateDirect(
                4 +                 // header
                4 +                 // row index
                4 +                 // num cols
                4 * row.size() +    // col indexes
                1 * row.size()      // col values
        );
        buffer.putInt(0, HEADER);
        buffer.putInt(4, rowIndex);
        buffer.putInt(8, row.size());
        int i = 0;
        for (Map.Entry<Integer, Float> entry : row.entrySet()) {
            buffer.putInt(12 + 4 * i, entry.getKey());
            int val = -1;
            if (entry.getValue() > MAX_SCORE) {
                LOG.info("truncating out of range score: " + entry.getValue());
                val = Byte.MAX_VALUE;
            } else if (entry.getValue() < MIN_SCORE) {
                LOG.info("truncating out of range score: " + entry.getValue());
                val = Byte.MIN_VALUE;
            } else {
                float normalized = (entry.getValue() - MIN_SCORE) / SCORE_RANGE;
                val = (byte)(normalized * BYTE_RANGE + Byte.MIN_VALUE);
            }
            assert(Byte.MIN_VALUE <= val && val <= Byte.MAX_VALUE);
            buffer.put(12 + 4 * row.size() + i, (byte) val);
            i++;
        }
    }

    public SparseMatrixRow(ByteBuffer buffer) {
        this.buffer = buffer;
        if (this.buffer.getInt(0) != HEADER) {
            throw new IllegalArgumentException("Invalid header in byte buffer");
        }
    }

    public final int getColIndex(int i) {
        return buffer.getInt(12 + 4 * i);
    }

    public final float getColValue(int i) {
        byte b = buffer.get(12 + 4 * getNumCols() + i);
        float f = (1.0f * (b - Byte.MIN_VALUE) / BYTE_RANGE) * SCORE_RANGE + MIN_SCORE;
        assert(MIN_SCORE <= f && f <= MAX_SCORE);
        return f;
    }

    public final int getRowIndex() {
        return buffer.getInt(4);
    }

    public final int getNumCols() {
        return buffer.getInt(8);
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }

    public LinkedHashMap<Integer, Float> asMap() {
        LinkedHashMap<Integer, Float> result = new LinkedHashMap<Integer, Float>();
        for (int i = 0; i < getNumCols(); i++) {
            result.put(getColIndex(i), getColValue(i));
        }
        return result;
    }
}
