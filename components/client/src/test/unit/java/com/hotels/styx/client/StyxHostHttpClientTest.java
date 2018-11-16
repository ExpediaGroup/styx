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
package com.hotels.styx.client;

import com.hotels.styx.api.Buffer;
import com.hotels.styx.api.ByteStream;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.client.connectionpool.ConnectionPool;
import org.reactivestreams.Subscription;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;
import rx.Observable;
import rx.subjects.PublishSubject;

import java.util.concurrent.atomic.AtomicReference;

import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static rx.Observable.just;


public class StyxHostHttpClientTest {
    private LiveHttpRequest request;
    private LiveHttpResponse response;
    private PublishSubject<LiveHttpResponse> responseProvider;

    @BeforeMethod
    public void setUp() {
        request =  HttpRequest.get("/").build().stream();
        response = HttpResponse.response(OK)
                .header("X-Id", "123")
                .body("xyz", UTF_8)
                .build()
                .stream();
        responseProvider = PublishSubject.create();
    }

    @Test
    public void returnsConnectionBackToPool() {
        Connection connection = mockConnection(just(response));
        ConnectionPool pool = mockPool(connection);

        StyxHostHttpClient hostClient = new StyxHostHttpClient(pool);

        StepVerifier.create(hostClient.sendRequest(request))
                .consumeNextWith(response -> response.consume())
                .expectComplete()
                .verify();

        verify(pool).borrowConnection();
        verify(connection).write(any(LiveHttpRequest.class));
        verify(pool).returnConnection(any(Connection.class));
    }

    @Test
    public void ignoresCancelledHeaders() {
        // Request observable unsubscribe/cancel has to be ignored after "onNext" event.
        // This is because Reactor Mono will automatically cancel after an event has
        // been published.
        Connection connection = mockConnection(just(response));
        ConnectionPool pool = mockPool(connection);
        AtomicReference<LiveHttpResponse> transformedResponse = new AtomicReference<>();

        StyxHostHttpClient hostClient = new StyxHostHttpClient(pool);

        // The StepVerifier consumes the response event and then unsubscribes
        // from the response observable.
        StepVerifier.create(hostClient.sendRequest(request))
                .consumeNextWith(transformedResponse::set)
                .verifyComplete();

        // The response is still alive...
        verify(pool, never()).returnConnection(any(Connection.class));
        verify(pool, never()).closeConnection(any(Connection.class));

        // ... until response body is consumed:
        transformedResponse.get().consume();

        // Finally, the connection is returned after the response body is fully consumed:
        verify(pool).returnConnection(any(Connection.class));
    }

    @Test
    public void releasesIfRequestIsCancelledBeforeHeaders() {
        Connection connection = mockConnection(PublishSubject.create());
        ConnectionPool pool = mockPool(connection);

        StyxHostHttpClient hostClient = new StyxHostHttpClient(pool);
        AtomicReference<Subscription> subscription = new AtomicReference<>();

        Flux.from(hostClient.sendRequest(request))
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
    }

    @Test
    public void ignoresResponseObservableErrorsAfterHeaders() {
        Connection connection = mockConnection(responseProvider);
        ConnectionPool pool = mockPool(connection);
        AtomicReference<LiveHttpResponse> newResponse = new AtomicReference<>();

        StyxHostHttpClient hostClient = new StyxHostHttpClient(pool);

        StepVerifier.create(hostClient.sendRequest(request))
                .then(() -> {
                    responseProvider.onNext(response);
                    responseProvider.onError(new RuntimeException("oh dear ..."));
                })
                .consumeNextWith(newResponse::set)
                .expectError()
                .verify();

        newResponse.get().consume();

        verify(pool).returnConnection(any(Connection.class));
    }

    @Test
    public void terminatesConnectionWhenResponseObservableCompletesWithoutHeaders() {
        // A connection that yields no response:
        Connection connection = mockConnection(Observable.empty());
        ConnectionPool pool = mockPool(connection);

        StyxHostHttpClient hostClient = new StyxHostHttpClient(pool);

        StepVerifier.create(hostClient.sendRequest(request))
                .expectNextCount(0)
                .expectComplete()
                .log()
                .verify();

        verify(pool).closeConnection(any(Connection.class));
    }

    @Test
    public void releasesConnectionWhenResponseFailsBeforeHeaders() {
        Connection connection = mockConnection(Observable.error(new RuntimeException()));
        ConnectionPool pool = mockPool(connection);

        StyxHostHttpClient hostClient = new StyxHostHttpClient(pool);

        StepVerifier.create(hostClient.sendRequest(request))
                .expectNextCount(0)
                .expectError()
                .verify();

        verify(pool).closeConnection(any(Connection.class));
    }

    @Test
    public void terminatesConnectionDueToUnsubscribedBody() {
        TestPublisher<Buffer> testPublisher = TestPublisher.create();
        Connection connection = mockConnection(Observable.just(LiveHttpResponse.response(OK).body(new ByteStream(testPublisher)).build()));
        ConnectionPool pool = mockPool(connection);
        AtomicReference<LiveHttpResponse> receivedResponse = new AtomicReference<>();

        StyxHostHttpClient hostClient = new StyxHostHttpClient(pool);

        StepVerifier.create(hostClient.sendRequest(request))
                .consumeNextWith(receivedResponse::set)
                .expectComplete()
                .verify();

        StepVerifier.create(receivedResponse.get().body())
                .thenCancel()
                .verify();

        verify(pool).closeConnection(any(Connection.class));
    }

    @Test
    public void closesTheConnectionPool() {
        ConnectionPool pool = mock(ConnectionPool.class);

        StyxHostHttpClient hostClient = new StyxHostHttpClient(pool);

        hostClient.close();
        verify(pool).close();
    }

    Connection mockConnection(Observable<LiveHttpResponse> responseObservable) {
        Connection connection = mock(Connection.class);
        when(connection.write(any(LiveHttpRequest.class))).thenReturn(responseObservable);
        return connection;
    }

    ConnectionPool mockPool(Connection connection) {
        ConnectionPool pool = mock(ConnectionPool.class);
        when(pool.borrowConnection()).thenReturn(Flux.just(connection));
        return pool;
    }
}
