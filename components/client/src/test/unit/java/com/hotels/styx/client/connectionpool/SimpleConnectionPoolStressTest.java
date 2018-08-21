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


import com.hotels.styx.client.Connection;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.ConnectionPoolSettings;
import com.hotels.styx.client.connectionpool.stubs.StubConnectionFactory;
import com.hotels.styx.support.MultithreadedStressTester;
import org.testng.annotations.Test;

import java.util.Random;

import static com.hotels.styx.support.api.BlockingObservables.getFirst;
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