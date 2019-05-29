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
package com.hotels.styx;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.plugins.spi.Plugin;

import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * You can replace content after aggregating it in memory.
 * For example, if the message content contains a JSON object and you need to modify the object somehow.
 * You can aggregate the live HTTP message into a full HTTP message. Transform the content into a full message context
 * and convert the results back to live HTTP message
 *
 *This can be used when you need the entire content in order to operate on it.
 *Please note that this uses more heap space as the full response is transiently stored in the heap.
 */

public class ModifyContentByAggregationExample implements Plugin {
    private final Config config;

    public ModifyContentByAggregationExample(Config config) {
        this.config = requireNonNull(config);
    }

    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {
        return chain.proceed(request)
                .flatMap(response -> response.aggregate(10000))
                .map(response -> {
                    String body = response.bodyAs(UTF_8);
                    return response.newBuilder()
                            .body(body + config.extraText(), UTF_8)
                            .build();
                })
                .map(HttpResponse::stream);
    }

    public static class Config {
        private final String extraText;

        public Config(String extraText) {
            this.extraText = requireNonNull(extraText);
        }

        public String extraText() {
            return extraText;
        }
    }
}

