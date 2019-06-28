/*
  Copyright (C) 2013-2019 Expedia Inc.

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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.LiveHttpResponse.response;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class ResponseEventListenerTest {
    private AtomicBoolean cancelled;
    private AtomicReference<Throwable> responseError;
    private AtomicReference<Throwable> contentError;
    private AtomicBoolean completed;
    private AtomicBoolean finished;

    @BeforeMethod
    public void setUp() {
        cancelled = new AtomicBoolean();
        responseError = new AtomicReference<>();
        contentError = new AtomicReference<>();
        completed = new AtomicBoolean();
        finished = new AtomicBoolean();
    }

    @Test
    public void doesntFireEventsThatNeverOccurred() {
        Mono<LiveHttpResponse> publisher = Mono.just(response(OK).body(new ByteStream(Flux.just(new Buffer("hey", UTF_8)))).build());

        Flux<LiveHttpResponse> listener = ResponseEventListener.from(publisher)
                .whenCancelled(() -> cancelled.set(true))
                .whenResponseError(cause -> responseError.set(cause))
                .whenContentError(cause -> contentError.set(cause))
                .whenCompleted(() -> completed.set(true))
                .whenFinished(() -> finished.set(true))
                .apply();

        StepVerifier.create(listener)
                .consumeNextWith(LiveHttpMessage::consume)
                .then(() -> {
                    assertFalse(cancelled.get());
                    assertNull(responseError.get());
                    assertNull(contentError.get());
                    assertTrue(completed.get());
                    assertTrue(finished.get());
                })
                .verifyComplete();
    }

    @Test
    public void firesWhenResponseIsCancelledBeforeHeaders() {
        EmitterProcessor<LiveHttpResponse> publisher = EmitterProcessor.create();

        Flux<LiveHttpResponse> listener = ResponseEventListener.from(publisher)
                .whenCancelled(() -> cancelled.set(true))
                .whenFinished(() -> finished.set(true))
                .apply();

        StepVerifier.create(listener)
                .expectNextCount(0)
                .thenCancel()
                .verify();

        assertTrue(cancelled.get());
        assertTrue(finished.get());
    }

    @Test
    public void firesWhenContentCancelled() {
        EmitterProcessor<Buffer> contentPublisher = EmitterProcessor.create();

        Flux<LiveHttpResponse> listener = ResponseEventListener.from(
                Flux.just(response(OK)
                        .body(new ByteStream(contentPublisher))
                        .build()))
                .whenCancelled(() -> cancelled.set(true))
                .whenFinished(() -> finished.set(true))
                .apply();

        StepVerifier.create(listener)
                .consumeNextWith(response ->
                        StepVerifier.create(response.body())
                                .then(() -> assertFalse(cancelled.get()))
                                .thenCancel()
                                .verify())
                .verifyComplete();

        assertTrue(cancelled.get());
        assertTrue(finished.get());
    }

    @Test
    public void firesOnResponseError() {
        Mono<LiveHttpResponse> publisher = Mono.error(new RuntimeException());

        Flux<LiveHttpResponse> listener = ResponseEventListener.from(publisher)
                .whenResponseError(cause -> responseError.set(cause))
                .whenFinished(() -> finished.set(true))
                .apply();

        StepVerifier.create(listener)
                .expectError(RuntimeException.class)
                .verify();

        assertTrue(responseError.get() instanceof RuntimeException);
        assertTrue(finished.get());
    }

    @Test
    public void ignoresResponseErrorAfterHeaders() {
        Flux<LiveHttpResponse> publisher = Flux.just(
                response(OK)
                        .body(new ByteStream(Flux.just(new Buffer("hey", UTF_8))))
                        .build())
                .concatWith(Flux.error(new RuntimeException()));

        Publisher<LiveHttpResponse> listener = ResponseEventListener.from(publisher)
                .whenCancelled(() -> cancelled.set(true))
                .whenResponseError(cause -> responseError.set(cause))
                .whenContentError(cause -> contentError.set(cause))
                .whenFinished(() -> finished.set(true))
                .apply();

        StepVerifier.create(listener)
                .consumeNextWith(LiveHttpMessage::consume)
                .verifyError();

        assertFalse(cancelled.get());
        assertNull(responseError.get());
        assertNull(contentError.get());
        assertTrue(finished.get());
    }

    @Test
    public void firesResponseContentError() {
        Mono<LiveHttpResponse> publisher = Mono.just(
                response(OK)
                        .body(new ByteStream(Flux.error(new RuntimeException())))
                        .build());

        Publisher<LiveHttpResponse> listener = ResponseEventListener.from(publisher)
                .whenContentError(cause -> responseError.set(cause))
                .whenFinished(() -> finished.set(true))
                .apply();

        StepVerifier.create(listener)
                .consumeNextWith(LiveHttpMessage::consume)
                .verifyComplete();

        assertTrue(responseError.get() instanceof RuntimeException);
        assertTrue(finished.get());
    }

    @Test
    public void firesErrorWhenResponseCompletesWithoutHeaders() {

        Publisher<LiveHttpResponse> listener = ResponseEventListener.from(Mono.empty())
                .whenResponseError(cause -> responseError.set(cause))
                .whenFinished(() -> finished.set(true))
                .apply();

        StepVerifier.create(listener)
                .expectNextCount(0)
                .verifyComplete();

        assertTrue(responseError.get() instanceof RuntimeException);
        assertTrue(finished.get());
    }
}