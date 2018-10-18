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

import java.util.concurrent.CompletableFuture;

/**
 * A Styx HTTP client interface.
 *
 * This interface offers a fluent interface to build and configure HTTP
 * request transactions from a client instance. The requests can be consumed
 * either aggregated {@link HttpResponse} or streaming {@link LiveHttpRequest}
 * messages.
 */
public interface HttpClient {

    /**
     * Sends a HTTP request message using this client.
     *
     * @deprecated use {@link this::send} instead.
     *
     * @param request a full HTTP request object
     * @return a future of full HTTP request object
     */
    @Deprecated
    default CompletableFuture<HttpResponse> sendRequest(HttpRequest request) {
        return send(request);
    }

    /**
     * Sends a HTTP request message using this client.
     *
     * @param request a full HTTP request object
     * @return a future of full HTTP request object
     */
    CompletableFuture<HttpResponse> send(HttpRequest request);

    /**
     * A HTTP request transaction.
     *
     * This interface allows client attributes and context to be customised
     * for each request without having to rely on configured default values
     * in the client.
     *
     */
    interface Transaction {
        /**
         * Send the request using TLS protocol.
         *
         * @return this {@code Transaction} object
         */
        Transaction secure();

        /**
         * Determines if the request should be sent securely or not.
         *
         * @param secure Set to {@code true} if the request should be sent securely,
         *               or {@code false} if the request should be sent insecurely.
         * @return this {@code Transaction} object
         */
        Transaction secure(boolean secure);

        /**
         * Converts the transaction object to streaming transaction.
         *
         * A call to {@code streaming()} converts this {@link Transaction} object to
         * a {@link StreamingTransaction}. This allows responses to be consumed
         * in streaming responses.
         *
         * @return a {@link StreamingTransaction} object
         */
        StreamingTransaction streaming();

        /**
         * Sends a HTTP request message using this client.
         *
         * @param request a full HTTP request object
         * @return a future of full HTTP request object
         */
        CompletableFuture<HttpResponse> send(HttpRequest request);
    }

    /**
     * A streaming HTTP request transaction.
     *
     * This interface allows the response object to be consumed in a streaming
     * fashion instead of being aggregated into a HttpResponse.
     */
    interface StreamingTransaction {
        CompletableFuture<LiveHttpResponse> send(LiveHttpRequest request);
        CompletableFuture<LiveHttpResponse> send(HttpRequest request);
    }
}
