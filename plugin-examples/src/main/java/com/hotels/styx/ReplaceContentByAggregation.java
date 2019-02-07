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
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.plugins.spi.Plugin;

import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;


public class ReplaceContentByAggregationExample implements Plugin {


    /**
     * You can replace content by aggregation
     * For example the message content contains a JSON object and you need to modify the object somehow.
     * You can aggregate the live HTTP message into a full HTTP message. Transform the content into a full message context
     * and convert the results back to live HTTP message
     */

    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {

        LiveHttpRequest newRequest = request.newBuilder()
                .header("myRequestHeader", config.requestHeaderValue())
                .build();

        return chain.proceed(request)
                .flatMap(response -> it.aggregate(10000))
                .map(response -> {
                    String body = response.bodyAs(UTF_8);
                    return response.newBuilder()
                            .body(modify(body), UTF_8)
                            .build();
                })
                .map(HttpResponse::stream);
    }
}

/**
 * This can be used when you need to do something with full content and when you need to replace content after looking
 * into it
 * You need to do something with full content. This uses more heap as the full response it transiently stored in heap
 */

