/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx.common.http.handler;

import com.hotels.styx.api.ByteStream;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.LiveHttpResponse;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static com.hotels.styx.support.Support.requestContext;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class HttpStreamerTest {
    @Test
    public void streamsRequestAndAggregatesResponses() {
        HttpHandler httpHandler = (request, ctx) -> Eventual.of(
                LiveHttpResponse.response(OK)
                        .body(ByteStream.from("abcdef", UTF_8))
                        .build());

        HttpResponse response = Mono.from(
                new HttpStreamer(500, httpHandler)
                        .handle(HttpRequest.post("/")
                                        .body("ABCDEF", UTF_8)
                                        .build(),
                                requestContext()))
                .block();

        assertThat(response.bodyAs(UTF_8), is("abcdef"));
    }
}