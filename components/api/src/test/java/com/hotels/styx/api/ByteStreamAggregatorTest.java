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

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.testng.annotations.Test;
import reactor.core.publisher.Flux;
import reactor.test.publisher.TestPublisher;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertTrue;

public class ByteStreamAggregatorTest {

    @Test(expectedExceptions = IllegalStateException.class)
    public void allowsOnlyOneAggregation() {
        Publisher<Buffer> upstream = Flux.just(new Buffer("x", UTF_8));
        ByteStreamAggregator aggregator = new ByteStreamAggregator(upstream, 100);

        aggregator.apply();
        aggregator.apply();
    }

    @Test
    public void aggregatesZeroBuffers() throws ExecutionException, InterruptedException {
        ByteStreamAggregator aggregator = new ByteStreamAggregator(Flux.empty(), 100);

        Buffer a = aggregator.apply().get();
        assertThat(a.size(), is(0));
        assertThat(new String(a.content(), UTF_8), is(""));
    }

    @Test
    public void aggregatesOneBuffer() throws ExecutionException, InterruptedException {
        ByteStreamAggregator aggregator = new ByteStreamAggregator(Flux.just(new Buffer("x", UTF_8)), 100);

        Buffer a = aggregator.apply().get();
        assertThat(a.size(), is(1));
        assertThat(new String(a.content(), UTF_8), is("x"));
    }

    @Test
    public void aggregatesManyBuffers() throws ExecutionException, InterruptedException {
        ByteStreamAggregator aggregator = new ByteStreamAggregator(Flux.just(
                new Buffer("x", UTF_8),
                new Buffer("y", UTF_8)), 100);

        Buffer a = aggregator.apply().get();
        assertThat(a.size(), is(2));
        assertThat(new String(a.content(), UTF_8), is("xy"));
    }

    @Test
    public void aggregatesUpToNBytes() {
        AtomicReference<Throwable> causeCapture = new AtomicReference<>(null);

        Buffer a = new Buffer("aaabbb", UTF_8);
        Buffer b = new Buffer("ccc", UTF_8);

        TestPublisher<Buffer> upstream = TestPublisher.create();

        ByteStreamAggregator aggregator = new ByteStreamAggregator(upstream, 8);

        CompletableFuture<Buffer> future = aggregator.apply()
                .exceptionally(cause -> {
                    causeCapture.set(cause);
                    throw new RuntimeException();
                });

        upstream.next(a);
        upstream.next(b);

        upstream.assertCancelled();

        assertTrue(future.isCompletedExceptionally());
        assertThat(causeCapture.get(), instanceOf(ContentOverflowException.class));

        assertThat(a.delegate().refCnt(), is(0));
        assertThat(b.delegate().refCnt(), is(0));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void checkForNullSubscription() {
        Publisher<Buffer> upstream = mock(Publisher.class);
        ByteStreamAggregator aggregator = new ByteStreamAggregator(upstream, 100);

        aggregator.onSubscribe(null);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void allowsOnlyOneSubscription() {
        Publisher<Buffer> upstream = mock(Publisher.class);
        Subscription subscription1 = mock(Subscription.class);
        Subscription subscription2 = mock(Subscription.class);

        ByteStreamAggregator aggregator = new ByteStreamAggregator(upstream, 100);
        aggregator.onSubscribe(subscription1);

        try {
            aggregator.onSubscribe(subscription2);
        } catch (IllegalStateException cause) {
            verify(subscription2).cancel();
            throw cause;
        }
    }

    @Test
    public void emitsErrors() {
        AtomicReference<Throwable> causeCapture = new AtomicReference<>(null);

        Buffer a = new Buffer("aaabbb", UTF_8);

        TestPublisher<Buffer> upstream = TestPublisher.create();

        ByteStreamAggregator aggregator = new ByteStreamAggregator(upstream, 8);

        CompletableFuture<Buffer> future = aggregator.apply()
                .exceptionally(cause -> {
                    causeCapture.set(cause);
                    throw new RuntimeException();
                });

        upstream.next(a);
        upstream.error(new RuntimeException("something broke"));

        upstream.assertCancelled();

        assertTrue(future.isCompletedExceptionally());
        assertThat(causeCapture.get(), instanceOf(RuntimeException.class));
        assertThat(causeCapture.get().getMessage(), is("something broke"));

        assertThat(a.delegate().refCnt(), is(0));
    }
}