/*
  Copyright (C) 2013-2020 Expedia Inc.

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

import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.ConnectionPoolSettings;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.client.netty.connectionpool.NettyConnectionFactory;

import static com.hotels.styx.api.extension.service.ConnectionPoolSettings.defaultConnectionPoolSettings;
import static com.hotels.styx.client.HttpConfig.newHttpConfigBuilder;
import static com.hotels.styx.client.HttpRequestOperationFactory.Builder.httpRequestOperationFactoryBuilder;
import static java.lang.String.format;

/**
 * Helper routines for building connection pools with default settings.
 */
public final class ConnectionPools {
    private ConnectionPools() {
    }

    public static ConnectionPool poolForOrigin(Origin origin) {
        return new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), new NettyConnectionFactory.Builder().build());
    }

    public static ConnectionPool.Factory simplePoolFactory() {
        return ConnectionPools::poolForOrigin;
    }

    public static ConnectionPool poolForOrigin(Origin origin, MetricRegistry metricRegistry) {
        return new StatsReportingConnectionPool(
                new SimpleConnectionPool(
                        origin,
                        defaultConnectionPoolSettings(),
                        new NettyConnectionFactory.Builder()
                                .build()),
                metricRegistry);
    }

    public static ConnectionPool poolForOrigin(Origin origin, MetricRegistry metricRegistry, int responseTimeoutMillis) {
        return new StatsReportingConnectionPool(
                new SimpleConnectionPool(
                        origin,
                        defaultConnectionPoolSettings(),
                        new NettyConnectionFactory.Builder()
                                .httpRequestOperationFactory(httpRequestOperationFactoryBuilder().responseTimeoutMillis(responseTimeoutMillis).build())
                                .build()),
                metricRegistry);
    }

    public static ConnectionPool create(String hostname, int port, ConnectionPoolSettings poolSettings) {
        return new SimpleConnectionPoolFactory.Builder()
                .connectionPoolSettings(poolSettings)
                .connectionFactory(new NettyConnectionFactory.Builder().build())
                .metricRegistry(new CodaHaleMetricRegistry())
                .build()
                .create(Origin.newOriginBuilder(hostname, port)
                        .applicationId(format("%s:%d", hostname, port))
                        .id(format("%s:%d-01", hostname, port))
                        .build());
    }

    private static ConnectionPool poolForOrigin(Origin origin, MetricRegistry metricRegistry, NettyConnectionFactory connectionFactory) {
        return new StatsReportingConnectionPool(
                new SimpleConnectionPool(
                        origin,
                        defaultConnectionPoolSettings(),
                        connectionFactory),
                metricRegistry
        );
    }

    public static ConnectionPool.Factory simplePoolFactory(MetricRegistry metricRegistry) {
        return origin -> poolForOrigin(origin, metricRegistry);
    }

    public static ConnectionPool.Factory simplePoolFactory(BackendService backendService, MetricRegistry metricRegistry) {
        NettyConnectionFactory connectionFactory = new NettyConnectionFactory.Builder()
                .httpConfig(newHttpConfigBuilder().setMaxHeadersSize(backendService.maxHeaderSize()).build())
                .httpRequestOperationFactory(
                        httpRequestOperationFactoryBuilder()
                                .responseTimeoutMillis(backendService.responseTimeoutMillis())
                                .build())
                .build();

        return origin -> poolForOrigin(origin, metricRegistry, connectionFactory);
    }
}
