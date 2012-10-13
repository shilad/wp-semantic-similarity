package edu.macalester.wpsemsim.matrix;

import org.apache.commons.lang3.ArrayUtils;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
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
    IntBuffer headerBuffer;
    IntBuffer idBuffer;
    ByteBuffer valBuffer;

    public SparseMatrixRow(int rowIndex, LinkedHashMap<Integer, Float> row) {
        this(rowIndex,
            ArrayUtils.toPrimitive(row.keySet().toArray(new Integer[] {})),
            ArrayUtils.toPrimitive(row.values().toArray(new Float[] {}))
        );
    }

    public SparseMatrixRow(int rowIndex, int colIds[], float colVals[]) {
        byte byteVals[] = new byte[colVals.length];
        for (int i = 0; i < colVals.length; i++) {
            byteVals[i] = scoreToByte(colVals[i]);
        }
        createBuffer(rowIndex, colIds, byteVals);
    }

    public SparseMatrixRow(int rowIndex, int colIds[], byte colVals[]) {
        createBuffer(rowIndex, colIds, colVals);
    }

    public void createBuffer(int rowIndex, int colIds[], byte colVals[]) {
        assert(colIds.length == colVals.length);

        buffer = ByteBuffer.allocateDirect(
                4 +                 // header
                4 +                 // row index
                4 +                 // num cols
                4 * colVals.length +    // col indexes
                1 * colVals.length      // col values
        );
        createViewBuffers(colVals.length);

        headerBuffer.put(0, HEADER);
        headerBuffer.put(1, rowIndex);
        headerBuffer.put(2, colVals.length);
        idBuffer.put(colIds, 0, colIds.length);
        valBuffer.put(colVals, 0, colVals.length);
    }

    private void createViewBuffers(int numColumns) {
        buffer.position(0);
        headerBuffer = buffer.asIntBuffer();
        buffer.position(3 * 4);
        idBuffer = buffer.asIntBuffer();
        buffer.position(3 * 4 + numColumns * 4);
        valBuffer = buffer.slice();
    }

    public SparseMatrixRow(ByteBuffer buffer) {
        this.buffer = buffer;
        if (this.buffer.getInt(0) != HEADER) {
            throw new IllegalArgumentException("Invalid header in byte buffer");
        }
        createViewBuffers(buffer.getInt(8));
    }

    public final int getColIndex(int i) {
        return idBuffer.get(i);
    }

    public final float getColValue(int i) {
        return byteToScore(valBuffer.get(i));
    }

    public final byte getColValueAsByte(int i) {
        return valBuffer.get(i);
    }

    public final int getRowIndex() {
        return headerBuffer.get(1);
    }

    public final int getNumCols() {
        return headerBuffer.get(2);
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

    public static final float byteToScore(byte b) {
        float f = (1.0f * (b - Byte.MIN_VALUE) / BYTE_RANGE) * SCORE_RANGE + MIN_SCORE;
        assert(MIN_SCORE <= f && f <= MAX_SCORE);
        return f;
    }

    public static final byte scoreToByte(float s) {
        float normalized = (s - MIN_SCORE) / SCORE_RANGE;
        return (byte)(normalized * BYTE_RANGE + Byte.MIN_VALUE);
    }
}
