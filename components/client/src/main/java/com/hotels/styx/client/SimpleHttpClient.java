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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.HostAndPort;
import com.hotels.styx.api.FullHttpClient;
import com.hotels.styx.api.FullHttpRequest;
import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.api.Url;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.TlsSettings;
import com.hotels.styx.client.netty.connectionpool.HttpRequestOperation;
import com.hotels.styx.client.netty.connectionpool.NettyConnectionFactory;
import rx.Observable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkArgument;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpHeaderNames.USER_AGENT;
import static com.hotels.styx.api.StyxInternalObservables.toRxObservable;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.client.HttpConfig.newHttpConfigBuilder;
import static com.hotels.styx.common.CompletableFutures.fromSingleObservable;

/**
 * A client that uses netty as transport.
 */
public final class SimpleHttpClient implements FullHttpClient {
    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final int DEFAULT_HTTP_PORT = 80;

    private final Optional<String> userAgent;
    private final ConnectionSettings connectionSettings;
    private final int maxResponseSize;
    private final Connection.Factory connectionFactory;

    private SimpleHttpClient(Builder builder) {
        this.userAgent = Optional.ofNullable(builder.userAgent);
        this.connectionSettings = builder.connectionSettings;
        this.maxResponseSize = builder.maxResponseSize;
        this.connectionFactory = builder.connectionFactory;
    }

    public CompletableFuture<FullHttpResponse> sendRequest(FullHttpRequest request) {
        FullHttpRequest networkRequest = addUserAgent(request);
        Origin origin = originFromRequest(networkRequest);

        Observable<FullHttpResponse> responseObservable = connectionFactory.createConnection(origin, connectionSettings)
                .flatMap(connection -> connection.write(networkRequest.toStreamingRequest())
                        .flatMap(response -> toRxObservable(response.toFullResponse(maxResponseSize)))
                        .doOnTerminate(connection::close));

        return fromSingleObservable(responseObservable);
    }

    private FullHttpRequest addUserAgent(FullHttpRequest request) {
        return Optional.of(request)
                .filter(req -> !req.header(USER_AGENT).isPresent())
                .flatMap(req -> userAgent.map(userAgent -> request.newBuilder()
                        .addHeader(USER_AGENT, userAgent)
                        .build()))
                .orElse(request);
    }

    private static Origin originFromRequest(FullHttpRequest request) {
        String hostAndPort = request.header(HOST)
                .orElseGet(() -> {
                    checkArgument(request.url().isAbsolute(), "host header is not set for request=%s", request);
                    return request.url().authority().map(Url.Authority::hostAndPort)
                            .orElseThrow(() -> new IllegalArgumentException("Cannot send request " + request + " as URL is not absolute and no HOST header is present"));
                });

        HostAndPort host = HostAndPort.fromString(hostAndPort);

        if (host.getPortOrDefault(-1) < 0) {
            host = host.withDefaultPort(request.isSecure() ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT);
        }

        return newOriginBuilder(host).build();
    }

    /**
     * Builder for {@link SimpleHttpClient}.
     */
    public static class Builder {
        private Connection.Factory connectionFactory;
        private TlsSettings tlsSettings;
        private String userAgent;
        private ConnectionSettings connectionSettings = new ConnectionSettings(1000);
        private int responseTimeout = 60000;
        private int maxResponseSize = 1024 * 100;
        private int maxHeaderSize = 8192;
        private String threadName = "simple-netty-http-client";

        public Builder connectionSettings(ConnectionSettings connectionSettings) {
            this.connectionSettings = connectionSettings;
            return this;
        }

        /**
         * Sets the user-agent header value to be included in requests.
         *
         * @param userAgent user-agent
         * @return this builder
         */
        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder responseTimeoutMillis(int responseTimeout) {
            this.responseTimeout = responseTimeout;
            return this;
        }

        public Builder maxResponseSize(int maxResponseSize) {
            this.maxResponseSize = maxResponseSize;
            return this;
        }

        public Builder tlsSettings(TlsSettings tlsSettings) {
            this.tlsSettings = tlsSettings;
            return this;
        }

        public Builder maxHeaderSize(int maxHeaderSize) {
            this.maxHeaderSize = maxHeaderSize;
            return this;
        }

        public Builder threadName(String threadName) {
            this.threadName = threadName;
            return this;
        }

        @VisibleForTesting
        Builder setConnectionFactory(Connection.Factory connectionFactory) {
            this.connectionFactory = connectionFactory;
            return this;
        }

        /**
         * Construct a client instance.
         *
         * @return a new instance
         */
        public SimpleHttpClient build() {
            connectionFactory = connectionFactory != null ? connectionFactory : new NettyConnectionFactory.Builder()
                    .name(threadName)
                    .httpConfig(newHttpConfigBuilder().setMaxHeadersSize(maxHeaderSize).build())
                    .tlsSettings(tlsSettings)
                    .httpRequestOperationFactory(request -> new HttpRequestOperation(
                            request,
                            null,
                            false,
                            responseTimeout,
                            false,
                            false))
                    .build();
            return new SimpleHttpClient(this);
        }
    }
}
