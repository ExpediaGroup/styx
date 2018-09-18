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
import com.hotels.styx.api.extension.service.TlsSettings;

import java.util.Optional;

import static com.hotels.styx.api.HttpHeaderNames.USER_AGENT;

class TransactionParameters {
    static final TlsSettings DEFAULT_TLS_SETTINGS = new TlsSettings.Builder().build();
    private final int connectTimeoutMillis;
    private final Optional<String> userAgent;
    private final int maxResponseSize;
    private final boolean isHttps;
    private final int responseTimeout;
    private TlsSettings tlsSettings;

    TransactionParameters(StyxHttpClient.Builder builder) {
        this.userAgent = Optional.ofNullable(builder.userAgent());
        this.connectTimeoutMillis = builder.connectTimeoutMillis();
        this.maxResponseSize = builder.maxResponseSize();
        this.isHttps = builder.https();
        this.tlsSettings = builder.tlsSettings();
        this.responseTimeout = builder.responseTimeout();
    }

    Optional<String> userAgent() {
        return userAgent;
    }

    int connectionSettings() {
        return connectTimeoutMillis;
    }

    int maxResponseSize() {
        return maxResponseSize;
    }

    boolean https() {
        return isHttps;
    }

    String threadName() {
        return "SimpleHttpClientThread";
    }

    int responseTimeout() {
        return this.responseTimeout;
    }

    FullHttpRequest addUserAgent(FullHttpRequest request) {
        return userAgent.map(value ->
            request.newBuilder()
                    .header(USER_AGENT, value)
                    .build())
                .orElse(request);
    }

    StyxHttpClient.Builder newBuilder() {
        StyxHttpClient.Builder builder = new StyxHttpClient.Builder()
                .connectTimeout(connectTimeoutMillis)
                .maxHeaderSize(maxResponseSize)
                .tlsSettings(Optional.ofNullable(tlsSettings).orElse(DEFAULT_TLS_SETTINGS))
                .responseTimeout(responseTimeout)
                .secure(isHttps);

        userAgent.ifPresent(builder::userAgent);

        return builder;
    }

    int maxHeaderSize() {
        return 8192;
    }

    Optional<TlsSettings> tlsSettings() {
        return Optional.ofNullable(tlsSettings);
    }

}
