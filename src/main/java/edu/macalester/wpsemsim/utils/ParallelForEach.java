package edu.macalester.wpsemsim.utils;


import edu.macalester.wpsemsim.sim.ensemble.Example;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ParallelForEach {
    public static final Logger LOG = Logger.getLogger(ParallelForEach.class.getName());

    public static <T> void loop(
            Collection<T> collection,
            int numThreads,
            final Function<T> fn) {
        loop(collection, numThreads, fn, 50);
    }
    public static <T> void loop(
            Collection<T> collection,
            int numThreads,
            final Function<T> fn,
            final int logModulo) {
        final ExecutorService exec = Executors.newFixedThreadPool(numThreads);
        final CountDownLatch latch = new CountDownLatch(collection.size());
        try {
            // create a copy so that modifications to original list are safe
            final List<T> asList = new ArrayList<T>(collection);
            for (int i = 0; i < asList.size(); i++) {
                final int finalI = i;
                exec.submit(new Runnable() {
                    public void run() {
                        T obj = asList.get(finalI);
                        try {
                            if (finalI % logModulo == 0) {
                                LOG.info("processing list element " + finalI + " of " + asList.size());
                            }
                            fn.call(obj);
                        } catch (Exception e) {
                            LOG.log(Level.SEVERE, "error processing list element " + obj, e);
                            LOG.log(Level.SEVERE, "stacktrace: " + ExceptionUtils.getStackTrace(e).replaceAll("\n", " ").replaceAll("\\s+", " "));
                        } finally {
                            latch.countDown();
                        }
                    }});
            }
            latch.await();
        } catch (InterruptedException e) {
            LOG.log(Level.SEVERE, "Interrupted parallel for each", e);
            throw new RuntimeException(e);
        } finally {
            exec.shutdown();
        }

    }
}
