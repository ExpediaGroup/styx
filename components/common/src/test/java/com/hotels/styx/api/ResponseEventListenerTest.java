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
import reactor.test.publisher.TestPublisher;
import rx.Observable;
import rx.observers.TestSubscriber;
import rx.subjects.PublishSubject;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.hotels.styx.api.LiveHttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class ResponseEventListenerTest {
    private AtomicBoolean cancelled;
    private AtomicReference<Throwable> responseError;
    private AtomicReference<Throwable> contentError;
    private AtomicBoolean completed;

    @BeforeMethod
    public void setUp() {
        cancelled = new AtomicBoolean();
        responseError = new AtomicReference<>();
        contentError = new AtomicReference<>();
        completed = new AtomicBoolean();
    }

    @Test
    public void doesntFireUnnecessaryEvents() {
        Observable<LiveHttpResponse> publisher = Observable.just(response(OK).body(new ByteStream(Flux.just(new Buffer("hey", UTF_8)))).build());
        TestSubscriber<LiveHttpResponse> subscriber = TestSubscriber.create();

        Observable<LiveHttpResponse> response = ResponseEventListener.from(publisher)
                .whenCancelled(() -> cancelled.set(true))
                .whenResponseError(cause -> responseError.set(cause))
                .whenContentError(cause -> contentError.set(cause))
                .whenCompleted(() -> completed.set(true))
                .apply();

        response.subscribe(subscriber);

        subscriber.getOnNextEvents().get(0).consume();

        assertFalse(cancelled.get());
        assertNull(responseError.get());
        assertNull(contentError.get());
        assertTrue(completed.get());
    }

    @Test
    public void firesWhenResponseIsCancelledBeforeHeaders() {
        PublishSubject<LiveHttpResponse> publisher = PublishSubject.create();
        TestSubscriber<LiveHttpResponse> subscriber = TestSubscriber.create(0);

        Observable<LiveHttpResponse> response = ResponseEventListener.from(publisher)
                .whenCancelled(() -> cancelled.set(true))
                .apply();

        response.subscribe(subscriber);

        subscriber.unsubscribe();

        assertTrue(cancelled.get());
    }

    @Test
    public void firesWhenContentCancelled() {
        TestPublisher<Buffer> contentPublisher = TestPublisher.create();

        LiveHttpResponse response = ResponseEventListener.from(
                Observable.just(response(OK)
                        .body(new ByteStream(contentPublisher))
                        .build()))
                .whenCancelled(() -> cancelled.set(true))
                .apply()
                .toBlocking()
                .first();

        assertThat(cancelled.get(), is(false));

        StepVerifier.create(response.body())
                .then(() -> assertThat(cancelled.get(), is(false)))
                .thenCancel()
                .verify();

        assertTrue(cancelled.get());
    }

    @Test
    public void firesOnResponseError() {
        Observable<LiveHttpResponse> publisher = Observable.error(new RuntimeException());
        TestSubscriber<LiveHttpResponse> subscriber = TestSubscriber.create();

        Observable<LiveHttpResponse> response = ResponseEventListener.from(publisher)
                .whenResponseError(cause -> responseError.set(cause))
                .apply();

        response.subscribe(subscriber);

        assertTrue(responseError.get() instanceof RuntimeException);
    }

    @Test
    public void ignoresResponseErrorAfterHeaders() {
        TestSubscriber<LiveHttpResponse> subscriber = TestSubscriber.create();
        Observable<LiveHttpResponse> publisher = Observable.just(
                response(OK)
                        .body(new ByteStream(Flux.just(new Buffer("hey", UTF_8))))
                        .build())
                .concatWith(Observable.error(new RuntimeException()));

        Observable<LiveHttpResponse> response = ResponseEventListener.from(publisher)
                .whenCancelled(() -> cancelled.set(true))
                .whenResponseError(cause -> responseError.set(cause))
                .whenContentError(cause -> contentError.set(cause))
                .apply();

        response.subscribe(subscriber);

        subscriber.getOnNextEvents().get(0).consume();

        assertFalse(cancelled.get());
        assertNull(responseError.get());
        assertNull(contentError.get());
    }

    @Test
    public void firesResponseContentError() {
        Observable<LiveHttpResponse> publisher = Observable.just(
                response(OK)
                        .body(new ByteStream(Flux.error(new RuntimeException())))
                        .build());

        LiveHttpResponse response = ResponseEventListener.from(publisher)
                .whenContentError(cause -> responseError.set(cause))
                .apply()
                .toBlocking()
                .first();

        response.consume();

        assertTrue(responseError.get() instanceof RuntimeException);
    }

    @Test
    public void firesErrorWhenResponseCompletesWithoutHeaders() {
        TestSubscriber<LiveHttpResponse> testSubscriber = new TestSubscriber<>();
        Observable<LiveHttpResponse> publisher = Observable.empty();

        ResponseEventListener.from(publisher)
                .whenResponseError(cause -> responseError.set(cause))
                .apply()
                .toBlocking()
                .subscribe(testSubscriber);

        testSubscriber.awaitTerminalEvent();
        assertEquals(testSubscriber.getOnCompletedEvents().size(), 1);
        assertTrue(responseError.get() instanceof RuntimeException);
    }
}