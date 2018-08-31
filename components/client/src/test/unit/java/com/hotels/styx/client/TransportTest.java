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

import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.client.connectionpool.ConnectionPool;
import com.hotels.styx.api.exceptions.NoAvailableHostsException;
import io.netty.buffer.ByteBuf;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observable;
import rx.Subscription;
import rx.observers.TestSubscriber;
import rx.subjects.PublishSubject;

import java.util.Optional;

import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static io.netty.buffer.Unpooled.copiedBuffer;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static rx.Observable.just;

public class TransportTest {
    private static final String X_STYX_ORIGIN_ID = "X-Styx-Origin-Id";
    private static final Id APP_ID = id("app-01");

    private HttpRequest request;
    private HttpResponse response;
    private Transport transport;
    private PublishSubject<HttpResponse> responseProvider;
    private TestSubscriber<HttpResponse> subscriber;

    @BeforeMethod
    public void setUp() {
        request = get("/").build();
        response = HttpResponse.response(OK).build();
        transport = new Transport(id("x"), X_STYX_ORIGIN_ID);
        responseProvider = PublishSubject.create();
        subscriber = new TestSubscriber<>();
    }

    @Test
    public void returnsConnectionBackToPoolWhenResponseCompletes() {
        Connection connection = mockConnection(just(response));
        ConnectionPool pool = mockPool(connection);

        HttpTransaction transaction = transport.send(request, Optional.of(pool), APP_ID);

        transaction.response().subscribe(subscriber);
        subscriber.awaitTerminalEvent();

        verify(pool).borrowConnection();
        verify(connection).write(any(HttpRequest.class));
        verify(pool).returnConnection(any(Connection.class));
    }

    @Test
    public void releasesConnectionWhenRequestFailsBeforeHeaders() {
        Connection connection = mockConnection(responseProvider);
        ConnectionPool pool = mockPool(connection);

        HttpTransaction transaction = transport.send(request, Optional.of(pool), APP_ID);

        transaction.response().subscribe(new TestSubscriber<>());

        responseProvider.onError(new RuntimeException("Connection failed - before receiving headers."));

        verify(pool).borrowConnection();
        verify(connection).write(any(HttpRequest.class));
        verify(pool).closeConnection(any(Connection.class));
    }

    @Test
    public void releasesConnectionWhenRequestFailsAfterHeaders() {
        ConnectionPool pool = mockPool(mockConnection(responseProvider));

        transport.send(request, Optional.of(pool), APP_ID)
                .response()
                .subscribe(new TestSubscriber<>());

        responseProvider.onNext(response);

        verify(pool, never()).returnConnection(any(Connection.class));
        verify(pool, never()).closeConnection(any(Connection.class));

        responseProvider.onError(new RuntimeException("Connection failed - after the headers"));

        verify(pool).closeConnection(any(Connection.class));
    }

    @Test
    public void releasesIfRequestIsCancelledBeforeHeaders() {
        Connection connection = mockConnection(responseProvider);
        ConnectionPool pool = mockPool(connection);

        HttpTransaction transaction = transport.send(request, Optional.of(pool), APP_ID);

        transaction.response().subscribe(subscriber);

        transaction.cancel();
        verify(pool).closeConnection(any(Connection.class));
    }

    @Test
    public void releasesIfRequestIsCancelledAfterHeaders() {
        Connection connection = mockConnection(responseProvider);
        ConnectionPool pool = mockPool(connection);

        HttpTransaction transaction = transport.send(request, Optional.of(pool), APP_ID);

        transaction.response().subscribe(subscriber);

        responseProvider.onNext(response);

        transaction.cancel();
        verify(pool).closeConnection(any(Connection.class));
    }

    @Test
    public void closesConnectionOnlyOnce() {
        ConnectionPool pool = mockPool(mockConnection(responseProvider));

        HttpTransaction transaction = transport.send(request, Optional.of(pool), APP_ID);

        transaction.response().subscribe(new TestSubscriber<>());

        responseProvider.onNext(response);

        verify(pool, never()).closeConnection(any(Connection.class));

        transaction.cancel();
        transaction.cancel();
        transaction.cancel();

        verify(pool).closeConnection(any(Connection.class));
    }

    @Test
    public void closesIfObservableUnsubscribedBeforeHeaders() {
        ConnectionPool pool = mockPool(mockConnection(PublishSubject.<HttpResponse>create()));

        Subscription subscription = transport.send(request, Optional.of(pool), APP_ID)
                .response()
                .subscribe(new TestSubscriber<>());

        verify(pool, never()).closeConnection(any(Connection.class));

        subscription.unsubscribe();
        verify(pool).closeConnection(any(Connection.class));
    }

    @Test
    public void closesIfObservableUnsubscribedAfterHeaders() {
        ConnectionPool pool = mockPool(mockConnection(responseProvider));

        Subscription subscription = transport.send(request, Optional.of(pool), APP_ID)
                .response()
                .subscribe(new TestSubscriber<>());

        responseProvider.onNext(response);
        verify(pool, never()).closeConnection(any(Connection.class));

        subscription.unsubscribe();
        verify(pool).closeConnection(any(Connection.class));
    }


    @Test
    public void releasesContentStreamBuffersWhenPoolIsNotProvided() {
        ByteBuf chunk1 = copiedBuffer("x", UTF_8);
        ByteBuf chunk2 = copiedBuffer("y", UTF_8);
        ByteBuf chunk3 = copiedBuffer("z", UTF_8);

        StyxObservable<ByteBuf> contentStream = StyxObservable.from(ImmutableList.of(chunk1, chunk2, chunk3));

        HttpRequest aRequest = request
                .newBuilder()
                .body(contentStream)
                .build();

        transport.send(aRequest, Optional.empty(), APP_ID)
                .response()
                .subscribe(subscriber);

        assertThat(chunk1.refCnt(), is(0));
        assertThat(chunk2.refCnt(), is(0));
        assertThat(chunk3.refCnt(), is(0));
    }

    @Test
    public void emitsNoAvailableHostsExceptionWhenPoolIsNotProvided() {

        transport.send(request, Optional.empty(), APP_ID)
                .response()
                .subscribe(subscriber);

        subscriber.awaitTerminalEvent();
        subscriber.assertError(NoAvailableHostsException.class);
    }

    Connection mockConnection(Observable responseObservable) {
        Connection connection = mock(Connection.class);
        when(connection.write(any(HttpRequest.class))).thenReturn(responseObservable);
        return connection;
    }

    ConnectionPool mockPool(Connection connection) {
        ConnectionPool pool = mock(ConnectionPool.class);
        when(pool.borrowConnection()).thenReturn(just(connection));
        return pool;
    }


}