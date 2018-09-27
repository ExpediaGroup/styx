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

import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.Buffer;
import org.reactivestreams.Publisher;
import org.testng.annotations.Test;
import rx.Observable;
import rx.Observer;
import rx.observables.SyncOnSubscribe;
import rx.observers.TestSubscriber;

import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class RxContentConsumerTest {

    private RxContentConsumer rxSubscriber;

    @Test
    public void consumesEvents() {
        TestSubscriber<Buffer> subscriber = new TestSubscriber<>();
        Publisher<Buffer> publisher = new RxContentPublisher(Observable.just(
                new Buffer("x", UTF_8),
                new Buffer("y", UTF_8),
                new Buffer("z", UTF_8)
        ));

        rxSubscriber = new RxContentConsumer(publisher);

        rxSubscriber.consume().subscribe(subscriber);

        assertThat(subscriber.getOnNextEvents().size(), is(3));
    }

    @Test
    public void consumesZeroLengthStreams() {
        TestSubscriber<Buffer> subscriber = new TestSubscriber<>();
        Publisher<Buffer> publisher = new RxContentPublisher(Observable.empty());

        rxSubscriber = new RxContentConsumer(publisher);

        rxSubscriber.consume().subscribe(subscriber);

        assertThat(subscriber.getOnNextEvents().size(), is(0));
        assertThat(subscriber.getOnErrorEvents().size(), is(0));
        assertThat(subscriber.getOnCompletedEvents().size(), is(1));
    }

    class ProducerState {
        private int produced;
        private List<Buffer> data;

        public ProducerState(int produced, List<Buffer> data) {
            this.produced = produced;
            this.data = new ArrayList<>(data);
        }

        public int produced() {
            return produced;
        }

        public Buffer produce() {
            Buffer b = data.remove(0);
            produced ++;
            return b;
        }

        public boolean isCompleted() {
            return data.isEmpty();
        }
    }

    @Test
    public void supportsBackpressure() {
        TestSubscriber<Buffer> subscriber = new TestSubscriber<>(0);
        ProducerState producerState = new ProducerState(0,
                ImmutableList.of(
                        new Buffer("x", UTF_8),
                        new Buffer("y", UTF_8),
                        new Buffer("z", UTF_8)
                ));

        Publisher<Buffer> publisher = new RxContentPublisher(
                Observable.create(new SyncOnSubscribe<ProducerState, Buffer>() {
                    @Override
                    protected ProducerState generateState() {
                        return producerState;
                    }

                    @Override
                    protected ProducerState next(ProducerState state, Observer<? super Buffer> observer) {
                        if (!state.isCompleted()) {
                            Buffer produce = state.produce();
                            System.out.println("produce: " + produce);
                            observer.onNext(produce);
                            if (state.isCompleted()) {
                                observer.onCompleted();
                            }
                        }

                        return state;
                    }

                }));

        rxSubscriber = new RxContentConsumer(publisher);

        rxSubscriber.consume().subscribe(subscriber);

        assertThat(subscriber.getOnNextEvents().size(), is(0));
        assertThat(producerState.produced(), is(0));

        System.out.println("request 1");
        subscriber.requestMore(1);
        assertThat(producerState.produced(), is(1));
        assertThat(subscriber.getOnNextEvents().size(), is(1));

        System.out.println("request 2");
        subscriber.requestMore(2);
        assertThat(subscriber.getOnNextEvents().size(), is(3));
        assertThat(subscriber.getOnCompletedEvents().size(), is(1));
        assertThat(producerState.produced(), is(3));
    }

    @Test
    public void supportsCancellation() {

    }

}