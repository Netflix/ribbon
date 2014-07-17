package com.netflix.ribbon.http.hystrix;

import com.netflix.hystrix.HystrixObservableCommand;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.functions.Func1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class implements chaining mechanism for Hystrix commands. If a command in a chain fails, the next one
 * is run. If all commands in the chain failed, the error from the last one is reported.
 * To be able to identify the Hystrix command for which request was executed, a materialized
 * {@link com.netflix.ribbon.http.hystrix.HystrixNotification} event stream is returned by
 * {@link #materializedNotificationObservable()} method. For convinvience this stream is mapped by:
 * <ul>
 *     <li>{@link #toObservable()} - value of T observable (no access to Hystrix information)</li>
 *     <li>{@link #toNotificationObservable()} -
 *         does not encapsulate error in {@link com.netflix.ribbon.http.hystrix.HystrixNotification}, but calls
 *         onError directly (we need it for mapping to future from observable).
 *     </li>
 * </ul>
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

    public Observable<HystrixNotification<T>> materializedNotificationObservable() {
        return Observable.create(new OnSubscribe<HystrixNotification<T>>() {
            @Override
            public void call(Subscriber<? super HystrixNotification<T>> subscriber) {
                new HystrixExecutor(subscriber).tryAt(0);
            }
        });
    }

    public Observable<HystrixNotification<T>> toNotificationObservable() {
        return materializedNotificationObservable().flatMap(new Func1<HystrixNotification<T>, Observable<HystrixNotification<T>>>() {
            @Override
            public Observable<HystrixNotification<T>> call(HystrixNotification<T> notification) {
                if (notification.isOnNext()) {
                    return Observable.just(notification);
                }
                if (notification.isOnCompleted()) {
                    return Observable.empty();
                }
                return Observable.error(notification.getThrowable());
            }
        });
    }

    public Observable<T> toObservable() {
        return materializedNotificationObservable().flatMap(new Func1<HystrixNotification<T>, Observable<? extends T>>() {
            @Override
            public Observable<? extends T> call(HystrixNotification<T> notification) {
                if (notification.isOnNext()) {
                    return Observable.just(notification.getValue());
                }
                if (notification.isOnError()) {
                    return Observable.error(notification.getThrowable());
                }
                // Must be onCompleted
                return Observable.empty();
            }
        });
    }

    public List<HystrixObservableCommand<T>> getCommands() {
        return Collections.unmodifiableList(hystrixCommands);
    }

    private class HystrixExecutor {
        private final Subscriber<? super HystrixNotification<T>> subscriber;

        public HystrixExecutor(Subscriber<? super HystrixNotification<T>> subscriber) {
            this.subscriber = subscriber;
        }

        public void tryAt(final int commandIndex) {
            final HystrixObservableCommand<T> command = hystrixCommands.get(commandIndex);

            command.toObservable().subscribe(new Subscriber<T>() {
                @Override
                public void onNext(T t) {
                    subscriber.onNext(HystrixNotification.createOnNext(t, command));
                }

                @Override
                public void onCompleted() {
                    subscriber.onNext(HystrixNotification.createOnCompleted(command));
                    subscriber.onCompleted();
                }

                @Override
                public void onError(Throwable e) {
                    if ((commandIndex + 1) < hystrixCommands.size()) {
                        tryAt(commandIndex + 1);
                    } else {
                        subscriber.onNext(HystrixNotification.createOnError(e, command));
                        subscriber.onCompleted();
                    }
                }
            });
        }
    }
}
