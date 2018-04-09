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
package com.hotels.styx.proxy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.hotels.styx.server.HttpConnectorConfig;
import com.hotels.styx.server.HttpsConnectorConfig;
import com.hotels.styx.server.netty.NettyServerConfig;

/**
 * Configuration for proxy server.
 */
@JsonDeserialize(builder = ProxyServerConfig.Builder.class)
public class ProxyServerConfig extends NettyServerConfig {
    private final int clientWorkerThreadsCount;

    public ProxyServerConfig() {
        this.clientWorkerThreadsCount = HALF_OF_AVAILABLE_PROCESSORS;
    }

    private ProxyServerConfig(Builder builder) {
        super(builder.builder);

        Integer clientThreads = builder.clientWorkerThreadsCount;

        this.clientWorkerThreadsCount = clientThreads == null || clientThreads == 0 ? HALF_OF_AVAILABLE_PROCESSORS : clientThreads;
    }

    public int clientWorkerThreadsCount() {
        return clientWorkerThreadsCount;
    }

    /**
     * Builder.
     */
    @JsonPOJOBuilder(buildMethodName = "build", withPrefix = "set")
    public static class Builder {
        private final NettyServerConfig.Builder builder = new NettyServerConfig.Builder();
        private Integer clientWorkerThreadsCount;

        @JsonProperty("bossThreadsCount")
        public Builder setBossThreadsCount(Integer bossThreadsCount) {
            builder.setBossThreadsCount(bossThreadsCount);
            return this;
        }

        @JsonProperty("workerThreadsCount")
        public Builder setWorkerThreadsCount(Integer workerThreadsCount) {
            builder.setWorkerThreadsCount(workerThreadsCount);
            return this;
        }

        @JsonProperty("nioAcceptorBacklog")
        public Builder setNioAcceptorBacklog(Integer nioAcceptorBacklog) {
            builder.setNioAcceptorBacklog(nioAcceptorBacklog);
            return this;
        }

        @JsonProperty("tcpNoDelay")
        public Builder setTcpNoDelay(Boolean tcpNoDelay) {
            builder.setTcpNoDelay(tcpNoDelay);
            return this;
        }

        @JsonProperty("nioReuseAddress")
        public Builder setNioReuseAddress(Boolean nioReuseAddress) {
            builder.setNioReuseAddress(nioReuseAddress);
            return this;
        }

        @JsonProperty("nioKeepAlive")
        public Builder setNioKeepAlive(Boolean nioKeepAlive) {
            builder.setNioKeepAlive(nioKeepAlive);
            return this;
        }

        @JsonProperty("maxInitialLineLength")
        public Builder setMaxInitialLineLength(Integer maxInitialLineLength) {
            builder.setMaxInitialLineLength(maxInitialLineLength);
            return this;
        }

        @JsonProperty("maxHeaderSize")
        public Builder setMaxHeaderSize(Integer maxHeaderSize) {
            builder.setMaxHeaderSize(maxHeaderSize);
            return this;
        }

        @JsonProperty("maxChunkSize")
        public Builder setMaxChunkSize(Integer maxChunkSize) {
            builder.setMaxChunkSize(maxChunkSize);
            return this;
        }

        @JsonProperty("maxContentLength")
        public Builder setMaxContentLength(Integer maxContentLength) {
            builder.setMaxContentLength(maxContentLength);
            return this;
        }

        @JsonProperty("requestTimeoutMillis")
        public Builder setRequestTimeoutMillis(Integer requestTimeoutMillis) {
            builder.setRequestTimeoutMs(requestTimeoutMillis);
            return this;
        }

        @JsonProperty("keepAliveTimeoutMillis")
        public Builder setKeepAliveTimeoutMillis(Integer keepAliveTimeoutMillis) {
            builder.setKeepAliveTimeoutMillis(keepAliveTimeoutMillis);
            return this;
        }

        @JsonProperty("connectors")
        public Builder setConnectors(Connectors connectors) {
            builder.setConnectors(connectors);
            return this;
        }

        public Builder setHttpConnector(HttpConnectorConfig httpConnector) {
            builder.setHttpConnector(httpConnector);
            return this;
        }

        public Builder setHttpsConnector(HttpsConnectorConfig httpsConnector) {
            builder.setHttpsConnector(httpsConnector);
            return this;
        }

        @JsonProperty("maxConnectionsCount")
        public Builder setMaxConnectionsCount(Integer maxConnectionsCount) {
            builder.setMaxConnectionsCount(maxConnectionsCount);
            return this;
        }

        @JsonProperty("clientWorkerThreadsCount")
        public Builder setClientWorkerThreadsCount(Integer clientWorkerThreadsCount) {
            this.clientWorkerThreadsCount = clientWorkerThreadsCount;
            return this;
        }

        public ProxyServerConfig build() {
            if (clientWorkerThreadsCount == null || clientWorkerThreadsCount == 0) {
                clientWorkerThreadsCount = HALF_OF_AVAILABLE_PROCESSORS;
            }

            return new ProxyServerConfig(this);
        }
    }
}
