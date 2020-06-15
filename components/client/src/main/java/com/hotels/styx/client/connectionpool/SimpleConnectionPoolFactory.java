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

import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.ConnectionPoolSettings;
import com.hotels.styx.client.Connection;
import io.micrometer.core.instrument.MeterRegistry;

import static java.util.Objects.requireNonNull;


/**
 * A factory that creates connection pools using the connection pool settings supplied to the constructor.
 * <p/>
 * It also registers metrics for the connection pools.
 */
public final class SimpleConnectionPoolFactory implements ConnectionPool.Factory {
    private final Connection.Factory connectionFactory;
    private final ConnectionPoolSettings poolSettings;
    private final MeterRegistry meterRegistry;

    private SimpleConnectionPoolFactory(Builder builder) {
        this.connectionFactory = requireNonNull(builder.connectionFactory);
        this.poolSettings = new ConnectionPoolSettings.Builder(requireNonNull(builder.poolSettings)).build();
        this.meterRegistry = requireNonNull(builder.meterRegistry);
    }

    @Override
    public ConnectionPool create(Origin origin) {
        return new StatsReportingConnectionPool(
                new SimpleConnectionPool(origin, poolSettings, connectionFactory),
                meterRegistry);
    }

    /**
     * Builder for connection pool factory.
     */
    public static final class Builder {
        private Connection.Factory connectionFactory;
        private ConnectionPoolSettings poolSettings;
        private MeterRegistry meterRegistry;

        public Builder connectionFactory(Connection.Factory connectionFactory) {
            this.connectionFactory = connectionFactory;
            return this;
        }

        public Builder connectionPoolSettings(ConnectionPoolSettings poolSettings) {
            this.poolSettings = poolSettings;
            return this;
        }

        public Builder meterRegistry(MeterRegistry metricRegistry) {
            this.meterRegistry = metricRegistry;
            return this;
        }

        public SimpleConnectionPoolFactory build() {
            return new SimpleConnectionPoolFactory(this);
        }
    }
}
