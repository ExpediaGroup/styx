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
package com.hotels.styx.api;

import io.netty.buffer.ByteBuf;
import org.testng.annotations.Test;
import rx.observers.TestSubscriber;
import rx.subjects.PublishSubject;

import static com.hotels.styx.api.FlowControlDisableOperator.disableFlowControl;
import static io.netty.buffer.Unpooled.copiedBuffer;
import static java.nio.charset.StandardCharsets.UTF_8;
import static rx.Observable.just;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class FlowControlDisableOperatorTest {
    /*
      This test proves that when the subscriber requests back-pressure, but the FlowControlDisableOperator is applied,
       the events are delivered as if no back-pressure was requested.
     */
    @Test
    public void liftedObservableDisablesBackpressure() throws InterruptedException {
        TestSubscriber<String> subscriber = new TestSubscriber<>(1);

        just("one", "two", "three")
                .lift(disableFlowControl())
                .subscribe(subscriber);

        subscriber.assertValues("one", "two", "three");
    }

    /*
      This test does not actually test the FlowControlDisableOperator, rather it is used to demonstrate the difference
       that the presence of the operator makes to the delivery of events.
     */
    @Test
    public void nonLiftedObservableCanBeSubscribedWithBackpressure() {
        TestSubscriber<String> subscriber = new TestSubscriber<>(1);

        just("one", "two", "three")
                .subscribe(subscriber);

        subscriber.assertValues("one");
        subscriber.requestMore(1);
        subscriber.assertValues("one", "two");
        subscriber.requestMore(1);
        subscriber.assertValues("one", "two", "three");
    }

    @Test
    public void releasesReferenceCountsAfterDownstreamUnsubscribes() throws Exception {
        TestSubscriber<ByteBuf> subscriber = new TestSubscriber<>();
        PublishSubject<ByteBuf> source = PublishSubject.create();

        ByteBuf buf1 = copiedBuffer("abc", UTF_8);
        ByteBuf buf2 = copiedBuffer("abc", UTF_8);
        ByteBuf buf3 = copiedBuffer("abc", UTF_8);

        source.lift(disableFlowControl())
                .subscribe(subscriber);

        source.onNext(buf1);
        assertThat(buf1.refCnt(), is(1));

        subscriber.unsubscribe();

        source.onNext(buf2);
        assertThat(buf2.refCnt(), is(0));

        source.onNext(buf3);
        assertThat(buf3.refCnt(), is(0));
    }
}