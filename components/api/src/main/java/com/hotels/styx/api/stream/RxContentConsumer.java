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
import org.reactivestreams.Subscription;
import rx.Observable;
import rx.Observer;
import rx.observables.SyncOnSubscribe;

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Todo: Javadoc: RxContentConsumer consumes content.
 */
public class RxContentConsumer {
    private final Publisher<Buffer> publisher;

    public RxContentConsumer(Publisher<Buffer> publisher) {
        this.publisher = publisher;
    }

    Observable<Buffer> consume() {

        return Observable.create(new SyncOnSubscribe<ProducerState, Buffer>() {
            @Override
            protected ProducerState generateState() {
                ProducerState state = new ProducerState(publisher, new MySubscriber(), 0);
                state.init();
                return state;
            }

            @Override
            protected ProducerState next(ProducerState state, Observer<? super Buffer> observer) {
                // Request in effect.
                return state.request(observer);
            }

        });
    }

    static class MySubscriber implements Subscriber<Buffer> {
        private final ConcurrentLinkedDeque<Observer<? super Buffer>> queue = new ConcurrentLinkedDeque<>();
        private Subscription subscription;
        private Observer<? super Buffer> lastObserver;

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
        }

        @Override
        public void onNext(Buffer buffer) {
            lastObserver = queue.removeFirst();
            lastObserver.onNext(buffer);
        }

        @Override
        public void onError(Throwable cause) {
            lastObserver.onError(cause);
            lastObserver = null;
        }

        @Override
        public void onComplete() {
            lastObserver.onCompleted();
            lastObserver = null;
        }

        void request(Observer<? super Buffer> observer) {
            queue.push(observer);
            subscription.request(1);
        }
    }


    private static class ProducerState {
        private final Publisher<Buffer> publisher;
        private final MySubscriber subscriber;
        private final long requested;

        ProducerState(Publisher<Buffer> publisher, MySubscriber subscriber, long n) {
            this.publisher = publisher;
            this.subscriber = subscriber;
            this.requested = n;
        }

        ProducerState request(Observer<? super Buffer> observer) {
            subscriber.request(observer);
            return new ProducerState(this.publisher, this.subscriber, this.requested + 1);
        }

        void init() {
            this.publisher.subscribe(this.subscriber);
        }
    }

}
