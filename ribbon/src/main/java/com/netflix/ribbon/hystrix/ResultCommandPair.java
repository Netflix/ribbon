package com.netflix.ribbon.hystrix;

import com.netflix.hystrix.HystrixObservableCommand;

/**
 * @author Tomasz Bak
 */
public class ResultCommandPair<T> {
    private final T result;
    private final HystrixObservableCommand<T> command;

    public ResultCommandPair(T result, HystrixObservableCommand<T> command) {
        this.result = result;
        this.command = command;
    }

    public T getResult() {
        return result;
    }

    public HystrixObservableCommand<T> getCommand() {
        return command;
    }
}
