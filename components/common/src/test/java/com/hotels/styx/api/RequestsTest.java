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

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.test.publisher.TestPublisher;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static com.hotels.styx.api.LiveHttpResponse.response;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.slf4j.LoggerFactory.getLogger;

public class RequestsTest {

    private TestPublisher<Buffer> publisher;
    private LiveHttpRequest request;
    private AtomicReference<Optional<Throwable>> completed;
    private LiveHttpResponse response;

    @BeforeMethod
    public void setUp() {
        publisher = TestPublisher.create();
        request = get("/").body(new ByteStream(publisher)).build();
        response = response(OK).body(new ByteStream(publisher)).build();
        completed = new AtomicReference<>();
    }

    @Test
    public void requestDoFinallyActivatesWhenSuccessfullyCompleted() {
        consumeInBackground(Requests.doFinally(request, completed::set));

        publisher.next(new Buffer("content", UTF_8));
        assertThat(completed.get(), is(nullValue()));

        publisher.complete();
        assertThat(completed.get(), is(Optional.empty()));
    }

    @Test
    public void responseDoFinallyActivatesWhenSuccessfullyCompleted() {
        consumeInBackground(Requests.doFinally(response, completed::set));

        publisher.next(new Buffer("content", UTF_8));
        assertThat(completed.get(), is(nullValue()));

        publisher.complete();
        assertThat(completed.get(), is(Optional.empty()));
    }

    @Test
    public void requestDoFinallyActivatesWhenErrors() {
        RuntimeException cause = new RuntimeException("help!!");
        consumeInBackground(Requests.doFinally(request, completed::set));

        publisher.next(new Buffer("content", UTF_8));
        assertThat(completed.get(), is(nullValue()));

        publisher.error(cause);
        assertThat(completed.get(), is(Optional.of(cause)));
    }

    @Test
    public void responseDoFinallyActivatesWhenErrors() {
        RuntimeException cause = new RuntimeException("help!!");
        consumeInBackground(Requests.doFinally(response, completed::set));

        publisher.next(new Buffer("content", UTF_8));
        assertThat(completed.get(), is(nullValue()));

        publisher.error(cause);
        assertThat(completed.get(), is(Optional.of(cause)));
    }

    @Test
    public void requestDoOnCompleteActivatesWhenSuccessfullyCompleted() {
        consumeInBackground(Requests.doOnComplete(request, () -> completed.set(Optional.empty())));

        publisher.next(new Buffer("content", UTF_8));
        assertThat(completed.get(), is(nullValue()));

        publisher.complete();
        assertThat(completed.get(), is(Optional.empty()));
    }

    @Test
    public void responseDoOnCompleteActivatesWhenSuccessfullyCompleted() {
        consumeInBackground(Requests.doOnComplete(response, () -> completed.set(Optional.empty())));

        publisher.next(new Buffer("content", UTF_8));
        assertThat(completed.get(), is(nullValue()));

        publisher.complete();
        assertThat(completed.get(), is(Optional.empty()));
    }

    @Test
    public void requestDoOnCompleteDoesNotActivatesWhenErrors() {
        RuntimeException cause = new RuntimeException("help!!");
        consumeInBackground(Requests.doOnComplete(request, () -> completed.set(Optional.empty())));

        publisher.next(new Buffer("content", UTF_8));
        assertThat(completed.get(), is(nullValue()));

        publisher.error(cause);
        assertThat(completed.get(), is(nullValue()));
    }

    @Test
    public void responseDoOnCompleteDoesNotActivatesWhenErrors() {
        RuntimeException cause = new RuntimeException("help!!");
        consumeInBackground(Requests.doOnComplete(response, () -> completed.set(Optional.empty())));

        publisher.next(new Buffer("content", UTF_8));
        assertThat(completed.get(), is(nullValue()));

        publisher.error(cause);
        assertThat(completed.get(), is(nullValue()));
    }

    @Test
    public void requestDoOnErrorDoesNotActivatesWhenSuccessfullyCompleted() {
        consumeInBackground(Requests.doOnError(request, (cause) -> completed.set(Optional.of(cause))));

        publisher.next(new Buffer("content", UTF_8));
        assertThat(completed.get(), is(nullValue()));

        publisher.complete();
        assertThat(completed.get(), is(nullValue()));
    }

    @Test
    public void responseDoOnErrorDoesNotActivatesWhenSuccessfullyCompleted() {
        consumeInBackground(Requests.doOnError(response, (cause) -> completed.set(Optional.of(cause))));

        publisher.next(new Buffer("content", UTF_8));
        assertThat(completed.get(), is(nullValue()));

        publisher.complete();
        assertThat(completed.get(), is(nullValue()));
    }

    @Test
    public void requestDoOnErrorActivatesWhenErrors() {
        RuntimeException cause = new RuntimeException("help!!");
        consumeInBackground(Requests.doOnError(request, (it) -> completed.set(Optional.of(it))));

        publisher.next(new Buffer("content", UTF_8));
        assertThat(completed.get(), is(nullValue()));

        publisher.error(cause);
        assertThat(completed.get(), is(Optional.of(cause)));
    }


    @Test
    public void responseDoOnErrorActivatesWhenErrors() {
        RuntimeException cause = new RuntimeException("help!!");
        consumeInBackground(Requests.doOnError(response, (it) -> completed.set(Optional.of(it))));

        publisher.next(new Buffer("content", UTF_8));
        assertThat(completed.get(), is(nullValue()));

        publisher.error(cause);
        assertThat(completed.get(), is(Optional.of(cause)));
    }

    private static void consumeInBackground(LiveHttpRequest request) {
        request.consume(1_000_000).subscribe(new Subscriber<LiveHttpRequest>() {
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            public void onNext(LiveHttpRequest o) {
                // ignore
            }

            public void onError(Throwable t) {
                // this will get the implementation class
                getLogger(ResponseEventListenerTest.class).error("Unexpected error", t);
            }

            public void onComplete() {
                // ignore
            }
        });
    }

    private static void consumeInBackground(LiveHttpResponse response) {
        response.consume(1_000_000).subscribe(new Subscriber<LiveHttpResponse>() {
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            public void onNext(LiveHttpResponse o) {
                // ignore
            }

            public void onError(Throwable t) {
                // this will get the implementation class
                getLogger(ResponseEventListenerTest.class).error("Unexpected error", t);
            }

            public void onComplete() {
                // ignore
            }
        });
    }
}