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


import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.client.Connection;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.ConnectionPoolSettings;
import com.hotels.styx.client.ConnectionSettings;
import com.hotels.styx.client.connectionpool.stubs.StubConnectionFactory;
import com.hotels.styx.support.MultithreadedStressTester;
import org.testng.annotations.Test;
import reactor.core.publisher.Flux;
import rx.Observable;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import static com.hotels.styx.support.api.BlockingObservables.getFirst;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo;

public class SimpleConnectionPoolStressTest {
    final Origin origin = Origin.newOriginBuilder("localhost", 9090)
            .build();

    final ConnectionPoolSettings settings = new ConnectionPoolSettings.Builder()
            .maxConnectionsPerHost(10)
            .maxPendingConnectionsPerHost(20)
            .connectTimeout(200, MILLISECONDS)
            .build();

    final SimpleConnectionPool pool = new SimpleConnectionPool(origin, settings, new StubConnectionFactory());

    @Test
    public void canRoundTripBorrowedConnectionsFromMultipleThreads() throws InterruptedException {
        MultithreadedStressTester stressTester = new MultithreadedStressTester(10, 250);
        Random returnOrClose = new Random();
        stressTester.stress(() -> {
            Connection connection = borrowConnectionSynchronously();
            if (returnOrClose.nextBoolean()) {
                releaseConnection(connection);
            } else {
                closeConnection(connection);
            }
        });

        stressTester.shutdown();

        assertThat("final busy connection count", pool.stats().busyConnectionCount(), is(0));
        assertThat("final available connection count", pool.stats().availableConnectionCount(), is(greaterThanOrEqualTo(0)));
    }

    @Test(enabled = false)
    public void stressTest() throws InterruptedException {
        AtomicLong borrowed = new AtomicLong(0);
        AtomicLong cancelled = new AtomicLong(0);
        AtomicLong returned = new AtomicLong(0);
        AtomicLong closed = new AtomicLong(0);

        CountDownLatch latch = new CountDownLatch(1);

        ConnectionPoolSettings poolSettings = new ConnectionPoolSettings.Builder()
                .maxConnectionsPerHost(7)
                .maxPendingConnectionsPerHost(300)
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(4);

        SimpleConnectionPool pool = new SimpleConnectionPool(origin, poolSettings, new FakeConnectionFactory());

        final int COUNT = 5000;

        Flux.range(1, COUNT)
                .delayElements(Duration.ofMillis(5))
                .doOnNext(i -> {
                    if (i % 100 == 0) {
                        System.out.println(format("processed %d connections", i));
                    }

                    // Queue a borrow event
                    executor.submit(borrowTask(borrowed, cancelled, returned, closed, executor, pool));

                    if (i == COUNT) {
                        executor.submit(latch::countDown);
                    }
                })
                .blockLast();

        latch.await();

        System.out.println("waiting for things to calm down:");

        Thread.sleep(4000);

        System.out.println("borrowed:  " + borrowed.get());
        System.out.println("returned:  " + returned.get());
        System.out.println("closed:    " + closed.get());
        System.out.println("cancelled: " + cancelled.get());

        System.out.println(pool.stats());
    }

    private Runnable borrowTask(AtomicLong borrowed, AtomicLong cancelled, AtomicLong returned, AtomicLong closed, ExecutorService executor, SimpleConnectionPool pool) {
        return () -> {
            borrowed.incrementAndGet();
            Flux.from(pool.borrowConnection2())
                    // Unsubscribes automatically:
                    .timeout(Duration.ofMillis(200))
                    // Delay elements to simulate connection usage
                    .delayElements(Duration.ofMillis(50))
                    // Finally: put them back
                    .subscribe(connection -> {
                                executor.submit(() -> {
                                    if (Math.random() < 0.1) {
                                        returned.incrementAndGet();
                                        pool.returnConnection(connection);
                                    } else {
                                        closed.incrementAndGet();
                                        pool.closeConnection(connection);
                                    }
                                });
                            },
                            cause -> {
                                if (cause instanceof TimeoutException) {
                                    cancelled.incrementAndGet();
                                } else {
                                    System.out.println("cause: " + cause);
                                }
                            });
        };
    }


    static class FakeConnectionFactory implements Connection.Factory {
        private static final Random rng = new Random();

        @Override
        public Observable<Connection> createConnection(Origin origin, ConnectionSettings connectionSettings) {
            if (rng.nextDouble() < 0.05) {
                return Observable.error(new RuntimeException("unable to connet"));
            } else if (rng.nextDouble() < 0.90) {
                return Observable.just(new FakeConnectionFactory.FakeConnection());
            } else {
                Observable<Connection> delay = Observable.just(new FakeConnectionFactory.FakeConnection());
                return delay.delay(1, TimeUnit.SECONDS);
            }
        }


        static class FakeConnection implements Connection {
            Boolean connected = true;

            @Override
            public Observable<LiveHttpResponse> write(LiveHttpRequest request) {
                return null;
            }

            @Override
            public boolean isConnected() {
                return connected;
            }

            @Override
            public Origin getOrigin() {
                return null;
            }

            @Override
            public long getTimeToFirstByteMillis() {
                return 0;
            }

            @Override
            public void addConnectionListener(Listener listener) {

            }

            @Override
            public void close() {
                connected = false;
            }
        }
    }

    private void closeConnection(Connection connection) {
        pool.closeConnection(connection);
    }

    private void releaseConnection(Connection connection) {
        pool.returnConnection(connection);
    }

    private Connection borrowConnectionSynchronously() {
        return getFirst(pool.borrowConnection());
    }

}