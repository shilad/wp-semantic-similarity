package edu.macalester.wpsemsim.utils;

import java.util.ArrayList;
import java.util.List;

public class SynchronizedBenchmark {
    public static final int NUM_THREADS = 30;
    public static final int NUM_ITERATIONS = 10000000;

    private Object lock = null;

    public static void main(String args[]) {
        long startTimeNano = System.nanoTime( );

        List<Integer> range = new ArrayList<Integer>();
        for (int i = 0; i < NUM_THREADS; i++) { range.add(i); }

        final SynchronizedBenchmark bench = new SynchronizedBenchmark();
        ParallelForEach.loop(range, NUM_THREADS, new Procedure<Integer>() {
            @Override
            public void call(Integer arg) throws Exception {
                bench.thread();
            }
        });
        long endtTimeNano = System.nanoTime( );
        System.out.println("ellapsed millis: " + (endtTimeNano - startTimeNano) / 1000000.0);
    }

    public void thread() {
        long z = 0;
        for (int i = 0; i < NUM_ITERATIONS; i++) {
            if (get().hashCode() > 0) {
                z++;
            }
        }
        System.out.println("z is " + z);
    }

    public  Object get() {
        if (lock == null) {
            lock = new Object();
        }
        return lock;
    }
}
