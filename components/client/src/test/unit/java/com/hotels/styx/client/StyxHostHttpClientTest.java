/*
  Copyright (C) 2013-2023 Expedia Inc.

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
package com.hotels.styx.client;

import com.hotels.styx.api.Buffer;
import com.hotels.styx.api.ByteStream;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpInterceptor.Context;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.connectionpool.ConnectionPool;
import com.hotels.styx.server.HttpInterceptorContext;
import com.hotels.styx.support.Support;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;

import java.util.concurrent.atomic.AtomicReference;

import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.client.StyxHostHttpClient.ORIGINID_CONTEXT_KEY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static reactor.core.publisher.Flux.just;

public class StyxHostHttpClientTest {
    private LiveHttpRequest request;
    private LiveHttpResponse response;
    private EmitterProcessor<LiveHttpResponse> responseProvider;

    @BeforeEach
    public void setUp() {
        request =  HttpRequest.get("/").build().stream();
        response = HttpResponse.response(OK)
                .header("X-Id", "123")
                .body("xyz", UTF_8)
                .build()
                .stream();
        responseProvider = EmitterProcessor.create();
    }

    @Test
    public void returnsConnectionBackToPool() {
        Connection connection = mockConnection(just(response));
        ConnectionPool pool = mockPool(connection);
        Context context = mockContext();

        StyxHostHttpClient hostClient = new StyxHostHttpClient(pool);

        StepVerifier.create(hostClient.sendRequest(request, context))
                .consumeNextWith(response -> response.consume())
                .expectComplete()
                .verify();

        verify(pool).borrowConnection();
        verify(connection).write(any(LiveHttpRequest.class), any(Context.class));
        verify(pool).returnConnection(any(Connection.class));
        verify(context).add(ORIGINID_CONTEXT_KEY, Id.id("mockorigin"));
    }

    @Test
    public void ignoresCancelledHeaders() {
        // Request observable unsubscribe/cancel has to be ignored after "onNext" event.
        // This is because Reactor Mono will automatically cancel after an event has
        // been published.
        Connection connection = mockConnection(just(response));
        ConnectionPool pool = mockPool(connection);
        Context context = mockContext();
        AtomicReference<LiveHttpResponse> transformedResponse = new AtomicReference<>();

        StyxHostHttpClient hostClient = new StyxHostHttpClient(pool);

        // The StepVerifier consumes the response event and then unsubscribes
        // from the response observable.
        StepVerifier.create(hostClient.sendRequest(request, context))
                .consumeNextWith(transformedResponse::set)
                .verifyComplete();

        // The response is still alive...
        verify(pool, never()).returnConnection(any(Connection.class));
        verify(pool, never()).closeConnection(any(Connection.class));

        // ... until response body is consumed:
        transformedResponse.get().consume();

        // Finally, the connection is returned after the response body is fully consumed:
        verify(pool).returnConnection(any(Connection.class));

        verify(context).add(ORIGINID_CONTEXT_KEY, Id.id("mockorigin"));
    }

    @Test
    public void releasesIfRequestIsCancelledBeforeHeaders() {
        Connection connection = mockConnection(EmitterProcessor.create());
        ConnectionPool pool = mockPool(connection);
        Context context = mockContext();

        StyxHostHttpClient hostClient = new StyxHostHttpClient(pool);
        AtomicReference<Subscription> subscription = new AtomicReference<>();

        Flux.from(hostClient.sendRequest(request, context))
                .subscribe(new BaseSubscriber<LiveHttpResponse>() {
                    @Override
                    protected void hookOnSubscribe(Subscription s) {
                        super.hookOnSubscribe(s);
                        s.request(1);
                        subscription.set(s);
                    }
                });

        subscription.get().cancel();
        verify(pool).closeConnection(any(Connection.class));
        verify(context).add(ORIGINID_CONTEXT_KEY, Id.id("mockorigin"));
    }

    @Test
    public void ignoresResponseObservableErrorsAfterHeaders() {
        Connection connection = mockConnection(responseProvider);
        ConnectionPool pool = mockPool(connection);
        Context context = mockContext();
        AtomicReference<LiveHttpResponse> newResponse = new AtomicReference<>();

        StyxHostHttpClient hostClient = new StyxHostHttpClient(pool);

        StepVerifier.create(hostClient.sendRequest(request, context))
                .then(() -> {
                    responseProvider.onNext(response);
                    responseProvider.onError(new RuntimeException("oh dear ..."));
                })
                .consumeNextWith(newResponse::set)
                .expectError()
                .verify();

        newResponse.get().consume();

        verify(pool).returnConnection(any(Connection.class));
        verify(context).add(ORIGINID_CONTEXT_KEY, Id.id("mockorigin"));
    }

    @Test
    public void terminatesConnectionWhenResponseObservableCompletesWithoutHeaders() {
        // A connection that yields no response:
        Connection connection = mockConnection(Flux.empty());
        ConnectionPool pool = mockPool(connection);
        Context context = mockContext();

        StyxHostHttpClient hostClient = new StyxHostHttpClient(pool);

        StepVerifier.create(hostClient.sendRequest(request, context))
                .expectNextCount(0)
                .expectComplete()
                .log()
                .verify();

        verify(pool).closeConnection(any(Connection.class));
        verify(context).add(ORIGINID_CONTEXT_KEY, Id.id("mockorigin"));
    }

    @Test
    public void releasesConnectionWhenResponseFailsBeforeHeaders() {
        Connection connection = mockConnection(Flux.error(new RuntimeException()));
        ConnectionPool pool = mockPool(connection);
        Context context = mockContext();

        StyxHostHttpClient hostClient = new StyxHostHttpClient(pool);

        StepVerifier.create(hostClient.sendRequest(request, context))
                .expectNextCount(0)
                .expectError()
                .verify();

        verify(pool).closeConnection(any(Connection.class));
        verify(context).add(ORIGINID_CONTEXT_KEY, Id.id("mockorigin"));
    }

    @Test
    public void terminatesConnectionDueToUnsubscribedBody() {
        TestPublisher<Buffer> testPublisher = TestPublisher.create();
        Connection connection = mockConnection(just(LiveHttpResponse.response(OK).body(new ByteStream(testPublisher)).build()));
        ConnectionPool pool = mockPool(connection);
        Context context = mockContext();
        AtomicReference<LiveHttpResponse> receivedResponse = new AtomicReference<>();

        StyxHostHttpClient hostClient = new StyxHostHttpClient(pool);

        StepVerifier.create(hostClient.sendRequest(request, context))
                .consumeNextWith(receivedResponse::set)
                .expectComplete()
                .verify();

        StepVerifier.create(receivedResponse.get().body())
                .thenCancel()
                .verify();

        verify(pool).closeConnection(any(Connection.class));
        verify(context).add(ORIGINID_CONTEXT_KEY, Id.id("mockorigin"));
    }

    @Test
    public void closesTheConnectionPool() {
        ConnectionPool pool = mock(ConnectionPool.class);

        StyxHostHttpClient hostClient = new StyxHostHttpClient(pool);

        hostClient.close();
        verify(pool).close();
    }

    Connection mockConnection(Flux<LiveHttpResponse> responseObservable) {
        Connection connection = mock(Connection.class);
        when(connection.write(any(LiveHttpRequest.class), any(Context.class))).thenReturn(responseObservable);
        return connection;
    }

    ConnectionPool mockPool(Connection connection) {
        ConnectionPool pool = mock(ConnectionPool.class);
        when(pool.borrowConnection()).thenReturn(Flux.just(connection));
        Origin origin = mockOrigin("mockorigin");
        when(pool.getOrigin()).thenReturn(origin);
        return pool;
    }

    Origin mockOrigin(String id) {
        Origin origin = mock(Origin.class);
        when(origin.id()).thenReturn(Id.id(id));
        return origin;
    }

    HttpInterceptor.Context mockContext() {
        return mock(HttpInterceptor.Context.class);
    }
}
