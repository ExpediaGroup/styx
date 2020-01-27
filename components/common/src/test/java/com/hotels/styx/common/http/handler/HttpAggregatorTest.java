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
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.WebServiceHandler;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static com.hotels.styx.support.Support.requestContext;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class HttpAggregatorTest {
    @Test
    public void aggregatesRequestAndStreamsResponses() {
        AtomicReference<String> result = new AtomicReference<>();

        WebServiceHandler webServiceHandler = (request, ctx) -> {
            result.set(request.bodyAs(UTF_8));
            return Eventual.of(response(OK)
                    .body("abcdef", UTF_8)
                    .build());
        };

        LiveHttpResponse response = Mono.from(
                new HttpAggregator(500, webServiceHandler)
                        .handle(LiveHttpRequest.post("/")
                                        .body(ByteStream.from("ABCDEF", UTF_8))
                                        .build(),
                                requestContext()))
                .block();

        assertThat(result.get(), is("ABCDEF"));
        assertThat(Mono.from(response.aggregate(500)).block().bodyAs(UTF_8), is("abcdef"));
    }
}
