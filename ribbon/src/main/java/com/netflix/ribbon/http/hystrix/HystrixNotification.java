package com.netflix.ribbon.http.hystrix;

import com.netflix.hystrix.HystrixObservableCommand;
import rx.Notification.Kind;

import java.util.concurrent.ExecutionException;

/**
 * Equivalent to RxJava {@link rx.Notification}, that holds additionally information
 * about Hystrix command related to this notification. Implemented from scratch since
 * {@link rx.Notification} cannot be extended.
 *
 * @author Tomasz Bak
 */
public class HystrixNotification<T> {
    private final Kind kind;
    private final Throwable throwable;
    private final T value;
    private final HystrixObservableCommand<T> hystrixObservableCommand;

    public static <T> HystrixNotification<T> createOnNext(T t, HystrixObservableCommand<T> command) {
        return new HystrixNotification<T>(Kind.OnNext, null, t, command);
    }

    public static <T> HystrixNotification<T> createOnError(Throwable e, HystrixObservableCommand<T> command) {
        return new HystrixNotification<T>(Kind.OnError, e, null, command);
    }

    public static <T> HystrixNotification<T> createOnCompleted(HystrixObservableCommand<T> command) {
        return new HystrixNotification<T>(Kind.OnCompleted, null, null, command);
    }

    HystrixNotification(Kind kind, Throwable throwable, T value, HystrixObservableCommand<T> hystrixObservableCommand) {
        this.kind = kind;
        this.throwable = throwable;
        this.value = value;
        this.hystrixObservableCommand = hystrixObservableCommand;
    }

    public Kind getKind() {
        return kind;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public T getValue() {
        return value;
    }

    public boolean isOnError() {
        return getKind() == Kind.OnError;
    }

    public boolean isOnCompleted() {
        return getKind() == Kind.OnCompleted;
    }

    public boolean isOnNext() {
        return getKind() == Kind.OnNext;
    }

    public HystrixObservableCommand<T> getHystrixObservableCommand() {
        return hystrixObservableCommand;
    }

    public T toFutureResult() throws ExecutionException {
        if (isOnNext()) {
            return value;
        }
        if (isOnCompleted()) {
            throw new ExecutionException("no element returned by observable", null);
        }
        // onError
        throw new ExecutionException(throwable);
    }
}
