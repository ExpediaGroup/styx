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
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;

import java.util.concurrent.CompletableFuture;

/**
 * HTTP Client that returns an observable of response.
 */
public interface HttpClient {
//    CompletableFuture<FullHttpResponse> sendRequest(HttpRequest request);
    CompletableFuture<FullHttpResponse> sendRequest(FullHttpRequest request);

    interface Transaction {
        Transaction secure();

        Transaction secure(boolean secure);

        Transaction userAgent(String userAgent);

        StreamingTransaction streaming();

//        CompletableFuture<FullHttpResponse> sendRequest(HttpRequest request);
        CompletableFuture<FullHttpResponse> sendRequest(FullHttpRequest request);
    }

    interface StreamingTransaction {
        CompletableFuture<HttpResponse> sendRequest(HttpRequest request);
        CompletableFuture<HttpResponse> sendRequest(FullHttpRequest request);
    }
}
