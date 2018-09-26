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
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observable;
import rx.subjects.PublishSubject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class RxContentPublisherTest {

    Buffer b1 = new Buffer("x", UTF_8);
    Buffer b2 = new Buffer("y", UTF_8);
    Buffer b3 = new Buffer("z", UTF_8);
    private TestSubscriber subscriber;

    @BeforeMethod
    public void setUp() {
        subscriber = new TestSubscriber();
    }

    @Test
    public void onCompleteInBufferingWithEmptyQueue() {
        /*
         * In BUFFERING state.
         * - downstream not accepting (n == 0)
         * - downstream requests 1 element --> EMITTING state
         * - emit a single event, fall back to BUFFERING
         * - onComplete follows in BUFFERING state
         */

        RxContentPublisher publisher = new RxContentPublisher(Observable.just(b1, b2, b3));

        publisher.subscribe(subscriber);
        assertThat(subscriber.events, contains("onSubscribe"));

        subscriber.request(2);
        assertThat(subscriber.events, contains("onSubscribe", "onNext - x", "onNext - y"));

        subscriber.request(1);
        System.out.println("test subscriber: " + subscriber.toString());
        assertThat(subscriber.events, contains("onSubscribe", "onNext - x", "onNext - y", "onNext - z", "onComplete"));
    }

    @Test
    public void onCompleteInBufferingWithQueuedEvents() {
        /*
         * In BUFFERING state.
         * - queued event(s) in BUFFERING state
         * - onComplete follows
         */

        RxContentPublisher publisher = new RxContentPublisher(Observable.just(b1, b2, b3),
                false);

        publisher.subscribe(subscriber);
        assertThat(subscriber.events, contains("onSubscribe"));

        subscriber.request(1);
        assertThat(subscriber.events, contains("onSubscribe", "onNext - x"));

        subscriber.request(2);
        System.out.println("test subscriber: " + subscriber.toString());
        assertThat(subscriber.events, contains("onSubscribe", "onNext - x", "onNext - y", "onNext - z", "onComplete"));
    }

    @Test
    public void onCompleteInEmittingState() {
        /*
         * In BUFFERING state.
         * - downstream not accepting (n == 0)
         * - downstream requests 2 elements --> EMITTING state
         * - emit a single event,
         * - onComplete follows in EMITTING state
         */

        RxContentPublisher publisher = new RxContentPublisher(Observable.just(b1, b2, b3));

        publisher.subscribe(subscriber);
        assertThat(subscriber.events, contains("onSubscribe"));

        subscriber.request(2);
        assertThat(subscriber.events, contains("onSubscribe", "onNext - x", "onNext - y"));

        subscriber.request(2);
        System.out.println("test subscriber: " + subscriber.toString());
        assertThat(subscriber.events, contains("onSubscribe", "onNext - x", "onNext - y", "onNext - z", "onComplete"));
    }

    @Test
    public void onErrorInBufferingWithEmptyQueue() {
        /*
         * - downstream requests an element -> EMITTING state
         * - event processed in EMITTING, drops back to BUFFERING
         * - onError appears in BUFFERING while queue is empty
         */
        RxContentPublisher publisher = new RxContentPublisher(
                Observable.just(b1, b2).concatWith(Observable.error(new RuntimeException("oh doh"))));

        publisher.subscribe(subscriber);
        assertThat(subscriber.events, contains("onSubscribe"));

        subscriber.request(1);
        assertThat(subscriber.events, contains("onSubscribe", "onNext - x"));

        subscriber.request(1);
        System.out.println("test subscriber: " + subscriber.toString());
        assertThat(subscriber.events, contains("onSubscribe", "onNext - x", "onNext - y", "onError"));
    }

    @Test
    public void onErrorInBufferingWithQueuedEvents() {
        RxContentPublisher publisher = new RxContentPublisher(
                Observable.just(b1, b2, b3).concatWith(Observable.error(new RuntimeException("oh my"))),
                false);

        publisher.subscribe(subscriber);
        assertThat(subscriber.events, contains("onSubscribe"));

        subscriber.request(2);
        assertThat(subscriber.events, contains("onSubscribe", "onNext - x", "onNext - y"));

        subscriber.request(2);
        System.out.println("test subscriber: " + subscriber.toString());
        assertThat(subscriber.events, contains("onSubscribe", "onNext - x", "onNext - y", "onNext - z", "onError"));
    }

    @Test
    public void onErrorInEmitting() {
        PublishSubject<Buffer> upstream = PublishSubject.create();

        RxContentPublisher publisher = new RxContentPublisher(upstream,true);

        publisher.subscribe(subscriber);
        assertThat(subscriber.events, contains("onSubscribe"));

        subscriber.request(10);
        upstream.onNext(b1);
        upstream.onNext(b2);
        upstream.onError(new RuntimeException("oh dear :-O"));
        System.out.println("test subscriber: " + subscriber.toString());
        assertThat(subscriber.events, contains("onSubscribe", "onNext - x", "onNext - y", "onError"));
    }

    @Test
    public void emitErrorOnNegativeRequest() {
        RxContentPublisher publisher = new RxContentPublisher(Observable.just(b1, b2, b3));
        publisher.subscribe(subscriber);

        System.out.println("subscriber before: " + subscriber);

        subscriber.request(-1);
        System.out.println("subscriber after: " + subscriber);
        assertThat(subscriber.events, contains("onSubscribe", "onError"));
    }

    @Test
    public void unsubscribesFromSourceOnNegativeRequest() {
        AtomicBoolean unsubscribed = new AtomicBoolean();
        PublishSubject<Buffer> upstream = PublishSubject.create();

        RxContentPublisher publisher = new RxContentPublisher(upstream.doOnUnsubscribe(() -> unsubscribed.set(true)));
        publisher.subscribe(subscriber);

        subscriber.request(100);

        upstream.onNext(new Buffer("xyz", UTF_8));

        subscriber.request(-1);

        assertThat(subscriber.events, contains("onSubscribe", "onNext - xyz", "onError"));
        assertThat(unsubscribed.get(), is(true));
    }

    @Test
    public void cancelInBuffering() {
        AtomicBoolean unsubscribed = new AtomicBoolean();
        PublishSubject<Buffer> upstream = PublishSubject.create();

        RxContentPublisher publisher = new RxContentPublisher(upstream.doOnUnsubscribe(() -> unsubscribed.set(true)));

        publisher.subscribe(subscriber);
        assertThat(subscriber.events, contains("onSubscribe"));

        upstream.onNext(b1);
        subscriber.cancel();

        assertThat(subscriber.events, contains("onSubscribe"));
        assertThat(unsubscribed.get(), is(true));
    }

    @Test
    public void cancelInEmitting() {
        AtomicBoolean unsubscribed = new AtomicBoolean();
        PublishSubject<Buffer> upstream = PublishSubject.create();

        RxContentPublisher publisher = new RxContentPublisher(upstream.doOnUnsubscribe(() -> unsubscribed.set(true)));

        publisher.subscribe(subscriber);
        assertThat(subscriber.events, contains("onSubscribe"));

        subscriber.request(100);

        upstream.onNext(b1);
        subscriber.cancel();

        assertThat(subscriber.events, contains("onSubscribe", "onNext - x"));
        assertThat(unsubscribed.get(), is(true));
    }

    @Test
    public void callsOnSubscribe() {
        Subscriber<Buffer> subscriber = mock(Subscriber.class);

        RxContentPublisher publisher = new RxContentPublisher(Observable.empty());
        publisher.subscribe(subscriber);

        verify(subscriber).onSubscribe(any(Subscription.class));
    }

//    @Test(expectedExceptions = NullPointerException.class)
//    public void subscribeThrowsNPE() {
//        RxContentPublisher publisher = new RxContentPublisher(Observable.empty());
//        publisher.subscribe(null);
//    }


    /* (chapter 3):

     * 2. The Subscription MUST allow the Subscriber to call Subscription.request synchronously
     *    from within onNext or onSubscribe.
     */

    @Test(enabled = false)
    public void allowsSynchronousRequestFromwithinOnNext() {
        RxContentPublisher publisher = new RxContentPublisher(Observable.just(b1).repeat(10000000));
        publisher.subscribe(new Subscriber<Buffer>() {
            private Subscription subscription;

            @Override
            public void onSubscribe(Subscription subscription) {
                this.subscription = subscription;
                this.subscription.request(100);
            }

            @Override
            public void onNext(Buffer buffer) {
                this.subscription.request(100);
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onComplete() {

            }
        });
    }

    // Specific to RxContentPublisher:
    @Test
    public void allowsOnlyOneSubscription() {

    }


    static class TestSubscriber implements Subscriber<Buffer> {
        List<String> events = new ArrayList<>();
        private Subscription subscription;

        @Override
        public void onSubscribe(Subscription subscription) {
            this.events.add("onSubscribe");
            this.subscription = subscription;
        }

        @Override
        public void onNext(Buffer buffer) {
            this.events.add("onNext - " + new String(buffer.content(), UTF_8));
        }

        @Override
        public void onError(Throwable cause) {
            this.events.add("onError");
            System.out.println("error: ");
            cause.printStackTrace();
        }

        @Override
        public void onComplete() {
            this.events.add("onComplete");
        }

        public void request(int n) {
            this.subscription.request(n);
        }

        public void cancel() {
            subscription.cancel();
        }

        public String toString() {
            return String.join(", ", events);
        }
    }
}
