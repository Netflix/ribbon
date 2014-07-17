package com.netflix.ribbon.http.hystrix;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixObservableCommand;
import org.junit.Test;
import rx.Observable;
import rx.Observer;
import rx.subjects.ReplaySubject;
import rx.subjects.Subject;

import java.util.Iterator;

import static org.junit.Assert.*;

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
        Iterator<HystrixNotification<String>> iterator = commandChain.materializedNotificationObservable().toBlocking().getIterator();
        assertMaterializedIsSuccessful(iterator, "result1", command1);
    }

    @Test
    public void testMaterializedNotificationObservableLastOK() throws Exception {
        HystrixObservableCommandChain<String> commandChain = new HystrixObservableCommandChain<String>(errorCommand1, command2);
        Iterator<HystrixNotification<String>> iterator = commandChain.materializedNotificationObservable().toBlocking().getIterator();
        assertMaterializedIsSuccessful(iterator, "result2", command2);
    }

    @Test
    public void testMaterializedNotificationObservableError() throws Exception {
        HystrixObservableCommandChain<String> commandChain = new HystrixObservableCommandChain<String>(errorCommand1, errorCommand2);
        HystrixNotification<String> onError = commandChain.materializedNotificationObservable().toBlocking().single();

        assertTrue("onError notification expected", onError.isOnError());
        assertEquals(errorCommand2, onError.getHystrixObservableCommand());
    }

    @Test
    public void testNotificationObservableOK() throws Exception {
        HystrixObservableCommandChain<String> commandChain = new HystrixObservableCommandChain<String>(command1, errorCommand1);
        HystrixNotification<String> onNext = commandChain.toNotificationObservable().toBlocking().single();

        assertTrue("onNext notification expected", onNext.isOnNext());
        assertEquals("result1", onNext.getValue());
        assertEquals(command1, onNext.getHystrixObservableCommand());
    }

    @Test(expected = RuntimeException.class)
    public void testNotificationObservableError() throws Exception {
        HystrixObservableCommandChain<String> commandChain = new HystrixObservableCommandChain<String>(errorCommand1, errorCommand2);
        commandChain.toNotificationObservable().toBlocking().last();
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

    private void assertMaterializedIsSuccessful(Iterator<HystrixNotification<String>> iterator, String result, HystrixObservableCommand<String> expectedCommand) {
        HystrixNotification<String> onNext = iterator.next();
        HystrixNotification<String> onComplete = iterator.next();

        assertEquals(result, onNext.getValue());
        assertEquals(expectedCommand, onNext.getHystrixObservableCommand());
        assertTrue("expected onCompleted, and is " + onComplete.getKind(), onComplete.isOnCompleted());
        assertTrue("more elements than expected", !iterator.hasNext());
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
        protected Observable<String> run() {
            return null;
        }

        @Override
        public Observable<String> toObservable() {
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