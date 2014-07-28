package com.netflix.ribbon.hystrix;

import com.netflix.hystrix.HystrixObservableCommand;
import rx.Observable;
import rx.functions.Func1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class implements chaining mechanism for Hystrix commands. If a command in a chain fails, the next one
 * is run. If all commands in the chain failed, the error from the last one is reported.
 * To be able to identify the Hystrix command for which request was executed, an {@link ResultCommandPair} event
 * stream is returned by {@link #toResultCommandPairObservable()} method.
 * If information about Hystrix command is not important, {@link #toObservable()} method shall be used, which
 * is more efficient.
 *
 * @author Tomasz Bak
 */
public class HystrixObservableCommandChain<T> {

    private final List<HystrixObservableCommand<T>> hystrixCommands;

    public HystrixObservableCommandChain(List<HystrixObservableCommand<T>> hystrixCommands) {
        this.hystrixCommands = hystrixCommands;
    }

    public HystrixObservableCommandChain(HystrixObservableCommand<T>... commands) {
        hystrixCommands = new ArrayList<HystrixObservableCommand<T>>(commands.length);
        Collections.addAll(hystrixCommands, commands);
    }

    public Observable<ResultCommandPair<T>> toResultCommandPairObservable() {
        Observable<ResultCommandPair<T>> rootObservable = null;
        for (final HystrixObservableCommand<T> command : hystrixCommands) {
            Observable<ResultCommandPair<T>> observable = command.toObservable().map(new Func1<T, ResultCommandPair<T>>() {
                @Override
                public ResultCommandPair<T> call(T result) {
                    return new ResultCommandPair<T>(result, command);
                }
            });
            rootObservable = rootObservable == null ? observable : rootObservable.onErrorResumeNext(observable);
        }
        return rootObservable;
    }

    public Observable<T> toObservable() {
        Observable<T> rootObservable = null;
        for (final HystrixObservableCommand<T> command : hystrixCommands) {
            Observable<T> observable = command.toObservable();
            rootObservable = rootObservable == null ? observable : rootObservable.onErrorResumeNext(observable);
        }
        return rootObservable;
    }

    public List<HystrixObservableCommand<T>> getCommands() {
        return Collections.unmodifiableList(hystrixCommands);
    }

    public HystrixObservableCommand getLastCommand() {
        return hystrixCommands.get(hystrixCommands.size() - 1);
    }
}
