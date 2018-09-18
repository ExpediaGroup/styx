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
import com.hotels.styx.api.FullHttpRequest;
import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.api.Url;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.TlsSettings;
import com.hotels.styx.client.netty.connectionpool.HttpRequestOperation;
import com.hotels.styx.client.netty.connectionpool.NettyConnectionFactory;
import com.hotels.styx.client.ssl.SslContextFactory;
import io.netty.handler.ssl.SslContext;
import rx.Observable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpHeaderNames.USER_AGENT;
import static com.hotels.styx.api.StyxInternalObservables.toRxObservable;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.client.HttpConfig.newHttpConfigBuilder;
import static com.hotels.styx.common.CompletableFutures.fromSingleObservable;
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
        transactionParameters = parameters;
        this.connectionFactory = connectionFactory;
    }

    /**
     * Shuts the styx HTTP client thread pool.
     *
     * @return A {@link CompletableFuture} that completes when the thread pool is terminated
     */
    public CompletableFuture<Void> shutdown() {
        return connectionFactory.close();
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
     *
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
     * Sends a request as {@link FullHttpRequest} object.
     *
     * @param request a {@link FullHttpRequest} object to be sent to remote origin.
     * @return a {@link CompletableFuture} of response
     */
    public CompletableFuture<FullHttpResponse> send(FullHttpRequest request) {
        return sendRequestInternal(connectionFactory, request, this.transactionParameters);
    }

    @VisibleForTesting
    static CompletableFuture<FullHttpResponse> sendRequestInternal(NettyConnectionFactory connectionFactory, FullHttpRequest request, Builder params) {
        FullHttpRequest networkRequest = addUserAgent(params.userAgent(), request);
        Origin origin = originFromRequest(networkRequest, params.https());

        SslContext sslContext = getSslContext(params.https(), params.tlsSettings());

        Observable<FullHttpResponse> responseObservable = connectionFactory.createConnection(
                origin,
                new ConnectionSettings(params.connectTimeoutMillis()),
                sslContext
        ).flatMap(connection ->
                connection.write(networkRequest.toStreamingRequest())
                        .flatMap(response -> toRxObservable(response.toFullResponse(params.maxResponseSize())))
                        .doOnTerminate(connection::close)
        );

        return fromSingleObservable(responseObservable);

    }

    private static FullHttpRequest addUserAgent(String userAgent, FullHttpRequest request) {
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

    private static Origin originFromRequest(FullHttpRequest request, Boolean isHttps) {
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
        private String threadName = "simple-netty-http-client";
        private int connectTimeoutMillis = 1000;
        private int maxResponseSize = 1024 * 100;
        private int responseTimeout = 60000;
        private int maxHeaderSize = 8192;
        private TlsSettings tlsSettings;
        private boolean isHttps;
        private String userAgent;

        public Builder() {
        }

        Builder(Builder another) {
            this.threadName = another.threadName;
            this.connectTimeoutMillis = another.connectTimeoutMillis;
            this.maxResponseSize = another.maxResponseSize;
            this.responseTimeout = another.responseTimeout;
            this.maxHeaderSize = another.maxHeaderSize;
            this.tlsSettings = another.tlsSettings;
            this.isHttps = another.isHttps;
            this.userAgent = another.userAgent;
        }

        /**
         * Sets a thread name used for the thread pool.
         *
         * @param threadName thread name
         * @return this {@link Builder}
         */
        public Builder threadName(String threadName) {
            this.threadName = threadName;
            return this;
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
         *
         * Sets a maximum tolerated length of inactivity on TCP connection before remote origin is considered
         * unresponsive. After this time a {@link com.hotels.styx.api.exceptions.ResponseTimeoutException} is
         * thrown is emitted on the response future.
         *
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

        /**
         * Construct a client instance.
         *
         * @return a new instance
         */
        public StyxHttpClient build() {
            NettyConnectionFactory connectionFactory = new NettyConnectionFactory.Builder()
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

            return new StyxHttpClient(connectionFactory, this.copy());
        }

    }
}
