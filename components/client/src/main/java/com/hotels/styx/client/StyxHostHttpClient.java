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

import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.Id;
import com.hotels.styx.client.connectionpool.ConnectionPool;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancingMetric;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancingMetricSupplier;
import rx.Observable;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * A Styx HTTP Client for proxying to an individual origin host.
 */
public class StyxHostHttpClient implements HttpClient, LoadBalancingMetricSupplier {
    private final Transport transport;
    private final Id originId;
    private final ConnectionPool pool;

    public StyxHostHttpClient(Id originId, ConnectionPool pool, Transport transport) {
        this.originId = requireNonNull(originId);
        this.pool = requireNonNull(pool);
        this.transport = requireNonNull(transport);
    }

    public static StyxHostHttpClient create(Id appId, Id originId, CharSequence headerName, ConnectionPool pool) {
        return new StyxHostHttpClient(originId, pool, new Transport(appId, headerName));
    }

    @Override
    public Observable<HttpResponse> sendRequest(HttpRequest request) {
        return transport
                .send(request, Optional.of(pool), originId)
                .response();
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
