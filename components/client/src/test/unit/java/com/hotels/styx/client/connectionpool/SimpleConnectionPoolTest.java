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

import com.hotels.styx.api.exceptions.OriginUnreachableException;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.ConnectionPoolSettings;
import com.hotels.styx.client.Connection;
import com.hotels.styx.client.ConnectionSettings;
import org.mockito.Mockito;
import org.reactivestreams.Publisher;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import rx.Observable;
import rx.subjects.PublishSubject;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.extension.service.ConnectionPoolSettings.defaultConnectionPoolSettings;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class SimpleConnectionPoolTest {
    private final Origin origin = newOriginBuilder("localhost", 9090).build();
    private Connection.Factory connectionFactory;
    private Connection connection1;
    private Connection connection2;
    private Connection connection3;
    private Connection connection4;

    @BeforeMethod
    public void setUp() {
        connectionFactory = mock(Connection.Factory.class);

        connection1 = mock(Connection.class);
        when(connection1.isConnected()).thenReturn(true);

        connection2 = mock(Connection.class);
        when(connection2.isConnected()).thenReturn(true);

        connection3 = mock(Connection.class);
        when(connection3.isConnected()).thenReturn(true);

        connection4 = mock(Connection.class);
        when(connection4.isConnected()).thenReturn(true);
    }

    @Test
    public void borrowsConnection() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class))).thenReturn(Observable.just(connection1));

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        StepVerifier.create(pool.borrowConnection2())
                .expectNext(connection1)
                .verifyComplete();

        assertEquals(pool.stats().connectionAttempts(), 1);
        assertEquals(pool.stats().busyConnectionCount(), 1);
        assertEquals(pool.stats().availableConnectionCount(), 0);
        assertEquals(pool.stats().pendingConnectionCount(), 0);
    }

    @Test
    public void borrowsReturnedConnection() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Observable.just(connection1))
                .thenReturn(Observable.just(connection2));

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        StepVerifier.create(pool.borrowConnection2())
                .expectNext(connection1)
                .verifyComplete();

        assertEquals(pool.stats().connectionAttempts(), 1);
        assertEquals(pool.stats().busyConnectionCount(), 1);
        assertEquals(pool.stats().availableConnectionCount(), 0);

        pool.returnConnection(connection1);

        assertEquals(pool.stats().busyConnectionCount(), 0);
        assertEquals(pool.stats().availableConnectionCount(), 1);

        StepVerifier.create(pool.borrowConnection2())
                .expectNext(connection1)
                .verifyComplete();

        assertEquals(pool.stats().busyConnectionCount(), 1);
        assertEquals(pool.stats().availableConnectionCount(), 0);
    }


    @Test
    public void emitsConnectionWhenOnceEstablished() throws ExecutionException, InterruptedException {
        PublishSubject<Connection> subject = PublishSubject.create();
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(subject);

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        // The pool is empty, so the `borrowConnection` triggers a connection establishment.
        Publisher<Connection> future = pool.borrowConnection2();

        StepVerifier.create(future)
                .then(() -> {
                    assertEquals(pool.stats().pendingConnectionCount(), 1);
                    assertEquals(pool.stats().busyConnectionCount(), 0);
                    assertEquals(pool.stats().availableConnectionCount(), 0);
                })
                .expectNextCount(0)
                .then(() -> {
                    subject.onNext(connection1);
                    subject.onCompleted();
                })
                .expectNext(connection1)
                .verifyComplete();

        assertEquals(pool.stats().connectionAttempts(), 1);
        assertEquals(pool.stats().pendingConnectionCount(), 0);
        assertEquals(pool.stats().busyConnectionCount(), 1);
        assertEquals(pool.stats().availableConnectionCount(), 0);
    }


    @Test
    public void purgesTerminatedConnections() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Observable.just(connection1))
                .thenReturn(Observable.just(connection2));

        // Create a new connection
        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        StepVerifier.create(pool.borrowConnection2())
                .expectNext(connection1)
                .then(() -> pool.returnConnection(connection1))
                .verifyComplete();

        when(connection1.isConnected()).thenReturn(false);
        pool.connectionClosed(connection1);

        StepVerifier.create(pool.borrowConnection2())
                .expectNext(connection2)
                .verifyComplete();

        assertEquals(pool.stats().connectionAttempts(), 2);
        assertEquals(pool.stats().pendingConnectionCount(), 0);
        assertEquals(pool.stats().busyConnectionCount(), 1);
        assertEquals(pool.stats().availableConnectionCount(), 0);

        assertEquals(pool.stats().terminatedConnections(), 1);
        assertEquals(pool.stats().closedConnections(), 0);
    }

    @Test
    public void returnsConnectionToWaitingSubscribers() throws ExecutionException, InterruptedException {
        PublishSubject<Connection> subject = PublishSubject.create();
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Observable.just(connection1))
                .thenReturn(subject);
        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        // The pool is empty, so the `borrowConnection` triggers a connection establishment.
        StepVerifier.create(pool.borrowConnection2())
                .expectNext(connection1)
                .verifyComplete();

        // Another borrow comes in, and triggers a connection establishment.
        // The subject keeps the connection perpetually in "3-way handshake"
        Publisher<Connection> publisher = pool.borrowConnection2();
        CompletableFuture<Connection> future = Mono.from(publisher).toFuture();

        assertEquals(pool.stats().connectionAttempts(), 2);
        assertEquals(pool.stats().pendingConnectionCount(), 1);
        assertEquals(pool.stats().busyConnectionCount(), 1);
        assertEquals(pool.stats().availableConnectionCount(), 0);

        // The returned connection is now given to the waiting subscriber:
        pool.returnConnection(connection1);

        assertTrue(future.isDone());
        assertEquals(future.get(), connection1);

        assertEquals(pool.stats().connectionAttempts(), 2);
        assertEquals(pool.stats().pendingConnectionCount(), 0);
        assertEquals(pool.stats().busyConnectionCount(), 1);
        assertEquals(pool.stats().availableConnectionCount(), 0);
    }

    @Test
    public void returnConnectionChecksIfConnectionIsClosed() {
        PublishSubject<Connection> subject = PublishSubject.create();
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Observable.just(connection1))
                .thenReturn(subject);
        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        // The pool is empty, so the `borrowConnection` triggers a connection establishment.
        StepVerifier.create(pool.borrowConnection2())
                .expectNext(connection1)
                .verifyComplete();

        // Another borrow comes in, and triggers a connection establishment.
        // The subject keeps the connection perpetually in "3-way handshake"
        CompletableFuture<Connection> future = Mono.from(pool.borrowConnection2()).toFuture();
        assertFalse(future.isDone());

        assertEquals(pool.stats().connectionAttempts(), 2);
        assertEquals(pool.stats().pendingConnectionCount(), 1);
        assertEquals(pool.stats().busyConnectionCount(), 1);
        assertEquals(pool.stats().availableConnectionCount(), 0);

        // The returned connection is closed, and then returned:
        when(connection1.isConnected()).thenReturn(false);
        pool.returnConnection(connection1);

        // The closed connection is discarded, and the subscriber still waits for the 3-way handshake
        assertFalse(future.isDone());

        assertEquals(pool.stats().connectionAttempts(), 2);
        assertEquals(pool.stats().pendingConnectionCount(), 1);
        assertEquals(pool.stats().busyConnectionCount(), 0);
        assertEquals(pool.stats().availableConnectionCount(), 0);
        assertEquals(pool.stats().terminatedConnections(), 0);
        assertEquals(pool.stats().closedConnections(), 0);

        // Finally the connectionClosed callback fires:
        pool.connectionClosed(connection1);

        assertEquals(pool.stats().terminatedConnections(), 1);
        assertEquals(pool.stats().closedConnections(), 0);
    }


    @Test
    public void unsubscribingRemovesTheWaitingSubscriber() throws ExecutionException, InterruptedException {
        PublishSubject<Connection> subject1 = PublishSubject.create();
        PublishSubject<Connection> subject2 = PublishSubject.create();
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(subject1)
                .thenReturn(subject2);
        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        // The subscriber is pending because the connection is still 3-way handshaking
        CompletableFuture<Connection> future1 = Mono.from(pool.borrowConnection2()).toFuture();

        // Borrow another one:
        CompletableFuture<Connection> future2 = Mono.from(pool.borrowConnection2()).toFuture();

        assertEquals(pool.stats().pendingConnectionCount(), 2);

        // `Future1` is ahead of `future2` in the waiting subscribers.
        // Cancel the future1 and verify that `future2` gets it spot:
        future1.cancel(true);

        assertEquals(pool.stats().pendingConnectionCount(), 1);

        subject1.onNext(connection1);
        subject1.onCompleted();

        assertEquals(pool.stats().pendingConnectionCount(), 0);
        assertEquals(pool.stats().availableConnectionCount(), 0);
        assertEquals(pool.stats().busyConnectionCount(), 1);

        assertTrue(future1.isCompletedExceptionally());
        assertEquals(future2.get(), connection1);
    }


    // 2. The connection pool limits
    @Test
    public void limitsPendingConnectionsDueToConnectionEstablishment() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(PublishSubject.create())
                .thenReturn(PublishSubject.create())
                .thenReturn(PublishSubject.create());

        ConnectionPoolSettings poolSettings = new ConnectionPoolSettings.Builder()
                .maxPendingConnectionsPerHost(2)
                .build();

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, poolSettings, connectionFactory);

        CompletableFuture<Connection> pending1 = Mono.from(pool.borrowConnection2()).toFuture();
        CompletableFuture<Connection> pending2 = Mono.from(pool.borrowConnection2()).toFuture();

        StepVerifier.create(pool.borrowConnection2())
                .expectError(MaxPendingConnectionsExceededException.class)
                .verify();

        assertEquals(pool.stats().connectionAttempts(), 2);
        assertEquals(pool.stats().pendingConnectionCount(), 2);

    }

    // 2. The connection pool limits
    @Test
    public void limitsPendingConnectionsDueToPoolSaturation() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Observable.just(connection1))
                .thenReturn(Observable.just(connection2))
                .thenReturn(Observable.just(connection3))
                .thenReturn(Observable.just(connection4));

        ConnectionPoolSettings poolSettings = new ConnectionPoolSettings.Builder()
                .maxConnectionsPerHost(2)
                .maxPendingConnectionsPerHost(2)
                .build();

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, poolSettings, connectionFactory);

        // Saturate active connections:
        CompletableFuture<Connection> pending1 = Mono.from(pool.borrowConnection2()).toFuture();
        CompletableFuture<Connection> pending2 = Mono.from(pool.borrowConnection2()).toFuture();
        assertTrue(pending1.isDone());
        assertTrue(pending2.isDone());

        // These are pending
        CompletableFuture<Connection> pending3 = Mono.from(pool.borrowConnection2()).toFuture();
        CompletableFuture<Connection> pending4 = Mono.from(pool.borrowConnection2()).toFuture();

        assertFalse(pending3.isDone());
        assertFalse(pending4.isDone());

        // This throws an error:
        StepVerifier.create(pool.borrowConnection2())
                .expectError(MaxPendingConnectionsExceededException.class)
                .verify();

        assertEquals(pool.stats().connectionAttempts(), 2);
        assertEquals(pool.stats().pendingConnectionCount(), 2);
        assertEquals(pool.stats().busyConnectionCount(), 2);
        assertEquals(pool.stats().availableConnectionCount(), 0);
        assertEquals(pool.stats().terminatedConnections(), 0);
        assertEquals(pool.stats().closedConnections(), 0);
    }


    // 2. The connection pool limits
    @Test
    public void returnConnectionDecrementsConnectionCount() throws ExecutionException, InterruptedException, TimeoutException {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Observable.just(connection1))
                .thenReturn(Observable.just(connection2))
                .thenReturn(Observable.just(connection3))
                .thenReturn(Observable.just(connection4));

        ConnectionPoolSettings poolSettings = new ConnectionPoolSettings.Builder()
                .maxConnectionsPerHost(2)
                .maxPendingConnectionsPerHost(2)
                .build();

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, poolSettings, connectionFactory);

        // Saturate active connections:
        CompletableFuture<Connection> active1 = Mono.from(pool.borrowConnection2()).toFuture();
        CompletableFuture<Connection> active2 = Mono.from(pool.borrowConnection2()).toFuture();

        assertTrue(active1.isDone());
        assertTrue(active2.isDone());
        assertEquals(pool.stats().connectionAttempts(), 2);
        assertEquals(pool.stats().busyConnectionCount(), 2);

        // Create two pending connections
        CompletableFuture<Connection> pending1 = Mono.from(pool.borrowConnection2()).toFuture();
        CompletableFuture<Connection> pending2 = Mono.from(pool.borrowConnection2()).toFuture();

        assertFalse(pending1.isDone());
        assertFalse(pending2.isDone());
        assertEquals(pool.stats().connectionAttempts(), 2);
        assertEquals(pool.stats().busyConnectionCount(), 2);
        assertEquals(pool.stats().pendingConnectionCount(), 2);

        // Return active connections
        pool.returnConnection(active1.get());
        pool.returnConnection(active2.get());

        // And ensure pending connections complete
        assertEquals(pending1.get(), connection1);
        assertEquals(pending2.get(), connection2);
        assertEquals(pool.stats().connectionAttempts(), 2);
        assertEquals(pool.stats().busyConnectionCount(), 2);
        assertEquals(pool.stats().pendingConnectionCount(), 0);

        // Returning connections ...
        pool.returnConnection(connection1);
        pool.returnConnection(connection2);

        assertEquals(pool.stats().connectionAttempts(), 2);
        assertEquals(pool.stats().busyConnectionCount(), 0);
        assertEquals(pool.stats().pendingConnectionCount(), 0);

        // .. allows more connections to be borrowed:
        StepVerifier.create(pool.borrowConnection2())
                .expectNext(connection1)
                .verifyComplete();

        StepVerifier.create(pool.borrowConnection2())
                .expectNext(connection2)
                .verifyComplete();

        assertEquals(pool.stats().connectionAttempts(), 2);
        assertEquals(pool.stats().pendingConnectionCount(), 0);
        assertEquals(pool.stats().busyConnectionCount(), 2);
        assertEquals(pool.stats().availableConnectionCount(), 0);
        assertEquals(pool.stats().terminatedConnections(), 0);
        assertEquals(pool.stats().closedConnections(), 0);
    }

    @Test
    public void closesConnections() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Observable.just(connection1))
                .thenReturn(Observable.just(connection2))
                .thenReturn(Observable.just(connection3))
                .thenReturn(Observable.just(connection4));

        ConnectionPoolSettings poolSettings = new ConnectionPoolSettings.Builder()
                .maxConnectionsPerHost(2)
                .maxPendingConnectionsPerHost(2)
                .build();

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, poolSettings, connectionFactory);

        StepVerifier.create(pool.borrowConnection2())
                .expectNext(connection1)
                .verifyComplete();

        pool.closeConnection(connection1);

        verify(connection1).close();
        assertEquals(pool.stats().terminatedConnections(), 0);
        assertEquals(pool.stats().closedConnections(), 1);
    }

    @Test
    public void closeConnectionDecrementsBorrowedCount() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Observable.just(connection1))
                .thenReturn(Observable.just(connection2))
                .thenReturn(Observable.just(connection3))
                .thenReturn(Observable.just(connection4));

        ConnectionPoolSettings poolSettings = new ConnectionPoolSettings.Builder()
                .maxConnectionsPerHost(2)
                .maxPendingConnectionsPerHost(2)
                .build();

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, poolSettings, connectionFactory);

        // Saturate pool:
        StepVerifier.create(pool.borrowConnection2())
                .expectNext(connection1)
                .verifyComplete();

        StepVerifier.create(pool.borrowConnection2())
                .expectNext(connection2)
                .verifyComplete();

        assertEquals(pool.stats().busyConnectionCount(), 2);
        assertEquals(pool.stats().terminatedConnections(), 0);
        assertEquals(pool.stats().closedConnections(), 0);

        // Close a connection
        pool.closeConnection(connection1);

        assertEquals(pool.stats().busyConnectionCount(), 1);
        assertEquals(pool.stats().closedConnections(), 1);

        // A new connection has been established ...
        // proving that the borrowed count was decremented
        StepVerifier.create(pool.borrowConnection2())
                .expectNext(connection3)
                .verifyComplete();

        assertEquals(pool.stats().busyConnectionCount(), 2);
        assertEquals(pool.stats().terminatedConnections(), 0);
        assertEquals(pool.stats().closedConnections(), 1);
    }


    @Test
    public void closeConnectionTriggersConnectionEstablishment() throws ExecutionException, InterruptedException {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Observable.just(connection1))
                .thenReturn(Observable.just(connection2))
                .thenReturn(Observable.just(connection3))
                .thenReturn(Observable.just(connection4));

        ConnectionPoolSettings poolSettings = new ConnectionPoolSettings.Builder()
                .maxConnectionsPerHost(2)
                .maxPendingConnectionsPerHost(2)
                .build();

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, poolSettings, connectionFactory);

        // Saturate pool:
        StepVerifier.create(pool.borrowConnection2())
                .expectNext(connection1)
                .verifyComplete();

        StepVerifier.create(pool.borrowConnection2())
                .expectNext(connection2)
                .verifyComplete();

        assertEquals(pool.stats().busyConnectionCount(), 2);
        assertEquals(pool.stats().availableConnectionCount(), 0);

        // Create a pending connection
        CompletableFuture<Connection> pending1 = Mono.from(pool.borrowConnection2()).toFuture();
        assertFalse(pending1.isDone());

        assertEquals(pool.stats().busyConnectionCount(), 2);
        assertEquals(pool.stats().pendingConnectionCount(), 1);
        assertEquals(pool.stats().availableConnectionCount(), 0);

        // Close a previously borrowed connection:
        pool.closeConnection(connection1);

        // A new connection is established and handed over to pending subscriber:
        assertEquals(pending1.get(), connection3);
        assertEquals(pool.stats().busyConnectionCount(), 2);
        assertEquals(pool.stats().pendingConnectionCount(), 0);
        assertEquals(pool.stats().availableConnectionCount(), 0);
        assertEquals(pool.stats().closedConnections(), 1);
    }


    @Test
    public void idleActiveConnectionMakesRoomForOthers() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Observable.just(connection1))
                .thenReturn(Observable.just(connection2))
                .thenReturn(Observable.just(connection3))
                .thenReturn(Observable.just(connection4));

        ConnectionPoolSettings poolSettings = new ConnectionPoolSettings.Builder()
                .maxConnectionsPerHost(2)
                .maxPendingConnectionsPerHost(2)
                .build();

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, poolSettings, connectionFactory);

        // Create a new connection
        StepVerifier.create(pool.borrowConnection2())
                .expectNext(connection1)
                .verifyComplete();

        assertEquals(pool.stats().busyConnectionCount(), 1);
        assertEquals(pool.stats().availableConnectionCount(), 0);

        // Put it back to pool
        pool.returnConnection(connection1);

        assertEquals(pool.stats().busyConnectionCount(), 0);
        assertEquals(pool.stats().availableConnectionCount(), 1);

        // Connection terminates while it is in active queue
        // Note: `connectionClosed` is a notification, NOT a formal API for returning a connection.
        // therefore `availableConnections` metric stays at 1.
        when(connection1.isConnected()).thenReturn(false);
        pool.connectionClosed(connection1);

        assertEquals(pool.stats().busyConnectionCount(), 0);
        assertEquals(pool.stats().availableConnectionCount(), 1);
        assertEquals(pool.stats().closedConnections(), 0);
        assertEquals(pool.stats().terminatedConnections(), 1);

        // Connection closure doesn't affect subsequent borrows:
        StepVerifier.create(pool.borrowConnection2())
                .expectNext(connection2)
                .verifyComplete();

        StepVerifier.create(pool.borrowConnection2())
                .expectNext(connection3)
                .verifyComplete();

        assertEquals(pool.stats().busyConnectionCount(), 2);
        assertEquals(pool.stats().availableConnectionCount(), 0);
        assertEquals(pool.stats().closedConnections(), 0);
        assertEquals(pool.stats().terminatedConnections(), 1);
    }

    @Test
    public void borrowRetriesThreeTimesOnConnectionEstablishmentFailure() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Observable.error(new OriginUnreachableException(origin, new RuntimeException())))
                .thenReturn(Observable.error(new OriginUnreachableException(origin, new RuntimeException())))
                .thenReturn(Observable.just(connection4));

        ConnectionPoolSettings poolSettings = new ConnectionPoolSettings.Builder()
                .maxConnectionsPerHost(2)
                .maxPendingConnectionsPerHost(2)
                .build();

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, poolSettings, connectionFactory);

        StepVerifier.create(pool.borrowConnection2())
                .expectNext(connection4)
                .verifyComplete();
    }

    @Test
    public void connectionEstablishmentFailureRetryThreeTimesAtConnectionClosure() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Observable.just(connection1))
                .thenReturn(Observable.error(new OriginUnreachableException(origin, new RuntimeException())))
                .thenReturn(Observable.error(new OriginUnreachableException(origin, new RuntimeException())))
                .thenReturn(Observable.just(connection4));

        ConnectionPoolSettings poolSettings = new ConnectionPoolSettings.Builder()
                .maxConnectionsPerHost(2)
                .maxPendingConnectionsPerHost(2)
                .build();

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, poolSettings, connectionFactory);

        StepVerifier.create(pool.borrowConnection2())
                .expectNext(connection1)
                .then(() -> {
                    assertEquals(pool.stats().availableConnectionCount(), 0);
                    assertEquals(pool.stats().closedConnections(), 0);
                    assertEquals(pool.stats().pendingConnectionCount(), 0);
                    assertEquals(pool.stats().busyConnectionCount(), 1);
                })
                .then(() -> pool.closeConnection(connection1))
                .then(() -> {
                    assertEquals(pool.stats().availableConnectionCount(), 1);
                    assertEquals(pool.stats().closedConnections(), 1);
                    assertEquals(pool.stats().pendingConnectionCount(), 0);
                    assertEquals(pool.stats().busyConnectionCount(), 0);
                })
                .verifyComplete();
    }

    @Test
    public void connectionEstablishmentFailureRetryThreeTimesOnlyAtBorrow() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Observable.error(new OriginUnreachableException(origin, new RuntimeException())))
                .thenReturn(Observable.error(new OriginUnreachableException(origin, new RuntimeException())))
                .thenReturn(Observable.error(new OriginUnreachableException(origin, new RuntimeException())));

        ConnectionPoolSettings poolSettings = new ConnectionPoolSettings.Builder()
                .maxConnectionsPerHost(2)
                .maxPendingConnectionsPerHost(2)
                .build();

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, poolSettings, connectionFactory);

        Mono.from(pool.borrowConnection2()).subscribe();

        assertEquals(pool.stats().pendingConnectionCount(), 1);
        assertEquals(pool.stats().connectionFailures(), 1);
        assertEquals(pool.stats().availableConnectionCount(), 0);
        assertEquals(pool.stats().closedConnections(), 0);
        assertEquals(pool.stats().terminatedConnections(), 0);
    }

    @Test
    public void connectionEstablishmentFailureRetryThreeTimesOnlyAtConnectionClosure() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Observable.just(connection1))
                .thenReturn(Observable.error(new OriginUnreachableException(origin, new RuntimeException())))
                .thenReturn(Observable.error(new OriginUnreachableException(origin, new RuntimeException())))
                .thenReturn(Observable.error(new OriginUnreachableException(origin, new RuntimeException())));

        ConnectionPoolSettings poolSettings = new ConnectionPoolSettings.Builder()
                .maxConnectionsPerHost(2)
                .maxPendingConnectionsPerHost(2)
                .build();

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, poolSettings, connectionFactory);

        StepVerifier.create(pool.borrowConnection2())
                .expectNext(connection1)
                .then(() -> {
                    assertEquals(pool.stats().availableConnectionCount(), 0);
                    assertEquals(pool.stats().closedConnections(), 0);
                    assertEquals(pool.stats().pendingConnectionCount(), 0);
                    assertEquals(pool.stats().busyConnectionCount(), 1);
                })
                .then(() -> {
                    pool.closeConnection(connection1);
                    pool.connectionClosed(connection1);
                })
                .then(() -> {
                    assertEquals(pool.stats().connectionFailures(), 1);
                    assertEquals(pool.stats().pendingConnectionCount(), 0);
                    assertEquals(pool.stats().availableConnectionCount(), 0);
                    assertEquals(pool.stats().closedConnections(), 1);
                    assertEquals(pool.stats().terminatedConnections(), 1);
                })
                .verifyComplete();
    }

    @Test
    public void pendingConnectionTimeout() {
        PublishSubject<Connection> subject = PublishSubject.create();
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(subject);

        ConnectionPoolSettings poolSettings = new ConnectionPoolSettings.Builder()
                .pendingConnectionTimeout(500, MILLISECONDS)
                .build();

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, poolSettings, connectionFactory);

        StepVerifier.create(pool.borrowConnection2())
                .expectError(MaxPendingConnectionTimeoutException.class)
                .verifyThenAssertThat()
                .tookMoreThan(Duration.ofMillis(500));

        // And then ensure connection is placed in the active queue:
        subject.onNext(mock(Connection.class));

        assertEquals(pool.stats().availableConnectionCount(), 1);
        assertEquals(pool.stats().pendingConnectionCount(), 0);
        assertEquals(pool.stats().busyConnectionCount(), 0);
        assertEquals(pool.stats().connectionAttempts(), 1);
    }

    @Test
    public void registersAsConnectionListener() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Observable.just(connection1));

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        StepVerifier.create(pool.borrowConnection2())
                .consumeNextWith(pool::returnConnection)
                .verifyComplete();

        verify(connection1).addConnectionListener(Mockito.eq(pool));
    }
}
