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
package com.hotels.styx.client;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.HostAndPort;
import com.hotels.styx.NettyExecutor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.Url;
import com.hotels.styx.api.exceptions.ContentTimeoutException;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.TlsSettings;
import com.hotels.styx.client.netty.connectionpool.NettyConnectionFactory;
import com.hotels.styx.client.ssl.SslContextFactory;
import io.netty.handler.ssl.SslContext;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpHeaderNames.USER_AGENT;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.client.HttpConfig.newHttpConfigBuilder;
import static com.hotels.styx.client.HttpRequestOperationFactory.Builder.httpRequestOperationFactoryBuilder;
import static java.util.Objects.requireNonNull;

/**
 * A client that uses netty as transport.
 */
public final class StyxHttpClient implements HttpClient {
    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final int DEFAULT_HTTP_PORT = 80;

    private final Builder transactionParameters;
    private final NettyConnectionFactory connectionFactory;

    private StyxHttpClient(NettyConnectionFactory connectionFactory, Builder parameters) {
        this.transactionParameters = parameters;
        this.connectionFactory = connectionFactory;
    }

    /**
     * Indicates that a request should be sent using secure {@code https} protocol.
     *
     * @return a {@HttpClient.Transaction} instance that allows fluent method chaining
     */
    public HttpClient.Transaction secure() {
        return new StyxHttpClientTransaction(connectionFactory, this.transactionParameters.copy().secure(true));
    }

    /**
     * Indicates if a request should be sent over secure {@code https} or insecure {@code http} protocol.
     * <p>
     * A value of {@code true} indicates that a request should be sent over a secure {@code https} protocol.
     * A value of (@code false} indicates that a request should be sent over an insecure {@code http} protocol.
     *
     * @param secure a boolean flag to indicate if a request should be sent over a secure protocol or not
     * @return a {@HttpClient.Transaction} instance that allows fluent method chaining
     */
    public HttpClient.Transaction secure(boolean secure) {
        return new StyxHttpClientTransaction(connectionFactory, this.transactionParameters.copy().secure(secure));
    }

    /**
     * Creates a streaming transaction that accepts a LiveHttpRequest.
     *
     * @return a StreamingTransaction object
     */
    public StreamingTransaction streaming() {
        return new StreamingTransaction() {
            @Override
            public CompletableFuture<LiveHttpResponse> send(LiveHttpRequest request) {
                return sendRequestInternal(connectionFactory, request, transactionParameters).toFuture();
            }

            @Override
            public CompletableFuture<LiveHttpResponse> send(HttpRequest request) {
                return sendRequestInternal(connectionFactory, request.stream(), transactionParameters).toFuture();
            }
        };
    }

    /**
     * Sends a request as {@link HttpRequest} object.
     *
     * @param request a {@link HttpRequest} object to be sent to remote origin.
     * @return a {@link CompletableFuture} of response
     */
    public CompletableFuture<HttpResponse> send(HttpRequest request) {
        return sendRequestInternal(connectionFactory, request.stream(), this.transactionParameters)
                .flatMap(response -> Mono.from(response.aggregate(this.transactionParameters.maxResponseSize())))
                .toFuture();
    }

    @VisibleForTesting
    static Mono<LiveHttpResponse> sendRequestInternal(NettyConnectionFactory connectionFactory, LiveHttpRequest request, Builder params) {
        LiveHttpRequest networkRequest = addUserAgent(params.userAgent(), request);
        Origin origin = originFromRequest(networkRequest, params.https());

        SslContext sslContext = getSslContext(params.https(), params.tlsSettings());

        return connectionFactory.createConnection(
                origin,
                new ConnectionSettings(params.connectTimeoutMillis()),
                sslContext
        ).flatMap(connection ->
                Mono.from(connection.write(networkRequest)
                        .doOnComplete(connection::close)
                        .doOnError(e -> connection.close())
                        .map(response -> response.newBuilder()
                            .body(it ->
                                it.doOnEnd(x -> connection.close())
                                  .doOnCancel(() -> connection.close())
                            )
                            .build()
                        )
                )
        );
    }

    private static LiveHttpRequest addUserAgent(String userAgent, LiveHttpRequest request) {
        if (userAgent != null) {
            return request.newBuilder()
                    .header(USER_AGENT, userAgent)
                    .build();
        } else {
            return request;
        }
    }


    private static SslContext getSslContext(boolean isHttps, TlsSettings tlsSettings) {
        if (isHttps) {
            return Optional.of(tlsSettings != null ? tlsSettings : new TlsSettings.Builder().build())
                    .map(SslContextFactory::get)
                    .orElse(null);
        } else {
            return null;
        }

    }

    private static Origin originFromRequest(LiveHttpRequest request, Boolean isHttps) {
        String hostAndPort = request.header(HOST)
                .orElseGet(() -> {
                    checkArgument(request.url().isAbsolute(), "host header is not set for request=%s", request);
                    return request.url().authority().map(Url.Authority::hostAndPort)
                            .orElseThrow(() -> new IllegalArgumentException("Cannot send request " + request + " as URL is not absolute and no HOST header is present"));
                });

        HostAndPort host = HostAndPort.fromString(hostAndPort);

        if (host.getPortOrDefault(-1) < 0) {
            host = host.withDefaultPort(isHttps ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT);
        }

        return newOriginBuilder(host.getHostText(), host.getPort()).build();
    }

    /**
     * Builder for {@link StyxHttpClient}.
     */
    public static class Builder {
        private static final NettyExecutor DEFAULT_EXECUTOR = NettyExecutor.create("Styx-Client", 0);

        private int connectTimeoutMillis = 1000;
        private int maxResponseSize = 1024 * 100;
        private int responseTimeout = 60000;
        private int maxHeaderSize = 8192;
        private TlsSettings tlsSettings;
        private boolean isHttps;
        private String userAgent;
        private NettyExecutor executor = DEFAULT_EXECUTOR;

        public Builder() {
        }

        Builder(Builder another) {
            this.connectTimeoutMillis = another.connectTimeoutMillis;
            this.maxResponseSize = another.maxResponseSize;
            this.responseTimeout = another.responseTimeout;
            this.maxHeaderSize = another.maxHeaderSize;
            this.tlsSettings = another.tlsSettings;
            this.isHttps = another.isHttps;
            this.userAgent = another.userAgent;
        }

        /**
         * Sets the TCP connection timeout.
         *
         * @param duration desired TCP connection timeout duration
         * @param timeUnit duration unit
         * @return this {@link Builder}
         */
        public Builder connectTimeout(int duration, TimeUnit timeUnit) {
            this.connectTimeoutMillis = (int) timeUnit.toMillis(duration);
            return this;
        }

        int connectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        /**
         * Sets the maximum allowed response size in bytes.
         *
         * @param maxResponseSize maximum response size in bytes.
         * @return this {@link Builder}
         */
        public Builder maxResponseSize(int maxResponseSize) {
            this.maxResponseSize = maxResponseSize;
            return this;
        }

        int maxResponseSize() {
            return this.maxResponseSize;
        }

        /**
         * Maximum time in milliseconds this client is willing to wait for the origin server to respond.
         * <p>
         * Sets a maximum tolerated length of inactivity on TCP connection before remote origin is considered
         * unresponsive. After this time a {@link ContentTimeoutException} is
         * thrown is emitted on the response future.
         * <p>
         * Note that an actual response can take considerably longer time to arrive than @{code responseTimeoutMillis}.
         * This can happen if origin sends the response slowly. Origin may send headers first, and then
         * slowly drip feed the response body. This is acceptable as long as the TCP connection does not
         * experience longer inactivity than @{code responseTimeoutMillis} between any two consecutive
         * data packets.
         *
         * @param duration maximum tolerated inactivity on the TCP connection.
         * @param timeUnit time unit for @{code duration}
         * @return this {@link Builder}
         */
        public Builder responseTimeout(int duration, TimeUnit timeUnit) {
            this.responseTimeout = (int) timeUnit.toMillis(duration);
            return this;
        }

        /**
         * Sets the maximum length for HTTP request or status line.
         *
         * @param maxHeaderSize maximum HTTP request or status line length in bytes
         * @return this {@link Builder}
         */
        public Builder maxHeaderSize(int maxHeaderSize) {
            this.maxHeaderSize = maxHeaderSize;
            return this;
        }

        /**
         * Sets the TLS parameters to be used with secure connections.
         * Implies that requests should be sent securely over @{code https} protocol.
         *
         * @param tlsSettings TLS parameters
         * @return this {@link Builder}
         */
        public Builder tlsSettings(TlsSettings tlsSettings) {
            this.tlsSettings = requireNonNull(tlsSettings);
            this.isHttps = true;
            return this;
        }

        TlsSettings tlsSettings() {
            return this.tlsSettings;
        }

        /**
         * Specifies whether requests should be sent securely or not.
         *
         * @param secure @{code true} if secure {@code https} protocol should be used, or {@code false} if insecure {@code http} protocol can be used
         * @return this {@link Builder}
         */
        public Builder secure(boolean secure) {
            this.isHttps = secure;
            return this;
        }

        boolean https() {
            return this.isHttps;
        }

        /**
         * Sets the user-agent header value to be included in requests.
         *
         * @param userAgent user-agent
         * @return this {@link Builder}
         */
        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        String userAgent() {
            return this.userAgent;
        }

        Builder copy() {
            return new Builder(this);
        }

        public Builder executor(NettyExecutor executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Construct a client instance.
         *
         * @return a new instance
         */
        public StyxHttpClient build() {
            NettyConnectionFactory connectionFactory = new NettyConnectionFactory.Builder()
                    .httpConfig(newHttpConfigBuilder().setMaxHeadersSize(maxHeaderSize).build())
                    .tlsSettings(tlsSettings)
                    .httpRequestOperationFactory(httpRequestOperationFactoryBuilder()
                            .responseTimeoutMillis(responseTimeout)
                            .build())
                    .executor(executor)
                    .build();

            return new StyxHttpClient(connectionFactory, this.copy());
        }

    }
}
