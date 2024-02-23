/*
  Copyright (C) 2013-2024 Expedia Inc.

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

import com.hotels.styx.api.HttpInterceptor.Context;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.ResponseEventListener;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancingMetric;
import com.hotels.styx.client.connectionpool.ConnectionPool;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import static java.util.Objects.requireNonNull;

/**
 * A Styx HTTP Client for proxying to an individual origin host.
 * @deprecated Use {@link ReactorHostHttpClient} instead.
 */
@Deprecated
public class StyxHostHttpClient implements HostHttpClient {
    public static final String ORIGINID_CONTEXT_KEY = "styx.originid";

    private final ConnectionPool pool;

    StyxHostHttpClient(ConnectionPool pool) {
        this.pool = requireNonNull(pool);
    }

    public static StyxHostHttpClient create(ConnectionPool pool) {
        return new StyxHostHttpClient(pool);
    }

    public Publisher<LiveHttpResponse> sendRequest(LiveHttpRequest request, Context context) {
        if (context != null) {
            context.add(ORIGINID_CONTEXT_KEY, pool.getOrigin().id());
        }
        return Flux.from(pool.borrowConnection())
                .flatMap(connection -> {

                    return ResponseEventListener.from(connection.write(request, context))
                            .whenCancelled(() -> pool.closeConnection(connection))
                            .whenResponseError(cause -> pool.closeConnection(connection))
                            .whenContentError(cause -> pool.closeConnection(connection))
                            .whenCompleted(response -> pool.returnConnection(connection))
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
