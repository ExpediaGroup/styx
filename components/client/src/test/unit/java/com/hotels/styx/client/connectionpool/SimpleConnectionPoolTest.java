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
package com.hotels.styx.client.connectionpool;

import com.hotels.styx.api.exceptions.OriginUnreachableException;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.ConnectionPoolSettings;
import com.hotels.styx.client.Connection;
import com.hotels.styx.client.ConnectionSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.reactivestreams.Publisher;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.extension.service.ConnectionPoolSettings.defaultConnectionPoolSettings;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SimpleConnectionPoolTest {
    private final Origin origin = newOriginBuilder("localhost", 9090).build();
    private Connection.Factory connectionFactory;
    private Connection connection1;
    private Connection connection2;
    private Connection connection3;
    private Connection connection4;

    @BeforeEach
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
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class))).thenReturn(Mono.just(connection1));

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        StepVerifier.create(pool.borrowConnection())
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
                .thenReturn(Mono.just(connection1))
                .thenReturn(Mono.just(connection2));

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        StepVerifier.create(pool.borrowConnection())
                .expectNext(connection1)
                .verifyComplete();

        assertEquals(pool.stats().connectionAttempts(), 1);
        assertEquals(pool.stats().busyConnectionCount(), 1);
        assertEquals(pool.stats().availableConnectionCount(), 0);

        pool.returnConnection(connection1);

        assertEquals(pool.stats().busyConnectionCount(), 0);
        assertEquals(pool.stats().availableConnectionCount(), 1);

        StepVerifier.create(pool.borrowConnection())
                .expectNext(connection1)
                .verifyComplete();

        assertEquals(pool.stats().busyConnectionCount(), 1);
        assertEquals(pool.stats().availableConnectionCount(), 0);
    }


    @Test
    public void emitsConnectionWhenOnceEstablished() {
        EmitterProcessor<Connection> processor = EmitterProcessor.create();
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Mono.from(processor));

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        // The pool is empty, so the `borrowConnection` triggers a connection establishment.
        Publisher<Connection> future = pool.borrowConnection();

        StepVerifier.create(future)
                .then(() -> {
                    assertEquals(pool.stats().pendingConnectionCount(), 1);
                    assertEquals(pool.stats().busyConnectionCount(), 0);
                    assertEquals(pool.stats().availableConnectionCount(), 0);
                })
                .expectNextCount(0)
                .then(() -> {
                    processor.onNext(connection1);
                    processor.onComplete();
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
                .thenReturn(Mono.just(connection1))
                .thenReturn(Mono.just(connection2));

        // Create a new connection
        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        StepVerifier.create(pool.borrowConnection())
                .expectNext(connection1)
                .then(() -> pool.returnConnection(connection1))
                .verifyComplete();

        when(connection1.isConnected()).thenReturn(false);
        pool.connectionClosed(connection1);

        StepVerifier.create(pool.borrowConnection())
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
        EmitterProcessor<Connection> processor = EmitterProcessor.create();
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Mono.just(connection1))
                .thenReturn(Mono.from(processor));
        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        // The pool is empty, so the `borrowConnection` triggers a connection establishment.
        StepVerifier.create(pool.borrowConnection())
                .expectNext(connection1)
                .verifyComplete();

        // Another borrow comes in, and triggers a connection establishment.
        // The processor keeps the connection perpetually in "3-way handshake"
        Publisher<Connection> publisher = pool.borrowConnection();
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
        EmitterProcessor<Connection> processor = EmitterProcessor.create();
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Mono.just(connection1))
                .thenReturn(Mono.from(processor));
        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        // The pool is empty, so the `borrowConnection` triggers a connection establishment.
        StepVerifier.create(pool.borrowConnection())
                .expectNext(connection1)
                .verifyComplete();

        // Another borrow comes in, and triggers a connection establishment.
        // The processor keeps the connection perpetually in "3-way handshake"
        CompletableFuture<Connection> future = Mono.from(pool.borrowConnection()).toFuture();
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
        EmitterProcessor<Connection> processor1 = EmitterProcessor.create();
        EmitterProcessor<Connection> processor2 = EmitterProcessor.create();
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Mono.from(processor1))
                .thenReturn(Mono.from(processor2));
        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        // The subscriber is pending because the connection is still 3-way handshaking
        CompletableFuture<Connection> future1 = Mono.from(pool.borrowConnection()).toFuture();

        // Borrow another one:
        CompletableFuture<Connection> future2 = Mono.from(pool.borrowConnection()).toFuture();

        assertEquals(pool.stats().pendingConnectionCount(), 2);

        // `Future1` is ahead of `future2` in the waiting subscribers.
        // Cancel the future1 and verify that `future2` gets it spot:
        future1.cancel(true);

        assertEquals(pool.stats().pendingConnectionCount(), 1);

        processor1.onNext(connection1);
        processor1.onComplete();

        assertEquals(pool.stats().pendingConnectionCount(), 0);
        assertEquals(pool.stats().availableConnectionCount(), 0);
        assertEquals(pool.stats().busyConnectionCount(), 1);

        assertTrue(future1.isCompletedExceptionally());
        assertEquals(future2.get(), connection1);
    }


    @Test
    public void limitsPendingConnectionsDueToConnectionEstablishment() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Mono.from(EmitterProcessor.create()))
                .thenReturn(Mono.from(EmitterProcessor.create()))
                .thenReturn(Mono.from(EmitterProcessor.create()));

        ConnectionPoolSettings poolSettings = new ConnectionPoolSettings.Builder()
                .maxPendingConnectionsPerHost(2)
                .build();

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, poolSettings, connectionFactory);

        Mono.from(pool.borrowConnection()).toFuture();
        Mono.from(pool.borrowConnection()).toFuture();

        StepVerifier.create(pool.borrowConnection())
                .expectError(MaxPendingConnectionsExceededException.class)
                .verify();

        assertEquals(pool.stats().connectionAttempts(), 2);
        assertEquals(pool.stats().pendingConnectionCount(), 2);

    }

    @Test
    public void limitsPendingConnectionsDueToPoolSaturation() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Mono.just(connection1))
                .thenReturn(Mono.just(connection2))
                .thenReturn(Mono.just(connection3))
                .thenReturn(Mono.just(connection4));

        ConnectionPoolSettings poolSettings = new ConnectionPoolSettings.Builder()
                .maxConnectionsPerHost(2)
                .maxPendingConnectionsPerHost(2)
                .build();

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, poolSettings, connectionFactory);

        // Saturate active connections:
        CompletableFuture<Connection> pending1 = Mono.from(pool.borrowConnection()).toFuture();
        CompletableFuture<Connection> pending2 = Mono.from(pool.borrowConnection()).toFuture();
        assertTrue(pending1.isDone());
        assertTrue(pending2.isDone());

        // These are pending
        CompletableFuture<Connection> pending3 = Mono.from(pool.borrowConnection()).toFuture();
        CompletableFuture<Connection> pending4 = Mono.from(pool.borrowConnection()).toFuture();

        assertFalse(pending3.isDone());
        assertFalse(pending4.isDone());

        // This throws an error:
        StepVerifier.create(pool.borrowConnection())
                .expectError(MaxPendingConnectionsExceededException.class)
                .verify();

        assertEquals(pool.stats().connectionAttempts(), 2);
        assertEquals(pool.stats().pendingConnectionCount(), 2);
        assertEquals(pool.stats().busyConnectionCount(), 2);
        assertEquals(pool.stats().availableConnectionCount(), 0);
        assertEquals(pool.stats().terminatedConnections(), 0);
        assertEquals(pool.stats().closedConnections(), 0);
    }


    @Test
    public void givesReturnedConnectionsToPendingSubscibers() throws ExecutionException, InterruptedException, TimeoutException {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Mono.just(connection1))
                .thenReturn(Mono.just(connection2))
                .thenReturn(Mono.just(connection3))
                .thenReturn(Mono.just(connection4));

        ConnectionPoolSettings poolSettings = new ConnectionPoolSettings.Builder()
                .maxConnectionsPerHost(2)
                .maxPendingConnectionsPerHost(2)
                .build();

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, poolSettings, connectionFactory);

        // Saturate active connections:
        CompletableFuture<Connection> active1 = Mono.from(pool.borrowConnection()).toFuture();
        CompletableFuture<Connection> active2 = Mono.from(pool.borrowConnection()).toFuture();

        assertTrue(active1.isDone());
        assertTrue(active2.isDone());
        assertEquals(pool.stats().connectionAttempts(), 2);
        assertEquals(pool.stats().busyConnectionCount(), 2);

        // Create two pending connections
        CompletableFuture<Connection> pending1 = Mono.from(pool.borrowConnection()).toFuture();
        CompletableFuture<Connection> pending2 = Mono.from(pool.borrowConnection()).toFuture();

        assertFalse(pending1.isDone());
        assertFalse(pending2.isDone());
        assertEquals(pool.stats().connectionAttempts(), 2);
        assertEquals(pool.stats().busyConnectionCount(), 2);
        assertEquals(pool.stats().pendingConnectionCount(), 2);

        // Return active connections
        pool.returnConnection(active1.get());
        pool.returnConnection(active2.get());

        // Show that returned connections are given to pending subscribers
        assertEquals(pending1.get(), connection1);
        assertEquals(pending2.get(), connection2);
        assertEquals(pool.stats().connectionAttempts(), 2);
        assertEquals(pool.stats().busyConnectionCount(), 2);
        assertEquals(pool.stats().pendingConnectionCount(), 0);
    }

    @Test
    public void closesConnections() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Mono.just(connection1))
                .thenReturn(Mono.just(connection2))
                .thenReturn(Mono.just(connection3))
                .thenReturn(Mono.just(connection4));


        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        StepVerifier.create(pool.borrowConnection())
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
                .thenReturn(Mono.just(connection1))
                .thenReturn(Mono.just(connection2))
                .thenReturn(Mono.just(connection3))
                .thenReturn(Mono.just(connection4));

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        // Saturate pool:
        StepVerifier.create(pool.borrowConnection())
                .expectNext(connection1)
                .verifyComplete();

        StepVerifier.create(pool.borrowConnection())
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
        StepVerifier.create(pool.borrowConnection())
                .expectNext(connection3)
                .verifyComplete();

        assertEquals(pool.stats().busyConnectionCount(), 2);
        assertEquals(pool.stats().terminatedConnections(), 0);
        assertEquals(pool.stats().closedConnections(), 1);
    }


    @Test
    public void closeConnectionTriggersConnectionEstablishment() throws ExecutionException, InterruptedException {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Mono.just(connection1))
                .thenReturn(Mono.just(connection2))
                .thenReturn(Mono.just(connection3))
                .thenReturn(Mono.just(connection4));

        ConnectionPoolSettings poolSettings = new ConnectionPoolSettings.Builder()
                .maxConnectionsPerHost(2)
                .maxPendingConnectionsPerHost(2)
                .build();

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, poolSettings, connectionFactory);

        // Saturate pool:
        StepVerifier.create(pool.borrowConnection())
                .expectNext(connection1)
                .verifyComplete();

        StepVerifier.create(pool.borrowConnection())
                .expectNext(connection2)
                .verifyComplete();

        assertEquals(pool.stats().busyConnectionCount(), 2);
        assertEquals(pool.stats().availableConnectionCount(), 0);

        // Create a pending connection
        CompletableFuture<Connection> pending1 = Mono.from(pool.borrowConnection()).toFuture();
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
                .thenReturn(Mono.just(connection1))
                .thenReturn(Mono.just(connection2))
                .thenReturn(Mono.just(connection3))
                .thenReturn(Mono.just(connection4));


        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        // Create a new connection
        StepVerifier.create(pool.borrowConnection())
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
        assertEquals(pool.stats().availableConnectionCount(), 0);
        assertEquals(pool.stats().closedConnections(), 0);
        assertEquals(pool.stats().terminatedConnections(), 1);

        // Connection closure doesn't affect subsequent borrows:
        StepVerifier.create(pool.borrowConnection())
                .expectNext(connection2)
                .verifyComplete();

        StepVerifier.create(pool.borrowConnection())
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
                .thenReturn(Mono.error(new OriginUnreachableException(origin, new RuntimeException())))
                .thenReturn(Mono.error(new OriginUnreachableException(origin, new RuntimeException())))
                .thenReturn(Mono.just(connection4));


        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        StepVerifier.create(pool.borrowConnection())
                .expectNext(connection4)
                .verifyComplete();
    }

    @Test
    public void borrowRetriesThreeTimesOnFailureDueToConnectionClosure() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Mono.just(connection1))
                .thenReturn(Mono.error(new OriginUnreachableException(origin, new RuntimeException())))
                .thenReturn(Mono.error(new OriginUnreachableException(origin, new RuntimeException())))
                .thenReturn(Mono.just(connection4));

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        StepVerifier.create(pool.borrowConnection())
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
    public void borrowGivesUpConnectionEstablishmentAttemptAfterThreeTries() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Mono.error(new OriginUnreachableException(origin, new RuntimeException())))
                .thenReturn(Mono.error(new OriginUnreachableException(origin, new RuntimeException())))
                .thenReturn(Mono.error(new OriginUnreachableException(origin, new RuntimeException())));

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        Mono.from(pool.borrowConnection()).subscribe();

        assertEquals(pool.stats().pendingConnectionCount(), 1);
        assertEquals(pool.stats().connectionFailures(), 1);
        assertEquals(pool.stats().availableConnectionCount(), 0);
        assertEquals(pool.stats().closedConnections(), 0);
        assertEquals(pool.stats().terminatedConnections(), 0);
    }

    @Test
    public void connectionEstablishmentFailureRetryThreeTimesOnlyAtConnectionClosure() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Mono.just(connection1))
                .thenReturn(Mono.error(new OriginUnreachableException(origin, new RuntimeException())))
                .thenReturn(Mono.error(new OriginUnreachableException(origin, new RuntimeException())))
                .thenReturn(Mono.error(new OriginUnreachableException(origin, new RuntimeException())));


        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        StepVerifier.create(pool.borrowConnection())
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
    public void emitsExceptionWhenPendingConnectionTimesOut() {
        EmitterProcessor<Connection> processor = EmitterProcessor.create();
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Mono.from(processor));

        ConnectionPoolSettings poolSettings = new ConnectionPoolSettings.Builder()
                .pendingConnectionTimeout(500, MILLISECONDS)
                .build();

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, poolSettings, connectionFactory);

        StepVerifier.create(pool.borrowConnection())
                .expectError(MaxPendingConnectionTimeoutException.class)
                .verifyThenAssertThat()
                .tookMoreThan(Duration.ofMillis(500));

        // And then ensure connection is placed in the active queue:
        processor.onNext(mock(Connection.class));

        assertEquals(pool.stats().availableConnectionCount(), 1);
        assertEquals(pool.stats().pendingConnectionCount(), 0);
        assertEquals(pool.stats().busyConnectionCount(), 0);
        assertEquals(pool.stats().connectionAttempts(), 1);
    }

    @Test
    public void shouldNotHandoutConnectionToCancelledSubscriberWhenCreatingNewConnection() throws Exception {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Mono.just(connection1));

        ConnectionPoolSettings poolSettings = new ConnectionPoolSettings.Builder()
                .pendingConnectionTimeout(100, MILLISECONDS)
                .build();

        SimpleConnectionPool simpleConnectionPool = new SimpleConnectionPool(origin, poolSettings, connectionFactory);
        SimpleConnectionPool pool = spy(simpleConnectionPool);
        when(pool.dequeue())
                .thenAnswer(new AnswersWithDelay(200, new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        return invocation.callRealMethod();
                    }
                }));
        StepVerifier.create(pool.borrowConnection())
                .expectError(MaxPendingConnectionTimeoutException.class)
                .verify();

        assertEquals(pool.stats().availableConnectionCount(), 1);
        assertEquals(pool.stats().pendingConnectionCount(), 0); // Waiting subscribers
        assertEquals(pool.stats().busyConnectionCount(), 0);    // Borrowed count
   }

    @Test
    public void shouldNotHandoutConnectionToCancelledSubscriberWhenConnectionIsReturned() throws Exception {
        EmitterProcessor<Connection> processor = EmitterProcessor.create();
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Mono.from(processor));
        ConnectionPoolSettings poolSettings = new ConnectionPoolSettings.Builder()
                .pendingConnectionTimeout(100, MILLISECONDS)
                .build();

        SimpleConnectionPool simpleConnectionPool = new SimpleConnectionPool(origin, poolSettings, connectionFactory);
        SimpleConnectionPool pool = spy(simpleConnectionPool);
        when(pool.dequeue())
                .thenAnswer(new AnswersWithDelay(200, new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        return invocation.callRealMethod();
                    }
                }));

        StepVerifier.create(pool.borrowConnection())
                .expectError(MaxPendingConnectionTimeoutException.class)
                .verify();

        pool.returnConnection(connection1);

        assertEquals(pool.stats().availableConnectionCount(), 1);
        assertEquals(pool.stats().pendingConnectionCount(), 0); // Waiting subscribers
        assertEquals(pool.stats().busyConnectionCount(), -1);    // Borrowed count
    }

    @Test
    public void shouldNotHandoutConnectionToCancelledSubscriberWhenConnectionAlreadyInPool() throws Exception {
        ConnectionPoolSettings poolSettings = new ConnectionPoolSettings.Builder()
                .pendingConnectionTimeout(100, MILLISECONDS)
                .build();

        SimpleConnectionPool simpleConnectionPool = new SimpleConnectionPool(origin, poolSettings, connectionFactory);
        SimpleConnectionPool pool = spy(simpleConnectionPool);
        when(pool.dequeue())
                .thenAnswer(new AnswersWithDelay(200, new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        return connection1;
                    }
                }));

        StepVerifier.create(pool.borrowConnection())
                .expectError(MaxPendingConnectionTimeoutException.class)
                .verify();

        assertEquals(pool.stats().availableConnectionCount(), 1);
        assertEquals(pool.stats().pendingConnectionCount(), 0); // Waiting subscribers
        assertEquals(pool.stats().busyConnectionCount(), 0);    // Borrowed count
    }

    @Test
    public void registersAsConnectionListener() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Mono.just(connection1));

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        StepVerifier.create(pool.borrowConnection())
                .consumeNextWith(pool::returnConnection)
                .verifyComplete();

        verify(connection1).addConnectionListener(Mockito.eq(pool));
    }

    @Test
    public void registersAsConnectionListenerOnlyOnce() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Mono.error(new RuntimeException("Connection failure.")))
                .thenReturn(Mono.just(connection1));

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        StepVerifier.create(pool.borrowConnection())
                .consumeNextWith(pool::returnConnection)
                .verifyComplete();

        verify(connection1).addConnectionListener(Mockito.eq(pool));
    }

    @Test
    public void removesTerminatedIdleConnections() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Mono.just(connection1));

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        StepVerifier.create(pool.borrowConnection())
                .consumeNextWith(pool::returnConnection)
                .verifyComplete();

        assertEquals(pool.stats().availableConnectionCount(), 1);

        pool.connectionClosed(connection1);

        assertEquals(pool.stats().availableConnectionCount(), 0);
    }

    @Test
    public void tracksConnectionsInEstablishment() {
        TestPublisher<Connection> pub1 = TestPublisher.create();
        TestPublisher<Connection> pub2 = TestPublisher.create();
        TestPublisher<Connection> pub3 = TestPublisher.create();
        TestPublisher<Connection> pub4 = TestPublisher.create();

        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(pub1.mono())
                .thenReturn(pub2.mono())
                .thenReturn(pub3.mono())
                .thenReturn(pub4.mono());

        SimpleConnectionPool pool = new SimpleConnectionPool(
                origin,
                new ConnectionPoolSettings.Builder(defaultConnectionPoolSettings())
                        .maxConnectionsPerHost(2)
                        .build(),
                connectionFactory);

        CompletableFuture<Connection> f1 = Mono.from(pool.borrowConnection()).toFuture();
        CompletableFuture<Connection> f2 = Mono.from(pool.borrowConnection()).toFuture();
        CompletableFuture<Connection> f3 = Mono.from(pool.borrowConnection()).toFuture();
        CompletableFuture<Connection> f4 = Mono.from(pool.borrowConnection()).toFuture();

        pub1.emit(connection1);
        pub2.emit(connection2);
        pub3.emit(connection3);
        pub4.emit(connection4);

        assertTrue(f1.isDone());
        assertTrue(f2.isDone());
        assertFalse(f3.isDone());
        assertFalse(f4.isDone());
    }

    @Test
    public void ensureInEstablishmentCountIsDecrementedInError() throws InterruptedException {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Mono.error(new RuntimeException("First attempt: connection 1")))
                .thenReturn(Mono.error(new RuntimeException("Second attempt: connection 1")))
                .thenReturn(Mono.error(new RuntimeException("Third attempt: connection 1")))
                .thenReturn(Mono.error(new RuntimeException("First attempt: connection 2")))
                .thenReturn(Mono.error(new RuntimeException("Second attempt: connection 2")))
                .thenReturn(Mono.error(new RuntimeException("Third attempt: connection 2")))
                .thenReturn(Mono.just(connection3));

        SimpleConnectionPool pool = new SimpleConnectionPool(
                origin,
                new ConnectionPoolSettings.Builder(defaultConnectionPoolSettings())
                        .maxConnectionsPerHost(2)
                        .pendingConnectionTimeout(50, MILLISECONDS)
                        .build(),
                connectionFactory);

        StepVerifier.create(pool.borrowConnection())
                .expectError()
                .verify();

        StepVerifier.create(pool.borrowConnection())
                .expectError()
                .verify();

        // This will fail if `connectionsInEstablishment` count is not decremented after
        // connection establishment errors:
        StepVerifier.create(pool.borrowConnection())
                .expectNext(connection3)
                .verifyComplete();
    }

    @Test
    public void availableConnectionsAreClosedAsPartOfPoolClosure() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Mono.just(connection1));

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        StepVerifier.create(pool.borrowConnection())
                .consumeNextWith(pool::returnConnection)
                .verifyComplete();

        assertEquals(1, pool.stats().availableConnectionCount());
        pool.close();

        verify(connection1).close();
        assertEquals(0, pool.stats().availableConnectionCount());
        assertEquals(0, pool.stats().busyConnectionCount());
    }

    @Test
    public void emitsExceptionWhenBrrowingFromClosedPool() {

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);
        pool.close();
        StepVerifier.create(pool.borrowConnection())
                .expectError(IllegalStateException.class)
                .verify();
        assertEquals(pool.stats().connectionAttempts(), 0);
        assertEquals(pool.stats().busyConnectionCount(), 0);
        assertEquals(pool.stats().availableConnectionCount(), 0);
        assertEquals(pool.stats().pendingConnectionCount(), 0);
    }

    @Test
    public void justClosesConnectionWhenReturningToClosedPool() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class))).thenReturn(Mono.just(connection1));
        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        StepVerifier.create(pool.borrowConnection())
                .expectNext(connection1)
                .verifyComplete();
        assertEquals(pool.stats().connectionAttempts(), 1);
        assertEquals(pool.stats().busyConnectionCount(), 1);

        pool.close();
        assertEquals(0, pool.stats(). closedConnections());

        doAnswer( arg -> {
            when(connection1.isConnected()).thenReturn(false);
            return true;
        }).when(connection1).close();
        pool.returnConnection(connection1);
        assertEquals(pool.stats().connectionAttempts(), 1);
        assertEquals(pool.stats().busyConnectionCount(), 0);
        assertEquals(1, pool.stats(). closedConnections());
    }

    @Test
    public void closedPoolDoesNotOpenNewConnectionWhenConnectionIsClosed() {

        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Mono.just(connection1));


        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        StepVerifier.create(pool.borrowConnection())
                .expectNext(connection1)
                .verifyComplete();

        assertEquals(1, pool.stats().busyConnectionCount());
        //Close connection pool
        pool.close();
        pool.closeConnection(connection1);

        verify(connection1).close();
        assertEquals(pool.stats().terminatedConnections(), 0);
        assertEquals(pool.stats().closedConnections(), 1);
        assertEquals(0, pool.stats().busyConnectionCount());
    }

    @Test
    public void purgesTerminatedConnectionsForClosedPools() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Mono.just(connection1));

        // Create a new connection
        SimpleConnectionPool pool = new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), connectionFactory);

        StepVerifier.create(pool.borrowConnection())
                .expectNext(connection1)
                 .verifyComplete();
        //Close the pool
        pool.close();
        //Close and return the connection
        pool.connectionClosed(connection1);
        when(connection1.isConnected()).thenReturn(false);
        pool.returnConnection(connection1);

        assertEquals(pool.stats().connectionAttempts(), 1);
        assertEquals(pool.stats().pendingConnectionCount(), 0);
        assertEquals(pool.stats().busyConnectionCount(), 0);
        assertEquals(pool.stats().availableConnectionCount(), 0);

        assertEquals(pool.stats().terminatedConnections(), 1);
        assertEquals(pool.stats().closedConnections(), 0);
    }


}
