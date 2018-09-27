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

import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

public class DiscardOperator implements Publisher<Buffer> {
    private final Publisher<Buffer> upstream;
    private final AtomicReference<Subscriber<? super Buffer>> subscriber = new AtomicReference<>();

    public DiscardOperator(Publisher<Buffer> upstream) {
        this.upstream = requireNonNull(upstream);
    }

    @Override
    public void subscribe(Subscriber<? super Buffer> subscriber) {
        if (this.subscriber.compareAndSet(null, subscriber)) {
            DiscardSubscription x = new DiscardSubscription(subscriber);
            subscriber.onSubscribe(x);
            this.upstream.subscribe(x);
        } else {
            throw new IllegalStateException("Second onSubscribe event to DiscardOperator");
        }
    }

    private static final class DiscardSubscription implements Subscription, Subscriber<Buffer> {
        private final Subscriber<? super Buffer> subscriber;

        public DiscardSubscription(Subscriber<? super Buffer> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            requireNonNull(subscription);
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(Buffer buffer) {
            buffer.delegate().release();
        }

        @Override
        public void onError(Throwable throwable) {
            // Swallow (should swallow?)
        }

        @Override
        public void onComplete() {
            subscriber.onComplete();
        }

        @Override
        public void request(long l) {

        }

        @Override
        public void cancel() {

        }
    }
}
