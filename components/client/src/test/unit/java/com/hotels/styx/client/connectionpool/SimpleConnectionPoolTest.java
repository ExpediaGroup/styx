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
package com.hotels.styx.client.connectionpool;

import com.google.common.net.HostAndPort;
import com.hotels.styx.api.Id;
import com.hotels.styx.client.Connection;
import com.hotels.styx.client.ConnectionSettings;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.exceptions.TransportException;
import com.hotels.styx.api.extension.service.ConnectionPoolSettings;
import com.hotels.styx.client.connectionpool.stubs.StubConnectionFactory;
import com.hotels.styx.client.connectionpool.stubs.StubConnectionFactory.StubConnection;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observable;
import rx.Subscriber;
import rx.observers.TestSubscriber;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.hotels.styx.support.api.BlockingObservables.getFirst;
import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.support.matchers.RegExMatcher.matchesRegex;
import static com.hotels.styx.client.connectionpool.ConnectionPoolStatsCounter.NULL_CONNECTION_POOL_STATS;
import static com.hotels.styx.support.OriginHosts.LOCAL_9090;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static rx.Observable.error;
import static rx.Observable.just;

public class SimpleConnectionPoolTest {
    private static final Id ORIGIN = id("foo");

    static final ActionOnOpen FAIL_TO_CONNECT = () -> {
        throw new TransportException("");
    };

    Connection.Factory connectionFactory = new StubConnectionFactory();
    SimpleConnectionPool connectionPool;
    ConnectionPoolSettings config;

    @BeforeMethod
    public void makeHostConnectPool() {
        config = connectionPoolConfig().build();
        connectionPool = originConnectionPool(ORIGIN, LOCAL_9090, connectionFactory, config);
    }

    @Test
    public void closingPoolWillCloseBorrowedConnection() {
        Connection connection = borrowConnectionSynchronously();
        Connection.Listener listener = mock(Connection.Listener.class);
        connection.addConnectionListener(listener);

        connectionPool.close();

        verify(listener).connectionClosed(connection);
    }

    @Test
    public void closingPoolWillCloseAvailableConnection() {
        Connection connection = borrowConnectionSynchronously();
        Connection.Listener listener = mock(Connection.Listener.class);
        connection.addConnectionListener(listener);

        connectionPool.returnConnection(connection);

        connectionPool.close();

        verify(listener).connectionClosed(connection);
    }

    @Test
    public void closingPoolWithTerminateSubscribersWithAnError() throws InterruptedException {
        StubConnectionFactory connectionFactory = new StubConnectionFactory();
        connectionPool = new SimpleConnectionPool(
                origin(),
                connectionPoolConfig()
                        .maxConnectionsPerHost(1)
                        .maxPendingConnectionsPerHost(1)
                        .connectTimeout(1, MILLISECONDS)
                        .build(),
                connectionFactory);

        borrowConnectionSynchronously();

        Observable<Connection> observable = connectionPool.borrowConnection();

        AtomicReference<Connection> connRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        observable.subscribe(new Subscriber<Connection>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
                errorRef.set(e);
            }

            @Override
            public void onNext(Connection connection) {
                connRef.set(connection);
            }
        });

        connectionPool.close();

        assertThat(connRef.get(), is(nullValue()));
        assertThat(errorRef.get(), is(notNullValue()));

        Throwable error = errorRef.get();

        assertThat(error.getMessage(), is("Connection pool closed"));
    }

    @Test
    public void canRoundTripBorrowedConnections() {
        Connection borrowedConnection = borrowConnectionSynchronously();

        assertThat(borrowedConnection, is(notNullValue()));
        assertThat(connectionPool.stats().busyConnectionCount(), is(1));

        boolean connectionClosed = connectionPool.returnConnection(borrowedConnection);
        assertThat(connectionClosed, is(false));
        assertThat(connectionPool.stats().busyConnectionCount(), is(0));
        assertThat(connectionPool.stats().availableConnectionCount(), is(1));
    }

    @Test
    public void returnsAConnectionFromPoolIfPresent() {
        Connection borrowedConnection = borrowConnectionSynchronously();

        connectionPool.returnConnection(borrowedConnection);

        Connection secondConnection = borrowConnectionSynchronously();
        assertThat(secondConnection, is(not(nullValue())));
        assertThat(connectionPool.stats().busyConnectionCount(), is(1));
        assertThat(connectionPool.stats().availableConnectionCount(), is(0));
    }

    @Test
    public void updatesStatsOnConnectionError() {
        Connection.Factory connectionFactory = connectionFactory(FAIL_TO_CONNECT);
        this.connectionPool = connectionPool(connectionPoolConfig(), connectionFactory);

        TestSubscriber<Connection> subscriber = new TestSubscriber<>();
        this.connectionPool.borrowConnection().subscribe(subscriber);

        assertThat(subscriber.getOnErrorEvents().get(0), instanceOf(TransportException.class));
        assertThat(connectionPool.stats().pendingConnectionCount(), is(0));
        assertThat(connectionPool.stats().busyConnectionCount(), is(0));
    }

    @Test
    public void opensANewConnectionWhenNoAvailableFromPool() {
        Connection firstConnection = borrowConnectionSynchronously();
        assertThat(connectionPool.stats().busyConnectionCount(), is(1));
        Connection secondConnection = borrowConnectionSynchronously();

        assertThat(firstConnection, is(notNullValue()));
        assertThat(secondConnection, is(notNullValue()));

        assertThat(connectionPool.stats().busyConnectionCount(), is(2));
    }

    @Test
    public void disregardsReturnConnectionIfTheConnectionIsAlreadyClosed() throws IOException {
        this.connectionPool = connectionPool(connectionPoolConfig()
                .maxConnectionsPerHost(2)
                .connectTimeout(1, MILLISECONDS));

        Connection connection = borrowConnectionSynchronously();
        connectionPool.closeConnection(connection);

        connectionPool.returnConnection(connection);

        assertThat(this.connectionPool.stats().availableConnectionCount(), is(0));
        assertThat(this.connectionPool.stats().busyConnectionCount(), is(0));
    }

    @Test
    public void disregardsCloseConnectionIfTheConnectionIsAlreadyReturned() throws IOException {
        this.connectionPool = connectionPool(connectionPoolConfig()
                .maxConnectionsPerHost(2)
                .connectTimeout(1, MILLISECONDS));

        Connection connection = borrowConnectionSynchronously();
        connectionPool.returnConnection(connection);

        connectionPool.closeConnection(connection);

        assertThat(this.connectionPool.stats().availableConnectionCount(), is(1));
    }

    @Test
    public void discardsTheReturnedConnectionIfNotConnected() throws IOException {
        this.connectionPool = connectionPool(connectionPoolConfig()
                .maxConnectionsPerHost(2)
                .connectTimeout(1, MILLISECONDS));

        Connection oldConnection = borrowConnectionSynchronously();
        connectionPool.closeConnection(oldConnection);

        connectionPool.returnConnection(oldConnection);

        assertThat(this.connectionPool.stats().availableConnectionCount(), is(0));
        assertThat(this.connectionPool.stats().busyConnectionCount(), is(0));
    }

    @Test
    public void willNotCloseTheReturnedConnectionIfHealthy() throws IOException {
        Connection connection = mock(Connection.class);
        when(connection.isConnected()).thenReturn(true);

        connectionPool = originConnectionPool(ORIGIN, LOCAL_9090, connectionFactory, config);
        connectionPool.returnConnection(connection);
        verify(connection, never()).close();
    }

    @Test
    public void willNotOpenANewConnectionIfPendingOpenConnectionsIsOverTheMaximumConfigured() {
        this.connectionPool = connectionPool(connectionPoolConfig()
                .maxConnectionsPerHost(1)
                .maxPendingConnectionsPerHost(0));

        TestSubscriber<Connection> subscriber = new TestSubscriber<>();
        this.connectionPool.borrowConnection().subscribe(subscriber);
        this.connectionPool.borrowConnection().subscribe(subscriber);

        Exception exception = (Exception) subscriber.getOnErrorEvents().get(0);
        assertThat(exception, is(instanceOf(MaxPendingConnectionsExceededException.class)));
        assertThat(exception.getMessage(), matchesRegex("" +
                "Maximum allowed pending connections exceeded for origin=foo:h1:localhost:9090. " +
                "pendingConnectionsCount=[0-9] is greater than maxPendingConnectionsPerHost=[0-9].*"));
    }

    @Test
    public void willPutConnectionRequestInWaitingListIfBusyConnectionCountOverTheMaximumConfigured() {
        this.connectionPool = connectionPool(connectionPoolConfig()
                .maxConnectionsPerHost(1)
                .maxPendingConnectionsPerHost(1)
                .connectTimeout(1, MILLISECONDS));

        borrowConnectionSynchronously();
        this.connectionPool.borrowConnection().subscribe(new TestSubscriber<>());

        assertThat(connectionPool.stats().availableConnectionCount(), is(0));
        assertThat(connectionPool.stats().pendingConnectionCount(), is(1));
    }

    @Test
    public void doesNotBorrowDeadConnectionIfConnectionDiesAfterReturning() throws IOException {
        Connection firstConnection = borrowConnectionSynchronously();
        connectionPool.returnConnection(firstConnection);
        firstConnection.close();

        Connection secondConnection = borrowConnectionSynchronously();
        assertThat(secondConnection.isConnected(), is(true));
    }

    @Test
    public void subscriberWillNotBeOfferedConnectionOnceItsBeenNotifiedOfAnError() {
        Connection.Factory connectionFactory = connectionFactory(FAIL_TO_CONNECT);
        this.connectionPool = connectionPool(connectionPoolConfig(), connectionFactory);

        TestSubscriber subscriber = new TestSubscriber();
        connectionPool.borrowConnection().subscribe(subscriber);

        assertThat(subscriber.getOnErrorEvents().get(0), is(instanceOf(Throwable.class)));
        assertThat(this.connectionPool.stats().pendingConnectionCount(), is(0));
    }

    @Test
    public void willNotHandoutClosedConnection() throws IOException {
        this.connectionPool = connectionPool(connectionPoolConfig()
                .maxConnectionsPerHost(3)
                .connectTimeout(1, MILLISECONDS));

        borrowReturnAndCloseConnection();
        borrowReturnAndCloseConnection();

        Connection connection = borrowConnectionSynchronously();
        assertThat(connection.isConnected(), is(true));
    }

    @Test
    public void removesAvailableConnectionsFromPoolWhenTheyTerminate() throws Exception {
        this.connectionPool = connectionPool(connectionPoolConfig()
                .maxConnectionsPerHost(2)
                .connectTimeout(1, MILLISECONDS));

        List<Connection> connections = populatePool(this.connectionPool, 2);
        closeAll(connections);

        assertThat(this.connectionPool.stats().availableConnectionCount(), is(0));
    }

    @Test
    public void borrowingAllTheConnectionsWillExhaustThePool() {
        this.connectionPool = connectionPool(connectionPoolConfig()
                .maxConnectionsPerHost(1)
                .maxPendingConnectionsPerHost(1)
                .connectTimeout(1, MILLISECONDS));

        // max out the pool
        borrowConnectionSynchronously();
        // max out the pending connections
        this.connectionPool.borrowConnection().subscribe(new TestSubscriber<>());

        assertThat(connectionPool.isExhausted(), is(true));
    }

    @Test
    public void timesOutLongestWaitingSubscribers() {
        TestSubscriber<Connection> subscriber = new TestSubscriber<>();
        this.connectionPool = connectionPool(connectionPoolConfig()
                        .maxConnectionsPerHost(1)
                        .pendingConnectionTimeout(100, MILLISECONDS)
        );

        borrowConnectionSynchronously();
        this.connectionPool.borrowConnection().subscribe(subscriber);

        subscriber.awaitTerminalEvent();
        assertThat(subscriber.getOnErrorEvents().get(0), instanceOf(MaxPendingConnectionTimeoutException.class));
    }

    @Test
    public void doesNotApplyTimeoutAfterEmittingResponse() {
        TestSubscriber<Connection> subscriber = new TestSubscriber<>();
        this.connectionPool = connectionPool(connectionPoolConfig()
                        .maxConnectionsPerHost(1)
                        .pendingConnectionTimeout(100, MILLISECONDS)
        );

        this.connectionPool.borrowConnection().subscribe(subscriber);

        subscriber.awaitTerminalEvent(500, MILLISECONDS);
        assertThat(connectionBorrowed(subscriber), instanceOf(Connection.class));
        subscriber.assertNoErrors();
    }

    @Test
    public void acceptsTtfbValueOfZero() throws IOException {
        Connection connection = mock(Connection.class);
        when(connection.isConnected()).thenReturn(true);
        when(connection.getTimeToFirstByteMillis()).thenReturn(0L);

        connectionPool = originConnectionPool(ORIGIN, LOCAL_9090, connectionFactory, config);
        connectionPool.returnConnection(connection);
        verify(connection, never()).close();
    }

    @Test
    public void shouldCreateAPoolWithoutStats() {
        Origin anyOrigin = newOriginBuilder(LOCAL_9090)
                .build();
        ConnectionPool pool = new SimpleConnectionPool(
                anyOrigin, new ConnectionPoolSettings.Builder().build(), new StubConnectionFactory(), false);

        assertThat(pool.stats(), is(NULL_CONNECTION_POOL_STATS));
    }

    @Test
    public void willNotRecordNegativeLatencyValues() {
        Origin anyOrigin = newOriginBuilder(LOCAL_9090)
                .build();
        Connection.Factory factory = (origin, connectionPoolConfiguration) -> {
            Connection stubConnection = new StubConnection(origin) {
                @Override
                public long getTimeToFirstByteMillis() {
                    return -1L;
                }
            };
            return just(stubConnection);
        };

        ConnectionPool pool = new SimpleConnectionPool(anyOrigin, new ConnectionPoolSettings.Builder().build(), factory, true);

        Connection connection = borrowConnectionSynchronously(pool);
        pool.returnConnection(connection);

        assertThat(pool.stats().timeToFirstByteMs(), is(0L));
    }

    @Test
    public void shouldTimeoutAndLogAfterConfiguredPendingTimeout() {
        this.connectionPool = connectionPool(connectionPoolConfig()
                .maxConnectionsPerHost(1)
                .maxPendingConnectionsPerHost(1)
                .pendingConnectionTimeout(100, MILLISECONDS)
                .connectTimeout(1, MILLISECONDS));

        borrowConnectionSynchronously();
        TestSubscriber<Connection> subscriber = new TestSubscriber<>();
        this.connectionPool.borrowConnection().subscribe(subscriber);
        subscriber.awaitTerminalEvent();

        assertThat(connectionPool.stats().availableConnectionCount(), is(0));
        assertThat(connectionPool.stats().pendingConnectionCount(), is(0));

        assertThat(errorMessage(subscriber), is("Maximum wait time exceeded for origin=foo:h1:localhost:9090. pendingConnectionTimeoutMillis=100"));
    }

    @Test
    public void willTakeConnectionFromWaitingListIfBusyConnectionCountDropsBelowTheMaximumConfigured() {
        this.connectionPool = connectionPool(connectionPoolConfig()
                .maxConnectionsPerHost(1)
                .maxPendingConnectionsPerHost(1)
                .connectTimeout(1, MILLISECONDS));

        Connection connection = borrowConnectionSynchronously();
        TestSubscriber<Connection> subscriber = new TestSubscriber<>();
        this.connectionPool.borrowConnection().subscribe(subscriber);

        assertThat(connectionPool.stats().availableConnectionCount(), is(0));
        assertThat(connectionPool.stats().pendingConnectionCount(), is(1));

        connectionPool.returnConnection(connection);

        assertThat(connectionPool.stats().pendingConnectionCount(), is(0));
        assertThat(connectionBorrowed(subscriber), is(connection));
        assertThat("busy connections", connectionPool.stats().busyConnectionCount(), is(1));
    }

    @Test
    public void returnsConnectionsAfterUse() {
        Connection.Factory factory = mockConnectionFactory();
        SimpleConnectionPool simpleConnectionPool = sizeOnePool(factory);
        AtomicReference<Connection> firstConnection = new AtomicReference<>();

        Observable<String> result = simpleConnectionPool.withConnection(connection -> {
            firstConnection.set(connection);
            return just("foo");
        });

        assertThat(result.toBlocking().single(), is("foo"));
        assertThat(simpleConnectionPool.stats().busyConnectionCount(), is(0));
        assertThat(simpleConnectionPool.stats().availableConnectionCount(), is(1));
        assertThat(simpleConnectionPool.borrowConnection().toBlocking().single(), is(firstConnection.get()));
    }

    @Test
    public void closesConnectionUponError() {
        Connection.Factory factory = mockConnectionFactory();
        SimpleConnectionPool simpleConnectionPool = sizeOnePool(factory);
        AtomicReference<Connection> firstConnection = new AtomicReference<>();

        Observable<String> result = simpleConnectionPool.withConnection(connection -> {
            firstConnection.set(connection);
            return error(new RuntimeException());
        });

        TestSubscriber<String> subscriber = new TestSubscriber<>();
        result.subscribe(subscriber);
        subscriber.assertError(RuntimeException.class);

        assertThat(simpleConnectionPool.stats().busyConnectionCount(), is(0));
        assertThat(simpleConnectionPool.stats().availableConnectionCount(), is(0));
        assertThat(simpleConnectionPool.borrowConnection().toBlocking().single(), is(not(firstConnection.get())));
    }

    @Test
    public void countsConnectionEstablishmentAttempts() throws Exception {
        Connection borrowedConnection = borrowConnectionSynchronously();
        assertThat(borrowedConnection, is(notNullValue()));
        assertThat(connectionPool.stats().connectionAttempts(), is(1));

        boolean connectionClosed = connectionPool.returnConnection(borrowedConnection);
        assertThat(connectionClosed, is(false));

        borrowConnectionSynchronously();
        assertThat(connectionPool.stats().connectionAttempts(), is(1));
    }

    @Test
    public void countsConnectionEstablishmentFailures() throws Exception {
        Connection.Factory connectionFactory = connectionFactory(FAIL_TO_CONNECT);
        this.connectionPool = connectionPool(connectionPoolConfig(), connectionFactory);

        TestSubscriber<Connection> subscriber = new TestSubscriber<>();
        this.connectionPool.borrowConnection().subscribe(subscriber);

        assertThat(connectionPool.stats().connectionFailures(), is(1));
    }

    @Test
    public void countsClosedConnections() throws Exception {
        Connection connection = borrowConnectionSynchronously();
        connectionPool.closeConnection(connection);

        assertThat(this.connectionPool.stats().closedConnections(), is(1));
        assertThat(this.connectionPool.stats().terminatedConnections(), is(1));
    }

    @Test
    public void countsTerminatedConnections() throws Exception {
        this.connectionPool = connectionPool(connectionPoolConfig()
                .maxConnectionsPerHost(2)
                .connectTimeout(1, MILLISECONDS));

        Connection connection = borrowConnectionSynchronously();

        connection.close();

        assertThat(this.connectionPool.stats().terminatedConnections(), is(1));
        assertThat(this.connectionPool.stats().closedConnections(), is(0));
        assertThat("busy connections ", this.connectionPool.stats().busyConnectionCount(), is(0));

        connectionPool.returnConnection(connection);

        assertThat(this.connectionPool.stats().terminatedConnections(), is(1));
        assertThat(this.connectionPool.stats().closedConnections(), is(0));
        assertThat("busy connections ", this.connectionPool.stats().busyConnectionCount(), is(0));
    }

    @Test
    private SimpleConnectionPool sizeOnePool(Connection.Factory factory) {
        return new SimpleConnectionPool.Factory()
                .connectionPoolSettings(new ConnectionPoolSettings(1, 1, 1000, 1000, 1L))
                .connectionFactory(factory)
                .create(origin());
    }

    private Connection.Factory mockConnectionFactory() {
        Connection.Factory factory = mock(Connection.Factory.class);
        when(factory.createConnection(any(Origin.class), any(ConnectionSettings.class))).thenAnswer(invocation -> {
            Connection mock = mock(Connection.class);
            when(mock.isConnected()).thenReturn(true);
            return just(mock);
        });
        return factory;
    }

    private Connection connectionBorrowed(TestSubscriber<Connection> subscriber) {
        return subscriber.getOnNextEvents().get(0);
    }

    private SimpleConnectionPool connectionPool(ConnectionPoolSettings.Builder configuration) {
        return originConnectionPool(ORIGIN, LOCAL_9090, connectionFactory, configuration.build());
    }

    private List<Connection> populatePool(ConnectionPool hostConnectionPool, int numConnections) {
        List<Connection> connections = new ArrayList<>(numConnections);

        for (int i = 0; i < numConnections; i++) {
            connections.add(borrowConnectionSynchronously());
        }

        for (Connection connection : connections) {
            hostConnectionPool.returnConnection(connection);
        }

        return connections;
    }

    private void borrowReturnAndCloseConnection() throws IOException {
        Connection firstConnection = borrowConnectionSynchronously();
        connectionPool.returnConnection(firstConnection);
        firstConnection.close();
    }

    private Connection borrowConnectionSynchronously() {
        Connection connection = getFirst(connectionPool.borrowConnection());
        assertThat(connection, is(notNullValue()));
        return connection;
    }

    private Connection borrowConnectionSynchronously(ConnectionPool connectionPool) {
        Connection connection = getFirst(connectionPool.borrowConnection());
        assertThat(connection, is(notNullValue()));
        return connection;
    }

    private static String errorMessage(TestSubscriber<Connection> subscriber) {
        return subscriber.getOnErrorEvents().get(0).getMessage();
    }

    private static SimpleConnectionPool connectionPool(ConnectionPoolSettings.Builder configuration, Connection.Factory connectionFactory) {
        return originConnectionPool(ORIGIN, LOCAL_9090, connectionFactory, configuration.build());
    }

    private static SimpleConnectionPool originConnectionPool(Id applicationId, HostAndPort host, Connection.Factory factory,
                                                             ConnectionPoolSettings settings) {
        return new SimpleConnectionPool(
                newOriginBuilder(host)
                        .applicationId(applicationId)
                        .id("h1")
                        .build(), settings, factory);
    }

    private static Origin origin() {
        return newOriginBuilder(LOCAL_9090)
                .applicationId(ORIGIN)
                .id("h1")
                .build();
    }

    private static void closeAll(Iterable<Connection> connections) throws IOException {
        for (Connection connection : connections) {
            connection.close();
        }
    }

    private static Connection.Factory connectionFactory(ActionOnOpen actionOnOpen) {
        return (origin, connectionPoolConfiguration) ->
                Observable.create(subscriber -> {
                    try {
                        actionOnOpen.execute();
                        subscriber.onNext(new StubConnection(origin));
                        subscriber.onCompleted();
                    } catch (TransportException e) {
                        subscriber.onError(e);
                    }
                });
    }

    private static ConnectionPoolSettings.Builder connectionPoolConfig() {
        return new ConnectionPoolSettings.Builder()
                .maxConnectionsPerHost(2)
                .connectTimeout(200, MILLISECONDS);
    }

    interface ActionOnOpen {
        void execute() throws TransportException;
    }
}
