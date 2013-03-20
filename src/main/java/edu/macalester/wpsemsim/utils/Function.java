package edu.macalester.wpsemsim.utils;

public interface Function<T> {
    /**
     * Call the function. If an exception occurs, it must be handled by the caller.
     * @param arg
     * @throws Exception
     */
    public void call(T arg) throws Exception;
}
