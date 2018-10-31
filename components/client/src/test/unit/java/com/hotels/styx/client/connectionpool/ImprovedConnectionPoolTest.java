package com.hotels.styx.client.connectionpool;

import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.Connection;
import com.hotels.styx.client.ConnectionSettings;
import org.reactivestreams.Publisher;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import rx.Observable;
import rx.subjects.PublishSubject;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ImprovedConnectionPoolTest {

    private final Origin origin = newOriginBuilder("localhost", 9090).build();
    private final ConnectionSettings settings = new ConnectionSettings(1000);
    private Connection.Factory connectionFactory;
    private Connection connection1;
    private Connection connection2;

    @BeforeMethod
    public void setUp() {
        connectionFactory = mock(Connection.Factory.class);
        connection1 = mock(Connection.class);
        when(connection1.isConnected()).thenReturn(true);
        connection2 = mock(Connection.class);
        when(connection2.isConnected()).thenReturn(true);
    }

    @Test
    public void borrowsConnection() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class))).thenReturn(Observable.just(connection1));

        ImprovedConnectionPool pool = new ImprovedConnectionPool(origin, settings, connectionFactory);

        StepVerifier.create(pool.borrowConnection())
                .expectNext(connection1)
                .verifyComplete();
    }

    @Test
    public void borrowsReturnedConnection() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Observable.just(connection1))
                .thenReturn(Observable.just(connection2));

        ImprovedConnectionPool pool = new ImprovedConnectionPool(origin, settings, connectionFactory);

        StepVerifier.create(pool.borrowConnection())
                .expectNext(connection1)
                .verifyComplete();

        pool.returnConnection(connection1);

        StepVerifier.create(pool.borrowConnection())
                .expectNext(connection1)
                .verifyComplete();
    }


    @Test
    public void emitsConnectionWhenOnceEstablished() throws ExecutionException, InterruptedException {
        PublishSubject<Connection> subject = PublishSubject.create();
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(subject);

        // The pool is empty, so the `borrowConnection` triggers a connection establishment.
        Publisher<Connection> future = new ImprovedConnectionPool(origin, settings, connectionFactory)
                .borrowConnection();

        StepVerifier.create(future)
                .expectNextCount(0)
                .then(() -> {
                    subject.onNext(connection1);
                    subject.onCompleted();
                })
                .expectNext(connection1)
                .verifyComplete();
    }


    @Test
    public void purgesTerminatedConnections() {
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Observable.just(connection1))
                .thenReturn(Observable.just(connection2));


        // Create a new connection
        ImprovedConnectionPool pool = new ImprovedConnectionPool(origin, settings, connectionFactory);

        StepVerifier.create(pool.borrowConnection())
                .expectNext(connection1)
                .then(() -> pool.returnConnection(connection1))
                .verifyComplete();


        when(connection1.isConnected()).thenReturn(false);

        StepVerifier.create(pool.borrowConnection())
                .expectNext(connection2)
                .verifyComplete();
    }

    @Test
    public void returnsConnectionToWaitingSubscribers() throws ExecutionException, InterruptedException {
        PublishSubject<Connection> subject = PublishSubject.create();
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Observable.just(connection1))
                .thenReturn(subject);
        ImprovedConnectionPool pool = new ImprovedConnectionPool(origin, settings, connectionFactory);

        // The pool is empty, so the `borrowConnection` triggers a connection establishment.
        StepVerifier.create(pool.borrowConnection())
                .expectNext(connection1)
                .verifyComplete();

        // Another borrow comes in, and triggers a connection establishment.
        // The subject keeps the connection perpetually in "3-way handshake"
        Publisher<Connection> publisher = pool.borrowConnection();
        CompletableFuture<Connection> future = Mono.from(publisher).toFuture();

        // The returned connection is now given to the waiting subscriber:
        pool.returnConnection(connection1);

        assertTrue(future.isDone());
        assertEquals(future.get(), connection1);
    }

    @Test
    public void returnConnectionChecksIfConnectionIsClosed() {
        PublishSubject<Connection> subject = PublishSubject.create();
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Observable.just(connection1))
                .thenReturn(subject);
        ImprovedConnectionPool pool = new ImprovedConnectionPool(origin, settings, connectionFactory);

        // The pool is empty, so the `borrowConnection` triggers a connection establishment.
        StepVerifier.create(pool.borrowConnection())
                .expectNext(connection1)
                .verifyComplete();

        // Another borrow comes in, and triggers a connection establishment.
        // The subject keeps the connection perpetually in "3-way handshake"
        CompletableFuture<Connection> future = Mono.from(pool.borrowConnection()).toFuture();
        assertFalse(future.isDone());

        // The returned connection is closed, and then returned:
        when(connection1.isConnected()).thenReturn(false);
        pool.returnConnection(connection1);

        // The closed connection is discarded, and the subscriber still waits for the 3-way handshake
        assertFalse(future.isDone());
    }


    // 1. The completable future is unsubscribed

    @Test
    public void unsubscribingRemovesTheWaitingSubscriber() throws ExecutionException, InterruptedException {
        PublishSubject<Connection> subject1 = PublishSubject.create();
        PublishSubject<Connection> subject2 = PublishSubject.create();
        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(subject1)
                .thenReturn(subject2);
        ImprovedConnectionPool pool = new ImprovedConnectionPool(origin, settings, connectionFactory);

        // The subscriber is pending because the connection is still 3-way handshaking
        CompletableFuture<Connection> future1 = Mono.from(pool.borrowConnection()).toFuture();

        // Borrow another one:
        CompletableFuture<Connection> future2 = Mono.from(pool.borrowConnection()).toFuture();

        // `Future1` is ahead of `future2` in the waiting subscribers.
        // Cancel the future1 and verify that `future2` gets it spot:
        future1.cancel(true);

        subject1.onNext(connection1);
        subject1.onCompleted();

        assertTrue(future1.isCompletedExceptionally());
        assertEquals(future2.get(), connection1);
    }


    // 2. The connection pool limits

    //


}