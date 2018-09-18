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

    public CompletableFuture<Void> shutdown() {
        return connectionFactory.close();
    }

    public HttpClient.Transaction secure() {
        return new StyxHttpClientTransaction(connectionFactory, this.transactionParameters.copy().secure(true));
    }

    public HttpClient.Transaction secure(boolean secure) {
        return new StyxHttpClientTransaction(connectionFactory, this.transactionParameters.copy().secure(secure));
    }

    public CompletableFuture<FullHttpResponse> sendRequest(FullHttpRequest request) {
        return sendRequestInternal(connectionFactory, request, this.transactionParameters);
    }

    @VisibleForTesting
    static CompletableFuture<FullHttpResponse> sendRequestInternal(NettyConnectionFactory connectionFactory, FullHttpRequest request, Builder params) {
        FullHttpRequest networkRequest = addUserAgent(params.userAgent, request);
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
        private NettyConnectionFactory connectionFactory;
        private TlsSettings tlsSettings;
        private String userAgent;
        private int connectTimeoutMillis = 1000;
        private int responseTimeout = 60000;
        private int maxResponseSize = 1024 * 100;
        private int maxHeaderSize = 8192;
        private String threadName = "simple-netty-http-client";
        private boolean isHttps;

        public Builder() {

        }

//        public Builder(TransactionParameters parameters) {
//            this.connectTimeoutMillis = parameters.connectionTimeoutMillis();
//            this.maxResponseSize = parameters.maxResponseSize();
//            this.tlsSettings = parameters.tlsSettings().orElse(new TlsSettings.Builder().build());
//            this.responseTimeout = parameters.responseTimeout();
//            this.isHttps = parameters.https();
//            this.userAgent = parameters.userAgent().orElse(null);
//        }

        public Builder(Builder another) {
            this.connectTimeoutMillis = another.connectTimeoutMillis;
            this.maxResponseSize = another.maxResponseSize;
            this.tlsSettings = another.tlsSettings;
            this.responseTimeout = another.responseTimeout;
            this.isHttps = another.isHttps;
            this.userAgent = another.userAgent;
            this.maxHeaderSize = another.maxHeaderSize;
        }

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

            return new StyxHttpClient(connectionFactory, this);
        }

    }
}
