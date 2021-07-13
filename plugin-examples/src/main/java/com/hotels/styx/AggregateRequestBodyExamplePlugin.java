/*
  Copyright (C) 2013-2021 Expedia Inc.

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
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.plugins.spi.Plugin;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * Example to show how we can read the whole content and aggregate it in memory.
 * Remember that the original  {@link LiveHttpRequest} will not be valid anymore after aggregating the content,
 * and thus you must operate on the  {@link HttpRequest} object returned by aggregate.
 *
 * For example, if the message content contains a JSON object and you need to modify the object somehow.
 * You can aggregate the live HTTP message into a full HTTP message. Transform the content into a full message context
 * and convert the results back to live HTTP message
 *
 *This can be used when you need the entire content in order to operate on it.
 *Please note that this uses more heap space as the full request is transiently stored in the heap.
 *
 */

public class AggregateRequestBodyExamplePlugin implements Plugin {
    private final Config config;

    public AggregateRequestBodyExamplePlugin(Config config) {
        this.config = requireNonNull(config);
    }

    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {

       return request.aggregate(10000)
                .map(fullRequest -> {
                    String body = fullRequest.bodyAs(UTF_8);
                    return fullRequest.newBuilder()
                            .body(body + config.extraText(), UTF_8)
                            .build().stream();
                }).flatMap(chain::proceed);
    }

    /**
     * Config for example plugin.
     */
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

