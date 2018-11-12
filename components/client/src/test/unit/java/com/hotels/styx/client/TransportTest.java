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
import com.hotels.styx.api.Id;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.client.connectionpool.ConnectionPool;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;
import rx.Observable;
import rx.observers.TestSubscriber;
import rx.subjects.PublishSubject;

import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static rx.Observable.just;

public class TransportTest {
    private static final String X_STYX_ORIGIN_ID = "X-Styx-Origin-Id";
    private static final Id APP_ID = id("app-01");

    private LiveHttpRequest request;
    private LiveHttpResponse response;
    private Transport transport;
    private PublishSubject<LiveHttpResponse> responseProvider;
    private TestSubscriber<LiveHttpResponse> subscriber;

    @BeforeMethod
    public void setUp() {
        request = get("/").build();
        response = LiveHttpResponse.response(OK).build();
        transport = new Transport();
        responseProvider = PublishSubject.create();
        subscriber = new TestSubscriber<>();
    }

    @Test
    public void returnsConnectionBackToPool() {
        Connection connection = mockConnection(just(response));
        ConnectionPool pool = mockPool(connection);

        HttpTransaction transaction = transport.send(request, pool);

        transaction.response()
                .toBlocking()
                .single()
                .consume();

        verify(pool).borrowConnection();
        verify(connection).write(any(LiveHttpRequest.class));
        verify(pool).returnConnection(any(Connection.class));
    }

    @Test
    public void returnsConnectionBackToPoolDueToCancelledHeaders() {
        Connection connection = mockConnection(just(response));
        ConnectionPool pool = mockPool(connection);

        transport.send(request, pool)
                .response()
                .subscribe(subscriber);

        LiveHttpResponse response2 = subscriber.getOnNextEvents().get(0);

        subscriber.unsubscribe();

        response2.consume();

        verify(pool).returnConnection(any(Connection.class));
    }

    @Test
    public void releasesIfRequestIsCancelledBeforeHeaders() {
        Connection connection = mockConnection(PublishSubject.create());
        ConnectionPool pool = mockPool(connection);

        transport.send(request, pool)
                .response()
                .subscribe(subscriber);

        assertTrue(subscriber.getOnNextEvents().isEmpty());
        assertTrue(subscriber.getOnCompletedEvents().isEmpty());
        assertTrue(subscriber.getOnErrorEvents().isEmpty());

        subscriber.unsubscribe();

        verify(pool).closeConnection(any(Connection.class));
    }

    @Test
    public void ignoresRequestCancellationAfterHeaders() {
        // Request observable unsubscribe/cancel has to be ignored after "onNext" event.
        // This is because Reactor Mono will automatically cancel after an event has
        // been published.

        TestPublisher<Buffer> testPublisher = TestPublisher.create();
        Connection connection = mockConnection(responseProvider);
        ConnectionPool pool = mockPool(connection);

        transport.send(request, pool)
                .response()
                .subscribe(subscriber);

        responseProvider.onNext(LiveHttpResponse.response(OK).body(new ByteStream(testPublisher)).build());

        assertEquals(subscriber.getOnNextEvents().size(), 1);
        assertTrue(subscriber.getOnCompletedEvents().isEmpty());
        assertTrue(subscriber.getOnErrorEvents().isEmpty());

        subscriber.unsubscribe();

        verify(pool, never()).closeConnection(any(Connection.class));
        verify(pool, never()).returnConnection(any(Connection.class));
    }

    @Test
    public void returnsConnectionBackToPoolDueToDelayedResponseError() {
        Connection connection = mockConnection(responseProvider);
        ConnectionPool pool = mockPool(connection);

        transport.send(request, pool)
                .response()
                .subscribe(subscriber);

        responseProvider.onNext(response);
        responseProvider.onError(new RuntimeException("oh dear ..."));

        subscriber.getOnNextEvents().get(0).consume();

        verify(pool).returnConnection(any(Connection.class));
    }

    @Test
    public void terminatesConnectionDueToObservableEmittingOnCompleteWithoutHeaders() {
        Connection connection = mockConnection(Observable.empty());
        ConnectionPool pool = mockPool(connection);

        transport.send(request, pool)
                .response()
                .subscribe(subscriber);

        assertThat(subscriber.getOnNextEvents().isEmpty(), is(true));
        assertThat(subscriber.getOnCompletedEvents().size(), is(1));

        verify(pool).closeConnection(any(Connection.class));
    }

    @Test
    public void releasesConnectionWhenRequestFailsBeforeHeaders() {
        Connection connection = mockConnection(Observable.error(new RuntimeException()));
        ConnectionPool pool = mockPool(connection);

        transport.send(request, pool)
                .response()
                .subscribe(subscriber);

        assertThat(subscriber.getOnNextEvents().isEmpty(), is(true));
        assertThat(subscriber.getOnErrorEvents().size(), is(1));

        verify(pool).closeConnection(any(Connection.class));
    }

    @Test
    public void terminatesConnectionDueToUnsubscribedBody() {
        TestPublisher<Buffer> testPublisher = TestPublisher.create();
        Connection connection = mockConnection(Observable.just(LiveHttpResponse.response(OK).body(new ByteStream(testPublisher)).build()));
        ConnectionPool pool = mockPool(connection);

        transport.send(request, pool)
                .response()
                .subscribe(subscriber);

        assertEquals(subscriber.getOnNextEvents().size(), 1);
        assertEquals(subscriber.getOnCompletedEvents().size(), 1);

        StepVerifier.create(subscriber.getOnNextEvents().get(0).body())
                .thenCancel()
                .verify();

        verify(pool).closeConnection(any(Connection.class));
    }


    Connection mockConnection(Observable responseObservable) {
        Connection connection = mock(Connection.class);
        when(connection.write(any(LiveHttpRequest.class))).thenReturn(responseObservable);
        return connection;
    }

    ConnectionPool mockPool(Connection connection) {
        ConnectionPool pool = mock(ConnectionPool.class);
        when(pool.borrowConnection()).thenReturn(just(connection));
        return pool;
    }


}