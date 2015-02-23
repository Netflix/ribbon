package com.netflix.ribbon.hystrix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import rx.Notification;
import rx.Observable;
import rx.Observer;
import rx.subjects.ReplaySubject;
import rx.subjects.Subject;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixObservableCommand;

/**
 * @author Tomasz Bak
 */
public class HystrixCommandChainTest {

    private TestableHystrixObservableCommand command1 = new TestableHystrixObservableCommand("result1");
    private TestableHystrixObservableCommand command2 = new TestableHystrixObservableCommand("result2");
    private TestableHystrixObservableCommand errorCommand1 = new TestableHystrixObservableCommand(new RuntimeException("error1"));
    private TestableHystrixObservableCommand errorCommand2 = new TestableHystrixObservableCommand(new RuntimeException("error2"));

    @Test
    public void testMaterializedNotificationObservableFirstOK() throws Exception {
        HystrixObservableCommandChain<String> commandChain = new HystrixObservableCommandChain<String>(command1, errorCommand1);
        ResultCommandPair<String> pair = commandChain.toResultCommandPairObservable().toBlocking().single();
        assertEquals("result1", pair.getResult());
        assertEquals("expected first hystrix command", command1, pair.getCommand());
    }

    @Test
    public void testMaterializedNotificationObservableLastOK() throws Exception {
        HystrixObservableCommandChain<String> commandChain = new HystrixObservableCommandChain<String>(errorCommand1, command2);
        ResultCommandPair<String> pair = commandChain.toResultCommandPairObservable().toBlocking().single();
        assertEquals("result2", pair.getResult());
        assertEquals("expected first hystrix command", command2, pair.getCommand());
    }

    @Test
    public void testMaterializedNotificationObservableError() throws Exception {
        HystrixObservableCommandChain<String> commandChain = new HystrixObservableCommandChain<String>(errorCommand1, errorCommand2);
        Notification<ResultCommandPair<String>> notification = commandChain.toResultCommandPairObservable().materialize().toBlocking().single();

        assertTrue("onError notification expected", notification.isOnError());
        assertEquals(errorCommand2, commandChain.getLastCommand());
    }

    @Test
    public void testObservableOK() throws Exception {
        HystrixObservableCommandChain<String> commandChain = new HystrixObservableCommandChain<String>(command1, errorCommand1);
        String value = commandChain.toObservable().toBlocking().single();
        assertEquals("result1", value);
    }

    @Test(expected = RuntimeException.class)
    public void testObservableError() throws Exception {
        HystrixObservableCommandChain<String> commandChain = new HystrixObservableCommandChain<String>(errorCommand1, errorCommand2);
        commandChain.toObservable().toBlocking().single();
    }

    private static final class TestableHystrixObservableCommand extends HystrixObservableCommand<String> {

        private final String[] values;
        private final Throwable error;

        private TestableHystrixObservableCommand(String... values) {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("test")));
            this.values = values;
            error = null;
        }

        private TestableHystrixObservableCommand(Throwable error) {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("test")));
            values = null;
            this.error = error;
        }

        @Override
        protected Observable<String> construct() {
            Subject<String, String> subject = ReplaySubject.create();
            fireEvents(subject);
            return subject;
        }

        public void fireEvents(Observer<String> observer) {
            if (values != null) {
                for (String v : values) {
                    observer.onNext(v);
                }
            }
            if (error == null) {
                observer.onCompleted();
            } else {
                observer.onError(error);
            }
        }
    }
}