/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx.api;

import org.testng.annotations.Test;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.LiveHttpRequest.post;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class EarlyReturnTest {
    @Test
    public void bodyIsConsumedWhenReturningEarlyWithError() {
        AtomicBoolean consumed = new AtomicBoolean();

        ByteStream content = ByteStream.from("foo", UTF_8)
                .doOnEnd(oe -> consumed.set(true));

        LiveHttpRequest request = post("/")
                .body(content)
                .build();

        RuntimeException myException = new RuntimeException("This is just a test");

        Eventual<LiveHttpResponse> eventual = EarlyReturn.returnEarlyWithError(request, myException);

        try {
            Mono.from(eventual).block();
        } catch (Exception e) {
            assertThat(e, is(myException));
        }

        assertThat(consumed.get(), is(true));
    }

    @Test
    public void bodyIsConsumedWhenReturningEarlyWithLiveResponse() {
        AtomicBoolean consumed = new AtomicBoolean();

        ByteStream content = ByteStream.from("foo", UTF_8)
                .doOnEnd(oe -> consumed.set(true));

        LiveHttpRequest request = post("/")
                .body(content)
                .build();

        Eventual<LiveHttpResponse> eventual = EarlyReturn.returnEarlyWithResponse(request, LiveHttpResponse.response(OK)
                .body(ByteStream.from("Expected response content", UTF_8))
                .build());

        HttpResponse response = Mono.from(eventual.flatMap(resp ->
                resp.aggregate(1000)))
                .block();

        assertThat(response.bodyAs(UTF_8), is("Expected response content"));
        assertThat(consumed.get(), is(true));
    }

    @Test
    public void bodyIsConsumedWhenReturningEarlyWithNonLiveResponse() {
        AtomicBoolean consumed = new AtomicBoolean();

        ByteStream content = ByteStream.from("foo", UTF_8)
                .doOnEnd(oe -> consumed.set(true));

        LiveHttpRequest request = post("/")
                .body(content)
                .build();

        Eventual<LiveHttpResponse> eventual = EarlyReturn.returnEarlyWithResponse(request, HttpResponse.response(OK)
                .body("Expected response content", UTF_8)
                .build());

        HttpResponse response = Mono.from(eventual.flatMap(resp ->
                resp.aggregate(1000)))
                .block();

        assertThat(response.bodyAs(UTF_8), is("Expected response content"));
        assertThat(consumed.get(), is(true));
    }
}