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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static rx.RxReactiveStreams.toObservable;
import static rx.RxReactiveStreams.toPublisher;

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
        Publisher<Buffer> upstream = toPublisher(Observable.just(new Buffer("x", UTF_8)));
        DiscardOperator discard = new DiscardOperator(upstream);

        discard.subscribe(mock(Subscriber.class));
        discard.subscribe(mock(Subscriber.class));
    }

    @Test
    public void discardsZeroBuffers() {
        DiscardOperator discard = new DiscardOperator(toPublisher(Observable.empty()));

        toObservable(discard).subscribe(testSubscriber);

        assertThat(testSubscriber.getOnNextEvents(), is(empty()));

        assertThat(testSubscriber.getOnNextEvents().size(), is(0));
        assertThat(testSubscriber.getOnErrorEvents().size(), is(0));
        assertThat(testSubscriber.getOnCompletedEvents().size(), is(1));
    }

    @Test
    public void discardsOneBuffer() {
        DiscardOperator discard = new DiscardOperator(
                toPublisher(
                        Observable.just(
                                new Buffer("x", UTF_8)
                        )));

        toObservable(discard).subscribe(testSubscriber);

        assertThat(testSubscriber.getOnNextEvents(), is(empty()));
        assertThat(testSubscriber.getOnErrorEvents().size(), is(0));
        assertThat(testSubscriber.getOnCompletedEvents().size(), is(1));
    }

    @Test
    public void discardsManyBuffers() {
        DiscardOperator discard = new DiscardOperator(
                toPublisher(
                        Observable.just(
                                buffer1,
                                buffer2
                        )));

        toObservable(discard).subscribe(testSubscriber);

        assertThat(testSubscriber.getOnNextEvents(), is(empty()));
        assertThat(testSubscriber.getOnErrorEvents().size(), is(0));
        assertThat(testSubscriber.getOnCompletedEvents().size(), is(1));
        assertThat(buffer1.delegate().refCnt(), is(0));
        assertThat(buffer2.delegate().refCnt(), is(0));
    }


    @Test(expectedExceptions = NullPointerException.class)
    public void checkForNullSubscription() {
        DiscardOperator discard = new DiscardOperator(
                subscriber -> subscriber.onSubscribe(null));

        discard.subscribe(mock(Subscriber.class));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void allowsOnlyOneSubscription() {
        Publisher<Buffer> upstream = toPublisher(Observable.just(new Buffer("x", UTF_8)));

        Subscriber subscription1 = mock(Subscriber.class);
        Subscriber subscription2 = mock(Subscriber.class);

        DiscardOperator discard = new DiscardOperator(upstream);

        discard.subscribe(subscription1);
        discard.subscribe(subscription2);
    }

}