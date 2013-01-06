package edu.macalester.wpsemsim.normalize;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Test;

import java.io.*;
import java.util.Arrays;

import static org.junit.Assert.*;

public class TestNormalizer {
    @Test
    public void testPercent() {
        PercentileNormalizer p = new PercentileNormalizer();
        for (double x : Arrays.asList(1.0, 4.0, 3.2, 5.0, 7.9, 10.5, 11.2, 6.5)) {
            p.observe(x);
        }
        p.observationsFinished();
        assertEquals(p.normalize(1.0), 0.1111, 0.0001);
        assertEquals(p.normalize(11.2), 0.8888, 0.0001);
        assertTrue(p.normalize(0.0) > 0);
        assertTrue(p.normalize(0.0) < 0.111);
        assertTrue(p.normalize(0.01) > p.normalize(0.0));
        assertTrue(p.normalize(20) > 0.888888);
        assertTrue(p.normalize(20) < 1.0);
        assertTrue(p.normalize(20) < p.normalize(200));

        assertEquals(p.unnormalize(0.11111111), 1.0, 0.0001);
        assertEquals(p.unnormalize(0.111112), 1.0, 0.0001);
        assertEquals(p.unnormalize(0.37037), 4.0, 0.0001);
        assertEquals(p.unnormalize(0.88887), 11.2, 0.0001);
        assertEquals(p.unnormalize(0.88888888), 11.2, 0.0001);
    }

    @Test
    public void testPercent2() {
        PercentileNormalizer p = new PercentileNormalizer();
        for (double x : Arrays.asList(1.0, 1.0, 3.2, 5.0, 7.9, 10.5, 11.2, 6.5)) {
            p.observe(x);
        }
        p.observationsFinished();
    }

    @Test
    public void testPercentIO() throws IOException, ClassNotFoundException {
        PercentileNormalizer p = new PercentileNormalizer();
        for (double x : Arrays.asList(1.0, 4.0, 3.2, 5.0, 7.9, 10.5, 11.2, 6.5)) {
            p.observe(x);
        }
        p.observationsFinished();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(stream);
        out.writeObject(p);

        ByteArrayInputStream stream2 = new ByteArrayInputStream(stream.toByteArray());
        ObjectInputStream in = new ObjectInputStream(stream2);
        p = (PercentileNormalizer) in.readObject();

        assertEquals(p.normalize(1.0), 0.1111, 0.0001);
        assertEquals(p.normalize(11.2), 0.8888, 0.0001);
        assertTrue(p.normalize(0.0) > 0);
        assertTrue(p.normalize(0.0) < 0.111);
        assertTrue(p.normalize(0.01) > p.normalize(0.0));
        assertTrue(p.normalize(20) > 0.888888);
        assertTrue(p.normalize(20) < 1.0);
        assertTrue(p.normalize(20) < p.normalize(200));

        assertEquals(p.unnormalize(0.11111111), 1.0, 0.0001);
        assertEquals(p.unnormalize(0.111112), 1.0, 0.0001);
        assertEquals(p.unnormalize(0.37037), 4.0, 0.0001);
        assertEquals(p.unnormalize(0.88887), 11.2, 0.0001);
        assertEquals(p.unnormalize(0.88888888), 11.2, 0.0001);
    }
}