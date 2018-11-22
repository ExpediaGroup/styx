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

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.testng.AssertJUnit.assertEquals;


public class ByteStreamTest {
    private Buffer buf1;
    private Buffer buf2;
    private Buffer buf3;

    @BeforeMethod
    public void setUp() {
        buf1 = new Buffer("a", UTF_8);
        buf2 = new Buffer("b", UTF_8);
        buf3 = new Buffer("c", UTF_8);
    }

    @Test
    public void createsFromString() {
        ByteStream byteStream = ByteStream.from("Created from string", UTF_8);

        StepVerifier.create(byteStream)
                .assertNext(buf -> assertEquals("Created from string", new String(buf.content(), UTF_8)))
                .verifyComplete();
    }

    @Test
    public void createsFromByteArray() {
        byte[] bytes = "Created from string".getBytes(UTF_8);

        ByteStream byteStream = ByteStream.from(bytes);

        StepVerifier.create(byteStream)
                .assertNext(buf -> assertEquals("Created from string", new String(buf.content(), UTF_8)))
                .verifyComplete();
    }

    @Test
    public void publishesContent() {
        ByteStream stream = new ByteStream(Flux.just(buf1, buf2, buf3));

        StepVerifier.create(stream)
                .expectNext(buf1)
                .expectNext(buf2)
                .expectNext(buf3)
                .verifyComplete();
    }

    @Test
    public void supportsBackpressure() {
        ByteStream stream = new ByteStream(Flux.just(buf1, buf2, buf3));

        StepVerifier.create(stream, 0)
                .expectSubscription()
                .thenRequest(1)
                .expectNext(buf1)
                .thenRequest(2)
                .expectNext(buf2, buf3)
                .verifyComplete();
    }

    @Test
    public void mapsContent() {
        ByteStream stream = new ByteStream(Flux.just(buf1, buf2, buf3));

        ByteStream mapped = stream.map(this::toUpperCase);

        StepVerifier.create(Flux.from(mapped).map(this::decodeUtf8String))
                .expectSubscription()
                .expectNext("A", "B", "C")
                .verifyComplete();
    }

    @Test
    public void releasesRefcountForMappedBuffers() {
        ByteStream stream = new ByteStream(Flux.just(buf1, buf2, buf3));

        ByteStream mapped = stream.map(this::toUpperCase);

        StepVerifier.create(Flux.from(mapped).map(this::decodeUtf8String))
                .expectSubscription()
                .expectNext("A", "B", "C")
                .verifyComplete();

        assertThat(buf1.delegate().refCnt(), is(0));
        assertThat(buf2.delegate().refCnt(), is(0));
        assertThat(buf3.delegate().refCnt(), is(0));
    }

    @Test
    public void mapRetainsRefcountsForInlineBufferChanges() {
        ByteStream stream = new ByteStream(Flux.just(buf1, buf2, buf3));

        ByteStream mapped = stream.map(buf -> buf);

        StepVerifier.create(Flux.from(mapped).map(this::decodeUtf8String))
                .expectSubscription()
                .expectNextCount(3)
                .verifyComplete();

        assertThat(buf1.delegate().refCnt(), is(1));
        assertThat(buf2.delegate().refCnt(), is(1));
        assertThat(buf3.delegate().refCnt(), is(1));
    }

    @Test
    public void discardsContent() {
        ByteStream stream = new ByteStream(Flux.just(buf1, buf2, buf3));

        ByteStream discarded = stream.drop();

        StepVerifier.create(discarded)
                .expectSubscription()
                .verifyComplete();

        assertThat(buf1.delegate().refCnt(), is(0));
        assertThat(buf2.delegate().refCnt(), is(0));
        assertThat(buf3.delegate().refCnt(), is(0));
    }

    @Test
    public void aggregatesContent() throws ExecutionException, InterruptedException {
        ByteStream stream = new ByteStream(Flux.just(buf1, buf2, buf3));

        Buffer aggregated = stream.aggregate(100).get();
        assertThat(decodeUtf8String(aggregated), is("abc"));
    }

    @Test
    public void contentAggregationOverflow() throws ExecutionException, InterruptedException {
        ByteStream stream = new ByteStream(Flux.just(buf1, buf2, buf3));

        Throwable cause = stream.aggregate(2)
                .handle((result, throwable) -> throwable)
                .get();

        assertThat(cause, instanceOf(ContentOverflowException.class));
    }


    @Test
    public void deliversAtEndOfStreamNotification() {
        AtomicBoolean terminated = new AtomicBoolean();
        ByteStream stream = new ByteStream(Flux.just(buf1, buf2))
                .doOnEnd(maybeCause -> terminated.set(maybeCause == Optional.<Throwable>empty()));

        StepVerifier.create(new ByteStream(stream), 0)
                .thenRequest(1)
                .expectNext(buf1)
                .then(() -> assertThat(terminated.get(), is(false)))
                .thenRequest(1)
                .expectNext(buf2)
                .then(() -> assertThat(terminated.get(), is(true)))
                .expectComplete()
                .verify();
    }

    @Test
    public void deliversAtEndOfStreamNotificationWhenTerminated() {
        AtomicBoolean terminated = new AtomicBoolean();
        ByteStream stream = new ByteStream(Flux.just(buf1, buf2).concatWith(Flux.error(new RuntimeException("bang!"))))
                .doOnEnd(maybeCause -> terminated.set(maybeCause.isPresent()));

        StepVerifier.create(new ByteStream(stream), 0)
                .thenRequest(1)
                .expectNext(buf1)
                .then(() -> assertThat(terminated.get(), is(false)))
                .thenRequest(1)
                .expectNext(buf2)
                .then(() -> assertThat(terminated.get(), is(true)))
                .expectError()
                .verify();
    }

    @Test
    public void runsOnCancelActionWhenCancelled() {
        AtomicBoolean cancelled = new AtomicBoolean();
        ByteStream stream = new ByteStream(Flux.just(buf1, buf2))
                .doOnCancel(() -> cancelled.set(true));

        StepVerifier.create(stream)
                .thenRequest(1)
                .expectNext(buf1)
                .then(() -> assertThat(cancelled.get(), is(false)))
                .thenCancel()
                .verify();

        assertThat(cancelled.get(), is(true));
    }

    @Test
    public void replacesStream() {
        ByteStream stream = new ByteStream(Flux.just(buf1, buf2))
                .replaceWith(new ByteStream(Flux.just(buf3)));

        StepVerifier.create(stream)
                .expectNext(buf3)
                .then(() -> {
                    assertEquals(buf1.delegate().refCnt(), 0);
                    assertEquals(buf2.delegate().refCnt(), 0);
                })
                .verifyComplete();
    }

    @Test
    public void concatenatesStreams() {
        ByteStream stream = new ByteStream(Flux.just(buf1, buf2))
                .concat(new ByteStream(Flux.just(buf3)));

        StepVerifier.create(stream)
                .expectNext(buf1)
                .expectNext(buf2)
                .expectNext(buf3)
                .verifyComplete();
    }

    @Test
    public void concatenatesEmptyStreams() {
        ByteStream stream = new ByteStream(Flux.empty())
                .concat(new ByteStream(Flux.just(buf3)))
                .concat(new ByteStream(Flux.empty()));

        StepVerifier.create(stream)
                .expectNext(buf3)
                .verifyComplete();
    }

    private String decodeUtf8String(Buffer buffer) {
        return new String(buffer.content(), UTF_8);
    }

    private Buffer toUpperCase(Buffer buffer) {
        return new Buffer(decodeUtf8String(buffer).toUpperCase(), UTF_8);
    }

}
