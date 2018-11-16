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

import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.ResponseEventListener;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancingMetric;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancingMetricSupplier;
import com.hotels.styx.client.connectionpool.ConnectionPool;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import rx.RxReactiveStreams;

import static java.util.Objects.requireNonNull;

/**
 * A Styx HTTP Client for proxying to an individual origin host.
 */
public class StyxHostHttpClient implements LoadBalancingMetricSupplier {
    private final ConnectionPool pool;

    StyxHostHttpClient(ConnectionPool pool) {
        this.pool = requireNonNull(pool);
    }

    public static StyxHostHttpClient create(ConnectionPool pool) {
        return new StyxHostHttpClient(pool);
    }

    public Publisher<LiveHttpResponse> sendRequest(LiveHttpRequest request) {
        return Flux.from(pool.borrowConnection())
                .flatMap(connection -> {
                    Publisher<LiveHttpResponse> write = RxReactiveStreams.toPublisher(connection.write(request));

                    return ResponseEventListener.from(write)
                            .whenCancelled(() -> pool.closeConnection(connection))
                            .whenResponseError(cause -> pool.closeConnection(connection))
                            .whenContentError(cause -> pool.closeConnection(connection))
                            .whenCompleted(() -> pool.returnConnection(connection))
                            .apply();
                });
    }

    public void close() {
        pool.close();
    }

    @Override
    public LoadBalancingMetric loadBalancingMetric() {
        return new LoadBalancingMetric(this.pool.stats().busyConnectionCount() + pool.stats().pendingConnectionCount());
    }

    /**
     * A factory for creating StyxHostHttpClient instances.
     */
    public interface Factory {
        StyxHostHttpClient create(ConnectionPool connectionPool);
    }

}
