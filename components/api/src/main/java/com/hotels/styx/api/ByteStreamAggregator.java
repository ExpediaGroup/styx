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

import io.netty.buffer.CompositeByteBuf;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.netty.buffer.Unpooled.compositeBuffer;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

class ByteStreamAggregator implements Subscriber<Buffer> {
    private final Publisher<Buffer> upstream;
    private final int maxSize;
    private final CompletableFuture<Buffer> future = new CompletableFuture<>();
    private final AtomicBoolean active = new AtomicBoolean();
    private final CompositeByteBuf aggregated = compositeBuffer();
    private Subscription subscription;

    ByteStreamAggregator(Publisher<Buffer> upstream, int maxSize) {
        this.upstream = requireNonNull(upstream);
        this.maxSize = maxSize;
    }

    public CompletableFuture<Buffer> apply() {
        if (active.compareAndSet(false, true)) {
            this.upstream.subscribe(this);
            return future;
        } else {
            throw new IllegalStateException("ByteStreamAggregator may only be started once.");
        }
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        if (this.subscription == null) {
            this.subscription = subscription;
            this.subscription.request(Long.MAX_VALUE);
        } else {
            subscription.cancel();
            throw new IllegalStateException("ByteStreamAggregator supports only one Producer instance.");
        }
    }

    @Override
    public void onNext(Buffer part) {
        long newSize = aggregated.readableBytes() + part.size();

        if (newSize > maxSize) {
            part.delegate().release();
            aggregated.release();
            subscription.cancel();
            this.future.completeExceptionally(
                    new ContentOverflowException(format("Maximum content size exceeded. Maximum size allowed is %d bytes.", maxSize)));
        } else {
            aggregated.addComponent(part.delegate());
            aggregated.writerIndex(aggregated.writerIndex() + part.size());
        }
    }

    @Override
    public void onError(Throwable cause) {
        aggregated.release();
        subscription.cancel();
        future.completeExceptionally(cause);
    }

    @Override
    public void onComplete() {
        future.complete(new Buffer(aggregated));
    }

}
