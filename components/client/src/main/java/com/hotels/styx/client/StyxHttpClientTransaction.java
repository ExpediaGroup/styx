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

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.client.netty.connectionpool.NettyConnectionFactory;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

class StyxHttpClientTransaction implements HttpClient.Transaction {
    private final StyxHttpClient.Builder transactionParameters;
    private final NettyConnectionFactory connectionFactory;

    public StyxHttpClientTransaction(NettyConnectionFactory connectionFactory, StyxHttpClient.Builder transactionParameters) {
        this.transactionParameters = transactionParameters;
        this.connectionFactory = connectionFactory;
    }

    /**
     * Indicates that a request should be sent using secure {@code https} protocol.
     *
     * @return a @{HttpClient.Transaction} instance that allows fluent method chaining
     */
    @Override
    public HttpClient.Transaction secure() {
        this.transactionParameters.secure(true);
        return this;
    }

    /**
     * Indicates if a request should be sent over secure {@code https} or insecure {@code http} protocol.
     *
     * A value of {@code true} indicates that a request should be sent over a secure {@code https} protocol.
     * A value of {@code false} indicates that a request should be sent over an insecure {@code http} protocol.
     *
     * @param secure a boolean flag to indicate if a request should be sent over a secure protocol or not
     * @return a @{HttpClient.Transaction} instance that allows fluent method chaining
     */
    @Override
    public HttpClient.Transaction secure(boolean secure) {
        this.transactionParameters.secure(secure);
        return this;
    }

    @Override
    public HttpClient.StreamingTransaction streaming() {
        return new HttpClient.StreamingTransaction() {
            @Override
            public CompletableFuture<LiveHttpResponse> send(LiveHttpRequest request) {
                return StyxHttpClient.sendRequestInternal(connectionFactory, request, transactionParameters).toFuture();
            }

            @Override
            public CompletableFuture<LiveHttpResponse> send(HttpRequest request) {
                return StyxHttpClient.sendRequestInternal(connectionFactory, request.stream(), transactionParameters).toFuture();
            }
        };
    }

    /**
     * Sends a request as {@link HttpRequest} object.
     *
     * @param request a {@link HttpRequest} object to be sent to remote origin.
     * @return a {@link CompletableFuture} of response
     */
    @Override
    public CompletableFuture<HttpResponse> send(HttpRequest request) {
        return StyxHttpClient.sendRequestInternal(connectionFactory, request.stream(), transactionParameters)
                .flatMap(response -> Mono.from(response.aggregate(transactionParameters.maxResponseSize())))
                .toFuture();
    }
}
