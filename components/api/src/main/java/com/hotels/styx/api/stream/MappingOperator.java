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
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

class MappingOperator implements Publisher<Buffer> {
    private final Function<Buffer, Buffer> mapping;
    private final Publisher<Buffer> upstream;
    private final AtomicReference<Subscriber<? super Buffer>> subscriber = new AtomicReference<>();

    public MappingOperator(Publisher<Buffer> upstream, Function<Buffer, Buffer> mapping) {
        this.upstream = upstream;
        this.mapping = mapping;
    }

    // TODO: calls to subscribe simultaneously from multiple threads:
    @Override
    public void subscribe(Subscriber<? super Buffer> subscriber) {
        if (this.subscriber.compareAndSet(null, subscriber)) {
            MapSubscription x = new MapSubscription(mapping, subscriber);
            // Subscribe downstream first, then upstream.
            // Otherwise upstream events may arrive before emitting an onSubscribe event.
            subscriber.onSubscribe(x);
            this.upstream.subscribe(x);
        } else {
            throw new IllegalStateException("Second onSubscribe event to MappingOperator");
        }
    }

    private static class MapSubscription implements Subscription , Subscriber<Buffer>  {
        private final Function<Buffer, Buffer> mapping;
        private final Subscriber<? super Buffer> subscriber;
        private Subscription subscription;

        public MapSubscription(Function<Buffer, Buffer> mapping, Subscriber<? super Buffer> subscriber) {
            this.mapping = mapping;
            this.subscriber = subscriber;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            requireNonNull(subscription);
            this.subscription = subscription;
        }

        @Override
        public void request(long n) {
            subscription.request(n);
        }

        @Override
        public void cancel() {
            subscription.cancel();
        }

        @Override
        public void onNext(Buffer buffer) {
            subscriber.onNext(mapping.apply(buffer));
        }

        @Override
        public void onError(Throwable throwable) {
            subscriber.onError(throwable);
        }

        @Override
        public void onComplete() {
            subscriber.onComplete();
        }
    }

}
