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

/**
 * You can replace without aggregating it first, if the replacement does not depend on the original contents.
 * For example, if you need to replace a message body based on information in the message headers , regardless of
 * the original message body. (E.g you need to add a HTTP message body based on a HTTP error code)
 *
 * You can transform a live HTTP message body using the `replaceWith` Bytestream operator such as shown in the example below.
 *
 * This can be used to replace a message body without having to look into it, which will also save heap space as the
 *  live upstream response body is never stored in the heap in full.
 */

public class ReplaceLiveContentExample implements Plugin {

    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {

        LiveHttpRequest newRequest = request.newBuilder()
                .header("myRequestHeader", config.requestHeaderValue())
                .build();

        return chain.proceed(request)
                .map(response -> response.newBuilder()
                        .body(body -> body.replaceWith(ByteStream.from("replacement", UTF_8)))
                        .build());
    }
}

