/*
  Copyright (C) 2013-2023 Expedia Inc.

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

import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.client.netty.connectionpool.HttpRequestOperation;
import com.hotels.styx.common.format.DefaultHttpMessageFormatter;
import com.hotels.styx.common.format.HttpMessageFormatter;
import com.hotels.styx.metrics.CentralisedMetrics;

import static java.util.Objects.requireNonNull;

/**
 * A Factory for creating an HttpRequestOperation from an LiveHttpRequest.
 */
public interface HttpRequestOperationFactory {
    /**
     * Create a new operation for handling the http request.
     *
     * @param request the http request
     * @return a new http operation
     */
    HttpRequestOperation newHttpRequestOperation(LiveHttpRequest request);

    /**
     * Builds HttpRequestOperationFactory objects.
     */
    class Builder {
        OriginStatsFactory originStatsFactory;
        int responseTimeoutMillis = 60000;
        boolean flowControlEnabled;
        boolean requestLoggingEnabled;
        boolean longFormat;
        HttpMessageFormatter httpMessageFormatter = new DefaultHttpMessageFormatter();
        private CentralisedMetrics metrics;

        public static Builder httpRequestOperationFactoryBuilder() {
            return new Builder();
        }

        public Builder originStatsFactory(OriginStatsFactory factory) {
            this.originStatsFactory = factory;
            return this;
        }

        public Builder responseTimeoutMillis(int responseTimeoutMillis) {
            this.responseTimeoutMillis = responseTimeoutMillis;
            return this;
        }

        public Builder flowControlEnabled(boolean flowControlEnabled) {
            this.flowControlEnabled = flowControlEnabled;
            return this;
        }

        public Builder requestLoggingEnabled(boolean requestLoggingEnabled) {
            this.requestLoggingEnabled = requestLoggingEnabled;
            return this;
        }

        public Builder longFormat(boolean longFormat) {
            this.longFormat = longFormat;
            return this;
        }

        public Builder httpMessageFormatter(HttpMessageFormatter httpMessageFormatter) {
            this.httpMessageFormatter = requireNonNull(httpMessageFormatter);
            return this;
        }

        public Builder metrics(CentralisedMetrics metrics) {
            this.metrics = metrics;
            return this;
        }

        // todo reset method name: i made it wrong on purpose to find all the places its used
        public HttpRequestOperationFactory build() {
            return request -> new HttpRequestOperation(
                    metrics,
                    request,
                    originStatsFactory,
                    responseTimeoutMillis,
                    requestLoggingEnabled,
                    longFormat,
                    httpMessageFormatter);
        }
    }

}
