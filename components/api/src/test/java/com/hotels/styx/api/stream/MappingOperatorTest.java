/*
  Copyright (C) 2013-2018 Expedia Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package com.hotels.styx.api.stream;

import com.hotels.styx.api.Buffer;
import org.reactivestreams.Publisher;
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
import static rx.RxReactiveStreams.toObservable;
import static rx.RxReactiveStreams.toPublisher;

public class MappingOperatorTest {
    private TestSubscriber<String> testSubscriber;
    private Buffer buffer1;
    private Buffer buffer2;
    private Publisher<Buffer> upstream;

    @BeforeMethod
    public void setUp() {
        testSubscriber = new TestSubscriber<>(100);
        buffer1 = new Buffer("x", UTF_8);
        buffer2 = new Buffer("y", UTF_8);
        upstream = toPublisher(Observable.just(
                new Buffer("x", UTF_8),
                new Buffer("Y", UTF_8)
        ));
    }

    @Test
    public void appliesMappingToContent() {
        Observable<Buffer> consumer = toObservable(
                new MappingOperator(upstream, this::toUpperCaseBuffer)
        );

        consumer.map(buffer -> new String(buffer.content(), UTF_8))
                .subscribe(testSubscriber);

        assertThat(testSubscriber.getOnNextEvents(), contains("X", "Y"));
    }


    @Test(expectedExceptions = IllegalStateException.class)
    public void allowsOnlyOneSubscription() {
        MappingOperator mapper = new MappingOperator(upstream, this::toUpperCaseBuffer);

        Subscriber subscription1 = mock(Subscriber.class);
        Subscriber subscription2 = mock(Subscriber.class);

        mapper.subscribe(subscription1);
        mapper.subscribe(subscription2);
    }


    @Test
    public void mapsAnEmptyStream() {

        Observable<Buffer> consumer = toObservable(
                new MappingOperator(
                        toPublisher(Observable.empty()),
                        this::toUpperCaseBuffer));

        testSubscriber.requestMore(2);

        consumer.map(buffer -> new String(buffer.content(), UTF_8))
                .subscribe(testSubscriber);

        assertThat(testSubscriber.getOnNextEvents().size(), is(0));
        assertThat(testSubscriber.getOnErrorEvents().size(), is(0));
        assertThat(testSubscriber.getOnCompletedEvents().size(), is(1));
    }

    @Test
    public void mapsAnEmptyErroringStream() {
        MappingOperator operator = new MappingOperator(
                toPublisher(Observable.<Buffer>empty().concatWith(Observable.error(new RuntimeException(">:-O")))),
                this::toUpperCaseBuffer);

        Observable<Buffer> consumer = toObservable(operator);

        consumer.map(buffer -> new String(buffer.content(), UTF_8))
                .subscribe(testSubscriber);

        assertThat(testSubscriber.getOnNextEvents().size(), is(0));
        assertThat(testSubscriber.getOnErrorEvents().size(), is(1));
        assertThat(testSubscriber.getOnCompletedEvents().size(), is(0));
    }


    @Test
    public void emitsErrors() {
        MappingOperator operator = new MappingOperator(
                toPublisher(Observable.just(buffer2).concatWith(Observable.error(new RuntimeException(";-(")))),
                this::toUpperCaseBuffer);

        Observable<Buffer> consumer = toObservable(operator);

        consumer.map(buffer -> new String(buffer.content(), UTF_8))
                .subscribe(testSubscriber);

        assertThat(testSubscriber.getOnNextEvents().size(), is(1));
        assertThat(testSubscriber.getOnErrorEvents().size(), is(1));
        assertThat(testSubscriber.getOnCompletedEvents().size(), is(0));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void checkForNullSubscription() {
        new MappingOperator(
                subscriber -> subscriber.onSubscribe(null),
                this::toUpperCaseBuffer
        ).subscribe(mock(Subscriber.class));
    }

    public Buffer toUpperCaseBuffer(Buffer buffer) {
        String mapped = new String(buffer.content(), UTF_8).toUpperCase();
        return new Buffer(mapped, UTF_8);
    }
}