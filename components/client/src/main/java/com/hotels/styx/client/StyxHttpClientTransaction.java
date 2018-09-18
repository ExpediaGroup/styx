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

import com.hotels.styx.api.FullHttpRequest;
import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.client.netty.connectionpool.NettyConnectionFactory;

import java.util.concurrent.CompletableFuture;

class StyxHttpClientTransaction implements HttpClient.Transaction {
    private StyxHttpClient.Builder builder;
    private NettyConnectionFactory connectionFactory;

    public StyxHttpClientTransaction(NettyConnectionFactory connectionFactory, TransactionParameters parent) {
        this.builder = parent.newBuilder();
        this.connectionFactory = connectionFactory;
    }

    @Override
    public HttpClient.Transaction secure() {
        this.builder.secure(true);
        return this;
    }

    @Override
    public HttpClient.Transaction secure(boolean secure) {
        this.builder.secure(secure);
        return this;
    }

    @Override
    public HttpClient.StreamingTransaction streaming() {
        return null;
    }

    @Override
    public CompletableFuture<FullHttpResponse> sendRequest(FullHttpRequest request) {
        return StyxHttpClient.sendRequestInternal(connectionFactory, request, new TransactionParameters(builder));
    }
}
