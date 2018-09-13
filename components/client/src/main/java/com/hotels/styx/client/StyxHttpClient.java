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

/**
 * A client that uses netty as transport.
 */
public final class StyxHttpClient implements HttpClient {
    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final int DEFAULT_HTTP_PORT = 80;

    private final ClientParameters clientParameters;
    final NettyConnectionFactory connectionFactory;

    private StyxHttpClient(Builder builder) {
        clientParameters = new ClientParameters(builder);
        connectionFactory = new NettyConnectionFactory.Builder()
                .name(clientParameters.threadName())
                .httpConfig(newHttpConfigBuilder().setMaxHeadersSize(clientParameters.maxHeaderSize()).build())
                .tlsSettings(clientParameters.tlsSettings().orElseGet(() -> clientParameters.https() ? new TlsSettings.Builder().build() : null))
                .httpRequestOperationFactory(aRequest -> new HttpRequestOperation(
                        aRequest,
                        null,
                        false,
                        clientParameters.responseTimeout(),
                        false,
                        false))
                .build();

    }

    public HttpClient.Transaction secure() {
        ClientParameters parameters = new ClientParameters(this.clientParameters.newBuilder().secure(true));
        return new StyxHttpClientTransaction(connectionFactory, parameters);
    }

    public HttpClient.Transaction secure(boolean secure) {
        if (!secure && this.clientParameters.https()) {
            throw new IllegalArgumentException("An insecure HTTP transaction is not allowed with HTTPS configuration.");
        }
        ClientParameters parameters = new ClientParameters(this.clientParameters.newBuilder().secure(secure));
        return new StyxHttpClientTransaction(connectionFactory, parameters);
    }

//    public HttpClient.Transaction secure(TlsSettings tlsSettings) {
//        ClientParameters parameters = new ClientParameters(this.clientParameters.newBuilder().tlsSettings(tlsSettings));
//        return new StyxHttpClientTransaction(connectionFactory, parameters);
//    }

    public HttpClient.Transaction userAgent(String userAgent) {
        ClientParameters parameters = new ClientParameters(this.clientParameters.newBuilder().userAgent(userAgent));
        return new StyxHttpClientTransaction(connectionFactory, parameters);
    }

    public CompletableFuture<FullHttpResponse> sendRequest(FullHttpRequest request) {
        return sendRequestInternal(connectionFactory, request, this.clientParameters);
    }

    private static CompletableFuture<FullHttpResponse> sendRequestInternal(NettyConnectionFactory connectionFactory, FullHttpRequest request, ClientParameters parent) {
        FullHttpRequest networkRequest = parent.addUserAgent(request);
        Origin origin = originFromRequest(networkRequest, parent.https());

        TlsSettings tlsSettings = parent.tlsSettings().orElseGet(() -> parent.https() ? new TlsSettings.Builder().build() : null);

        SslContext sslContext = Optional.ofNullable(tlsSettings)
                .map(SslContextFactory::get)
                .orElse(null);

        Observable<FullHttpResponse> responseObservable = connectionFactory.createConnection(origin, parent.connectionSettings(), sslContext)
                .flatMap(connection ->
                        connection.write(networkRequest.toStreamingRequest())
                                .flatMap(response -> toRxObservable(response.toFullResponse(parent.maxResponseSize())))
                                .doOnTerminate(connection::close)
                );

        return fromSingleObservable(responseObservable);

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


    static class ClientParameters {
        private final ConnectionSettings connectionSettings;
        private final Optional<String> userAgent;
        private final int maxResponseSize;
        private final boolean isHttps;
        private TlsSettings tlsSettings;

        private ClientParameters(Builder builder) {
            this.userAgent = Optional.ofNullable(builder.userAgent);
            this.connectionSettings = builder.connectionSettings;
            this.maxResponseSize = builder.maxResponseSize;
            this.isHttps = builder.isHttps;
            this.tlsSettings = builder.tlsSettings;
        }

        private Optional<String> userAgent() {
            return userAgent;
        }

        private ConnectionSettings connectionSettings() {
            return connectionSettings;
        }

        private int maxResponseSize() {
            return maxResponseSize;
        }

        private boolean https() {
            return isHttps;
        }

        public String threadName() {
            return "SimpleHttpClientThread";
        }

        private FullHttpRequest addUserAgent(FullHttpRequest request) {
            return Optional.of(request)
                    .filter(req -> !req.header(USER_AGENT).isPresent())
                    .flatMap(req -> userAgent.map(userAgent -> request.newBuilder()
                            .addHeader(USER_AGENT, userAgent)
                            .build()))
                    .orElse(request);
        }

        public Builder newBuilder() {
            Builder builder = new Builder()
                    .connectionSettings(connectionSettings)
                    .maxHeaderSize(maxResponseSize)
                    .tlsSettings(tlsSettings)
                    .secure(isHttps);

            userAgent.ifPresent(builder::userAgent);

            return builder;
        }

        public int maxHeaderSize() {
            return 8192;
        }

        public Optional<TlsSettings> tlsSettings() {
            return Optional.ofNullable(tlsSettings);
        }

        public int responseTimeout() {
            return 60000;
        }
    }

    static class StyxHttpClientTransaction implements HttpClient.Transaction {
        private Builder builder;
        private NettyConnectionFactory connectionFactory;

        public StyxHttpClientTransaction(NettyConnectionFactory connectionFactory, ClientParameters parent) {
            this.builder = parent.newBuilder();
            this.connectionFactory = connectionFactory;
        }

        @Override
        public Transaction secure() {
            this.builder.secure(true);
            return this;
        }

        @Override
        public Transaction secure(boolean secure) {
            this.builder.secure(secure);
            return this;
        }

        @Override
        public Transaction userAgent(String userAgent) {
            this.builder.userAgent(userAgent);
            return this;        }

        @Override
        public StreamingTransaction streaming() {
            return null;
        }

        @Override
        public CompletableFuture<FullHttpResponse> sendRequest(FullHttpRequest request) {
            return StyxHttpClient.sendRequestInternal(connectionFactory, request, new ClientParameters(builder));
        }
    }

    /**
     * Builder for {@link StyxHttpClient}.
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
        private boolean isHttps;

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
            this.isHttps = tlsSettings != null;
            return this;
        }

        public Builder secure(boolean secure) {
            this.isHttps = secure;            return this;
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
        public StyxHttpClient build() {
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
            return new StyxHttpClient(this);
        }
    }
}
