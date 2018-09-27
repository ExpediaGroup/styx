package com.hotels.styx.api.stream;

import com.hotels.styx.api.Buffer;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observable;
import rx.observers.TestSubscriber;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class DiscardOperatorTest {

    private TestSubscriber<? super Buffer> testSubscriber;
    private Buffer buffer1;
    private Buffer buffer2;

    @BeforeMethod
    public void setUp() {
        testSubscriber = new TestSubscriber<>();
        buffer1 = new Buffer("x", UTF_8);
        buffer2 = new Buffer("y", UTF_8);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void allowsOnlyOneDiscardOperation() {
        RxContentPublisher upstream = new RxContentPublisher(Observable.just(new Buffer("x", UTF_8)));
        DiscardOperator discard = new DiscardOperator(upstream);

        discard.apply();
        discard.apply();
    }

    @Test
    public void discardsZeroBuffers() {
        DiscardOperator discard = new DiscardOperator(new RxContentPublisher(Observable.empty()));

        Publisher<Buffer> result = discard.apply();

        new RxContentConsumer(result).consume().subscribe(testSubscriber);

        assertThat(testSubscriber.getOnNextEvents(), is(empty()));

        assertThat(testSubscriber.getOnNextEvents().size(), is(0));
        assertThat(testSubscriber.getOnErrorEvents().size(), is(0));
        assertThat(testSubscriber.getOnCompletedEvents().size(), is(1));
    }

    @Test
    public void discardsOneBuffer() {
        DiscardOperator discard = new DiscardOperator(
                new RxContentPublisher(
                        Observable.just(
                                new Buffer("x", UTF_8)
                        )));

        Publisher<Buffer> result = discard.apply();

        new RxContentConsumer(result).consume().subscribe(testSubscriber);

        assertThat(testSubscriber.getOnNextEvents(), is(empty()));
        assertThat(testSubscriber.getOnErrorEvents().size(), is(0));
        assertThat(testSubscriber.getOnCompletedEvents().size(), is(1));
    }

    @Test
    public void discardsManyBuffers() {
        DiscardOperator discard = new DiscardOperator(
                new RxContentPublisher(
                        Observable.just(
                                buffer1,
                                buffer2
                        )));

        Publisher<Buffer> result = discard.apply();

        new RxContentConsumer(result).consume().subscribe(testSubscriber);

        assertThat(testSubscriber.getOnNextEvents(), is(empty()));
        assertThat(testSubscriber.getOnErrorEvents().size(), is(0));
        assertThat(testSubscriber.getOnCompletedEvents().size(), is(1));
        assertThat(buffer1.delegate().refCnt(), is(0));
        assertThat(buffer2.delegate().refCnt(), is(0));
    }


    @Test(expectedExceptions = NullPointerException.class)
    public void checkForNullSubscription() {
        Publisher<Buffer> upstream = mock(Publisher.class);
        DiscardOperator aggregator = new DiscardOperator(upstream);

        aggregator.onSubscribe(null);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void allowsOnlyOneSubscription() {
        Subscription subscription1 = mock(Subscription.class);
        Subscription subscription2 = mock(Subscription.class);

        DiscardOperator discard = new DiscardOperator(
                new RxContentPublisher(
                        Observable.just(
                                buffer1,
                                buffer2
                        )));

        discard.onSubscribe(subscription1);

        try {
            discard.onSubscribe(subscription2);
        } catch (IllegalStateException cause) {
            verify(subscription2).cancel();
            throw cause;
        }
    }

//    @Test
//    public void emitsErrors() {
//        AtomicBoolean unsubscribed = new AtomicBoolean();
//        AtomicReference<Throwable> causeCapture = new AtomicReference<>(null);
//
//        Buffer a = new Buffer("aaabbb", UTF_8);
//
//        PublishSubject<Buffer> subject = PublishSubject.create();
//
//        AggregateOperator aggregator = new AggregateOperator(
//                new RxContentPublisher(
//                        subject.doOnUnsubscribe(() -> unsubscribed.set(true))), 8
//        );
//
//        CompletableFuture<Buffer> future = aggregator.apply()
//                .exceptionally(cause -> {
//                    causeCapture.set(cause);
//                    throw new RuntimeException();
//                });
//
//        subject.onNext(a);
//        subject.onError(new RuntimeException("something broke"));
//
//        assertTrue(future.isCompletedExceptionally());
//        assertThat(causeCapture.get(), instanceOf(RuntimeException.class));
//        assertThat(causeCapture.get().getMessage(), is("something broke"));
//
//        assertThat(a.delegate().refCnt(), is(0));
//        assertTrue(unsubscribed.get());
//    }
}