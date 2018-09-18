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

import static com.google.common.base.Preconditions.checkArgument;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
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

    private final TransactionParameters transactionParameters;
    final NettyConnectionFactory connectionFactory;

    private StyxHttpClient(Builder builder) {
        transactionParameters = new TransactionParameters(builder);
        connectionFactory = builder.connectionFactory;
    }

    public CompletableFuture<Void> shutdown() {
        return connectionFactory.close();
    }

    public HttpClient.Transaction secure() {
        TransactionParameters parameters = new TransactionParameters(this.transactionParameters.newBuilder().secure(true));
        return new StyxHttpClientTransaction(connectionFactory, parameters);
    }

    public HttpClient.Transaction secure(boolean secure) {
        TransactionParameters parameters = new TransactionParameters(this.transactionParameters.newBuilder().secure(secure));
        return new StyxHttpClientTransaction(connectionFactory, parameters);
    }

    public CompletableFuture<FullHttpResponse> sendRequest(FullHttpRequest request) {
        return sendRequestInternal(connectionFactory, request, this.transactionParameters);
    }

    @VisibleForTesting
    static CompletableFuture<FullHttpResponse> sendRequestInternal(NettyConnectionFactory connectionFactory, FullHttpRequest request, TransactionParameters params) {
        FullHttpRequest networkRequest = params.addUserAgent(request);
        Origin origin = originFromRequest(networkRequest, params.https());

        SslContext sslContext = getSslContext(params.https(), params.tlsSettings().orElse(null));

        Observable<FullHttpResponse> responseObservable = connectionFactory.createConnection(
                origin,
                new ConnectionSettings(params.connectionSettings()),
                sslContext
        ).flatMap(connection ->
                connection.write(networkRequest.toStreamingRequest())
                        .flatMap(response -> toRxObservable(response.toFullResponse(params.maxResponseSize())))
                        .doOnTerminate(connection::close)
        );

        return fromSingleObservable(responseObservable);

    }

    private static SslContext getSslContext(boolean isHttps, TlsSettings tlsSettings) {
        if (isHttps) {
            return Optional.of(tlsSettings != null ? tlsSettings : TransactionParameters.DEFAULT_TLS_SETTINGS)
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
        private NettyConnectionFactory connectionFactory;
        private TlsSettings tlsSettings;
        private String userAgent;
        private int connectTimeoutMillis = 1000;
        private int responseTimeout = 60000;
        private int maxResponseSize = 1024 * 100;
        private int maxHeaderSize = 8192;
        private String threadName = "simple-netty-http-client";
        private boolean isHttps;

        public Builder connectTimeout(int timeoutMs) {
            this.connectTimeoutMillis = timeoutMs;
            return this;
        }

        int connectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public Builder responseTimeout(int responseTimeoutMs) {
            this.responseTimeout = responseTimeoutMs;
            return this;
        }

        int responseTimeout() {
            return responseTimeout;
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

        String userAgent() {
            return this.userAgent;
        }

        public Builder maxResponseSize(int maxResponseSize) {
            this.maxResponseSize = maxResponseSize;
            return this;
        }

        int maxResponseSize() {
            return this.maxResponseSize;
        }

        public Builder tlsSettings(TlsSettings tlsSettings) {
            this.tlsSettings = requireNonNull(tlsSettings);
            this.isHttps = true;
            return this;
        }

        TlsSettings tlsSettings() {
            return this.tlsSettings;
        }

        public Builder secure(boolean secure) {
            this.isHttps = secure;
            return this;
        }

        boolean https() {
            return this.isHttps;
        }

        public Builder maxHeaderSize(int maxHeaderSize) {
            this.maxHeaderSize = maxHeaderSize;
            return this;
        }

        public Builder threadName(String threadName) {
            this.threadName = threadName;
            return this;
        }

        /**
         * Construct a client instance.
         *
         * @return a new instance
         */
        public StyxHttpClient build() {
            connectionFactory = new NettyConnectionFactory.Builder()
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
            return new StyxHttpClient(this);
        }
    }
}
