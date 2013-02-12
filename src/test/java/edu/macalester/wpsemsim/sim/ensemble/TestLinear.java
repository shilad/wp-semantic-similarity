package edu.macalester.wpsemsim.sim.ensemble;

import org.junit.Test;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Map;


public class TestLinear {

    @Test
    public void testReadWriteEquation() throws IOException {
        PublicLinearEnsemble e = new PublicLinearEnsemble();
        Map<String, Double> coeffs = e.readEquation("+34.5");
        assert(coeffs.size() == 1);
        assertEquals(coeffs.get("C"), 34.5, 0.001);
        coeffs = e.readEquation("34.5 - 4*foo_bar");
        assert(coeffs.size() == 2);
        assertEquals(coeffs.get("C"), 34.5, 0.001);
        assertEquals(coeffs.get("foo_bar"), -4, 0.001);
        coeffs = e.readEquation("-34.5 - 3*bz2 + 7.35 * gar");
        assert(coeffs.size() == 3);
        assertEquals(coeffs.get("C"), -34.5, 0.001);
        assertEquals(coeffs.get("bz2"), -3, 0.001);;
        assertEquals(coeffs.get("gar"), 7.35, 0.001);
    }


    public class PublicLinearEnsemble extends LinearEnsemble {
        public PublicLinearEnsemble() throws IOException {
            super();
        }
    }
}
