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
package com.hotels.styx.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.hotels.styx.server.HttpConnectorConfig;
import com.hotels.styx.server.HttpsConnectorConfig;
import com.hotels.styx.server.netty.NettyServerConfig;

import java.time.Duration;
import java.util.Optional;

import static com.google.common.base.Objects.firstNonNull;

/**
 * xConfigurations for the Admin Server.
 */
@JsonDeserialize(builder = AdminServerConfig.Builder.class)
public class AdminServerConfig extends NettyServerConfig {
    public static final int DEFAULT_ADMIN_PORT = 9000;

    private final Optional<Duration> metricsCacheExpiration;

    public AdminServerConfig() {
        this.metricsCacheExpiration = Optional.empty();
    }

    private AdminServerConfig(Builder builder) {
        super(builder);

        if (builder.metricsCache != null && builder.metricsCache.enabled) {
            this.metricsCacheExpiration = Optional.of(Duration.ofMillis(builder.metricsCache.expirationMillis));
        } else {
            this.metricsCacheExpiration = Optional.empty();
        }
    }

    public Optional<Duration> metricsCacheExpiration() {
        return metricsCacheExpiration;
    }

    /**
     * Metrics cache.
     */
    static class MetricsCache {
        private final boolean enabled;
        private final int expirationMillis;

        MetricsCache(
                @JsonProperty("enabled") Boolean enabled,
                @JsonProperty("expirationMillis") Integer expirationMillis) {
            this.enabled = firstNonNull(enabled, false);
            this.expirationMillis = firstNonNull(expirationMillis, 10_000);
        }
    }

    /**
     * Builder.
     */
    @JsonPOJOBuilder(buildMethodName = "build", withPrefix = "set")
    public static class Builder extends NettyServerConfig.Builder<Builder> {
        private MetricsCache metricsCache;

        public Builder() {
            httpConnectorConfig = new HttpConnectorConfig(DEFAULT_ADMIN_PORT);
        }

        @Override
        public Builder httpPort(int port) {
            return (Builder) super.httpPort(port);
        }

        @Override
        public Builder setBossThreadsCount(Integer bossThreadsCount) {
            return super.setBossThreadsCount(bossThreadsCount);
        }

        @Override
        public Builder setWorkerThreadsCount(Integer workerThreadsCount) {
            return super.setWorkerThreadsCount(workerThreadsCount);
        }

        @Override
        public Builder setNioAcceptorBacklog(Integer nioAcceptorBacklog) {
            return super.setNioAcceptorBacklog(nioAcceptorBacklog);
        }

        @Override
        public Builder setTcpNoDelay(Boolean tcpNoDelay) {
            return super.setTcpNoDelay(tcpNoDelay);
        }

        @Override
        public Builder setNioReuseAddress(Boolean nioReuseAddress) {
            return super.setNioReuseAddress(nioReuseAddress);
        }

        @Override
        public Builder setNioKeepAlive(Boolean nioKeepAlive) {
            return super.setNioKeepAlive(nioKeepAlive);
        }

        @Override
        public Builder setMaxInitialLineLength(Integer maxInitialLineLength) {
            return super.setMaxInitialLineLength(maxInitialLineLength);
        }

        @Override
        public Builder setMaxHeaderSize(Integer maxHeaderSize) {
            return super.setMaxHeaderSize(maxHeaderSize);
        }

        @Override
        public Builder setMaxChunkSize(Integer maxChunkSize) {
            return super.setMaxChunkSize(maxChunkSize);
        }

        @Override
        public Builder setMaxContentLength(Integer maxContentLength) {
            return super.setMaxContentLength(maxContentLength);
        }

        @Override
        public Builder setRequestTimeoutMs(Integer requestTimeoutMs) {
            return super.setRequestTimeoutMs(requestTimeoutMs);
        }

        @Override
        public Builder setKeepAliveTimeoutMillis(Integer keepAliveTimeoutMillis) {
            return super.setKeepAliveTimeoutMillis(keepAliveTimeoutMillis);
        }

        @Override
        public Builder setConnectors(Connectors connectors) {
            return super.setConnectors(connectors);
        }

        public Builder setHttpConnector(HttpConnectorConfig httpConnector) {
            return super.setHttpConnector(httpConnector);
        }

        public Builder setHttpsConnector(HttpsConnectorConfig httpsConnector) {
            return super.setHttpsConnector(httpsConnector);
        }

        @Override
        public Builder setMaxConnectionsCount(Integer maxConnectionsCount) {
            return super.setMaxConnectionsCount(maxConnectionsCount);
        }

        @JsonProperty("metricsCache")
        public Builder setMetricsCache(MetricsCache metricsCache) {
            this.metricsCache = metricsCache;
            return this;
        }

        public AdminServerConfig build() {
            return new AdminServerConfig(this);
        }
    }
}
