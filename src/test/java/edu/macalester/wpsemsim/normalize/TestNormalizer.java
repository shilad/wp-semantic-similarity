package edu.macalester.wpsemsim.normalize;

import org.junit.Test;

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
}
