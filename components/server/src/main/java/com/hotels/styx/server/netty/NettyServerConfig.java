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
package com.hotels.styx.server.netty;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.hotels.styx.server.ConnectorConfig;
import com.hotels.styx.server.HttpConnectorConfig;
import com.hotels.styx.server.HttpsConnectorConfig;

import java.net.URI;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.base.Objects.firstNonNull;

import static java.lang.Math.max;
import static java.lang.Runtime.getRuntime;
import static java.lang.String.format;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;

/**
 * Configuration values for the netty based web server.
 *
 */
@JsonDeserialize(builder = NettyServerConfig.Builder.class)
public class NettyServerConfig {
    public static final int HALF_OF_AVAILABLE_PROCESSORS = max(1, getRuntime().availableProcessors() / 2);

    private int bossThreadsCount = 1;
    private int workerThreadsCount = HALF_OF_AVAILABLE_PROCESSORS;

    private int nioAcceptorBacklog = 1024;
    private boolean tcpNoDelay = true;
    private boolean nioReuseAddress = true;
    private boolean nioKeepAlive = true;

    private int maxInitialLineLength = 4096;
    private int maxHeaderSize = 8192;
    private int maxChunkSize = 8192;
    private int maxContentLength = 65536;
    private int requestTimeoutMs = 12000;
    private int keepAliveTimeoutMillis = 12000;
    private int maxConnectionsCount = 512;

    private final Optional<HttpConnectorConfig> httpConnectorConfig;
    private final Optional<HttpsConnectorConfig> httpsConnectorConfig;

    private final Iterable<ConnectorConfig> connectors;

    public NettyServerConfig() {
        this.httpConnectorConfig = Optional.of(new HttpConnectorConfig(8080));
        this.httpsConnectorConfig = Optional.empty();
        this.connectors = singleton((ConnectorConfig) httpConnectorConfig.get());
    }

    protected NettyServerConfig(Builder<?> builder) {
        this.bossThreadsCount = firstNonNull(builder.bossThreadsCount, HALF_OF_AVAILABLE_PROCESSORS);
        this.workerThreadsCount = firstNonNull(builder.workerThreadsCount, HALF_OF_AVAILABLE_PROCESSORS);
        this.nioAcceptorBacklog = firstNonNull(builder.nioAcceptorBacklog, 1024);
        this.tcpNoDelay = firstNonNull(builder.tcpNoDelay, true);
        this.nioReuseAddress = firstNonNull(builder.nioReuseAddress, true);
        this.nioKeepAlive = firstNonNull(builder.nioKeepAlive, true);
        this.maxInitialLineLength = firstNonNull(builder.maxInitialLineLength, 4096);
        this.maxHeaderSize = firstNonNull(builder.maxHeaderSize, 8192);
        this.maxChunkSize = firstNonNull(builder.maxChunkSize, 8192);
        this.maxContentLength = firstNonNull(builder.maxContentLength, 65536);
        this.requestTimeoutMs = firstNonNull(builder.requestTimeoutMs, 12000);
        this.keepAliveTimeoutMillis = firstNonNull(builder.keepAliveTimeoutMillis, 12000);
        this.maxConnectionsCount = firstNonNull(builder.maxConnectionsCount, 512);

        if (this.workerThreadsCount == 0) {
            this.workerThreadsCount = HALF_OF_AVAILABLE_PROCESSORS;
        }

        this.httpConnectorConfig = Optional.ofNullable(builder.httpConnectorConfig);
        this.httpsConnectorConfig = Optional.ofNullable(builder.httpsConnectorConfig);

        this.connectors = connectorsIterable();
    }

    private Iterable<ConnectorConfig> connectorsIterable() {
        return Stream.of(httpConnectorConfig, httpsConnectorConfig)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toList());
    }

    public Optional<HttpConnectorConfig> httpConnectorConfig() {
        return httpConnectorConfig;
    }

    public Optional<HttpsConnectorConfig> httpsConnectorConfig() {
        return httpsConnectorConfig;
    }

    public Iterable<ConnectorConfig> connectors() {
        return connectors;
    }

    public int bossThreadsCount() {
        return this.bossThreadsCount;
    }

    public int workerThreadsCount() {
        return this.workerThreadsCount;
    }

    public int nioAcceptorBacklog() {
        return this.nioAcceptorBacklog;
    }

    public boolean tcpNoDelay() {
        return this.tcpNoDelay;
    }

    public boolean nioReuseAddress() {
        return this.nioReuseAddress;
    }

    public int maxInitialLineLength() {
        return this.maxInitialLineLength;
    }

    public int maxHeaderSize() {
        return this.maxHeaderSize;
    }

    public int maxChunkSize() {
        return this.maxChunkSize;
    }

    public int maxContentLength() {
        return this.maxContentLength;
    }

    public int requestTimeoutMillis() {
        return this.requestTimeoutMs;
    }

    public int keepAliveTimeoutMillis() {
        return this.keepAliveTimeoutMillis;
    }

    public int maxConnectionsCount() {
        return this.maxConnectionsCount;
    }

    public boolean nioKeepAlive() {
        return this.nioKeepAlive;
    }

    public URI endpoint() {
        return URI.create(format("http://%s:%s", "127.0.0.1", httpConnectorConfig().get().port()));
    }

    /**
     * Builder.
     *
     * @param <T> the type of the Builder
     */
    @JsonPOJOBuilder(buildMethodName = "build", withPrefix = "set")
    public static class Builder<T extends Builder<T>> {
        protected Integer bossThreadsCount;
        protected Integer workerThreadsCount;
        protected Integer nioAcceptorBacklog;
        protected Boolean tcpNoDelay;
        protected Boolean nioReuseAddress;
        protected Boolean nioKeepAlive;
        protected Integer maxInitialLineLength;
        protected Integer maxHeaderSize;
        protected Integer maxChunkSize;
        protected Integer maxContentLength;
        protected Integer requestTimeoutMs;
        protected Integer keepAliveTimeoutMillis;
        protected Integer maxConnectionsCount;
        protected HttpConnectorConfig httpConnectorConfig;
        protected HttpsConnectorConfig httpsConnectorConfig;

        public Builder httpPort(int port) {
            return (T) setHttpConnector(new HttpConnectorConfig(port));
        }

        @JsonProperty("bossThreadsCount")
        public T setBossThreadsCount(Integer bossThreadsCount) {
            this.bossThreadsCount = bossThreadsCount;
            return (T) this;
        }

        @JsonProperty("workerThreadsCount")
        public T setWorkerThreadsCount(Integer workerThreadsCount) {
            this.workerThreadsCount = workerThreadsCount;
            return (T) this;
        }

        @JsonProperty("nioAcceptorBacklog")
        public T setNioAcceptorBacklog(Integer nioAcceptorBacklog) {
            this.nioAcceptorBacklog = nioAcceptorBacklog;
            return (T) this;
        }

        @JsonProperty("tcpNoDelay")
        public T setTcpNoDelay(Boolean tcpNoDelay) {
            this.tcpNoDelay = tcpNoDelay;
            return (T) this;
        }

        @JsonProperty("nioReuseAddress")
        public T setNioReuseAddress(Boolean nioReuseAddress) {
            this.nioReuseAddress = nioReuseAddress;
            return (T) this;
        }

        @JsonProperty("nioKeepAlive")
        public T setNioKeepAlive(Boolean nioKeepAlive) {
            this.nioKeepAlive = nioKeepAlive;
            return (T) this;
        }

        @JsonProperty("maxInitialLineLength")
        public T setMaxInitialLineLength(Integer maxInitialLineLength) {
            this.maxInitialLineLength = maxInitialLineLength;
            return (T) this;
        }

        @JsonProperty("maxHeaderSize")
        public T setMaxHeaderSize(Integer maxHeaderSize) {
            this.maxHeaderSize = maxHeaderSize;
            return (T) this;
        }

        @JsonProperty("maxChunkSize")
        public T setMaxChunkSize(Integer maxChunkSize) {
            this.maxChunkSize = maxChunkSize;
            return (T) this;
        }

        @JsonProperty("maxContentLength")
        public T setMaxContentLength(Integer maxContentLength) {
            this.maxContentLength = maxContentLength;
            return (T) this;
        }

        @JsonProperty("requestTimeoutMillis")
        public T setRequestTimeoutMs(Integer requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
            return (T) this;
        }

        @JsonProperty("keepAliveTimeoutMillis")
        public T setKeepAliveTimeoutMillis(Integer keepAliveTimeoutMillis) {
            this.keepAliveTimeoutMillis = keepAliveTimeoutMillis;
            return (T) this;
        }

        @JsonProperty("connectors")
        public T setConnectors(Connectors connectors) {
            this.httpConnectorConfig = connectors.http;
            this.httpsConnectorConfig = connectors.https;
            return (T) this;
        }

        public T setHttpConnector(HttpConnectorConfig httpConnector) {
            this.httpConnectorConfig = httpConnector;
            return (T) this;
        }

        public T setHttpsConnector(HttpsConnectorConfig httpsConnector) {
            this.httpsConnectorConfig = httpsConnector;
            return (T) this;
        }

        @JsonProperty("maxConnectionsCount")
        public T setMaxConnectionsCount(Integer maxConnectionsCount) {
            this.maxConnectionsCount = maxConnectionsCount;
            return (T) this;
        }

        public NettyServerConfig build() {
            return new NettyServerConfig(this);
        }
    }

    /**
     * Connectors.
     */
    public static class Connectors {
        private final HttpConnectorConfig http;
        private final HttpsConnectorConfig https;

        public Connectors(@JsonProperty("http") HttpConnectorConfig http,
                          @JsonProperty("https") HttpsConnectorConfig https) {
            this.http = http;
            this.https = https;
        }
    }
}
