package com.hotels.styx.api.stream;

import com.hotels.styx.api.Buffer;
import org.reactivestreams.Subscriber;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observable;
import rx.observers.TestSubscriber;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

public class MappingOperatorTest {
    private TestSubscriber<String> testSubscriber;
    private Buffer buffer1;
    private Buffer buffer2;

    @BeforeMethod
    public void setUp() {
        testSubscriber = new TestSubscriber<>(100);
        buffer1 = new Buffer("x", UTF_8);
        buffer2 = new Buffer("y", UTF_8);
    }

    @Test
    public void appliesMappingToContent() {
        RxContentPublisher upstream = new RxContentPublisher(Observable.just(
                new Buffer("x", UTF_8),
                new Buffer("Y", UTF_8)
        ));

        MappingOperator mapping = new MappingOperator(upstream, MappingOperatorTest::toUpperCaseBuffer);

        RxContentConsumer consumer = new RxContentConsumer(mapping);

        consumer.consume()
                .map(buffer -> new String(buffer.content(), UTF_8))
                .subscribe(testSubscriber);

        assertThat(testSubscriber.getOnNextEvents(), contains("X", "Y"));
    }


    @Test(expectedExceptions = IllegalStateException.class)
    public void allowsOnlyOneSubscription() {
        RxContentPublisher upstream = new RxContentPublisher(Observable.just(new Buffer("x", UTF_8)));
        MappingOperator mapper = new MappingOperator(upstream,
                MappingOperatorTest::toUpperCaseBuffer);
        Subscriber subscription1 = mock(Subscriber.class);
        Subscriber subscription2 = mock(Subscriber.class);

        mapper.subscribe(subscription1);

        try {
            mapper.subscribe(subscription2);
        } catch (IllegalStateException cause) {
//            verify(subscription2).cancel();
            throw cause;
        }
    }


    @Test
    public void mapsAnEmptyStream() {
        MappingOperator operator = new MappingOperator(new RxContentPublisher(Observable.empty()),
                MappingOperatorTest::toUpperCaseBuffer);

        RxContentConsumer consumer = new RxContentConsumer(operator);

        testSubscriber.requestMore(2);

        consumer.consume()
                .map(buffer -> new String(buffer.content(), UTF_8))
                .subscribe(testSubscriber);

        assertThat(testSubscriber.getOnNextEvents().size(), is(0));
        assertThat(testSubscriber.getOnErrorEvents().size(), is(0));
        assertThat(testSubscriber.getOnCompletedEvents().size(), is(1));
    }

    @Test
    public void mapsAnEmptyErroringStream() {
        MappingOperator operator = new MappingOperator(new RxContentPublisher(
                Observable.<Buffer>empty().concatWith(Observable.error(new RuntimeException(">:-O")))),
                MappingOperatorTest::toUpperCaseBuffer);

        RxContentConsumer consumer = new RxContentConsumer(operator);

        consumer.consume()
                .map(buffer -> new String(buffer.content(), UTF_8))
                .subscribe(testSubscriber);

        assertThat(testSubscriber.getOnNextEvents().size(), is(0));
        assertThat(testSubscriber.getOnErrorEvents().size(), is(1));
        assertThat(testSubscriber.getOnCompletedEvents().size(), is(0));
    }


    @Test
    public void emitsErrors() {
        MappingOperator operator = new MappingOperator(
                new RxContentPublisher(
                        Observable.just(buffer2)
                                .concatWith(Observable.error(new RuntimeException(";-(")))),
                MappingOperatorTest::toUpperCaseBuffer);

        RxContentConsumer consumer = new RxContentConsumer(operator);

        consumer.consume()
                .map(buffer -> new String(buffer.content(), UTF_8))
                .subscribe(testSubscriber);

        assertThat(testSubscriber.getOnNextEvents().size(), is(1));
        assertThat(testSubscriber.getOnErrorEvents().size(), is(1));
        assertThat(testSubscriber.getOnCompletedEvents().size(), is(0));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void checkForNullSubscription() {

        MappingOperator aggregator = new MappingOperator(
                subscriber -> subscriber.onSubscribe(null),
                MappingOperatorTest::toUpperCaseBuffer);

        aggregator.subscribe(mock(Subscriber.class));
    }


    public static Buffer toUpperCaseBuffer(Buffer buffer) {
        String mapped = new String(buffer.content(), UTF_8).toUpperCase();
        return new Buffer(mapped, UTF_8);
    }
}