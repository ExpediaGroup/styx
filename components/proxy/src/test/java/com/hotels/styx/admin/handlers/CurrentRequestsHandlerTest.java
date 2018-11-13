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
package com.hotels.styx.admin.handlers;

import static com.hotels.styx.api.LiveHttpRequest.get;
import static java.nio.charset.StandardCharsets.UTF_8;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.hotels.styx.api.Buffer;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.server.track.CurrentRequestTracker;

import reactor.core.publisher.Flux;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class CurrentRequestsHandlerTest {

    LiveHttpRequest req1 = get("/requestId1").build();

    CurrentRequestTracker tracker = new CurrentRequestTracker();
    private CurrentRequestsHandler handler;

    @BeforeMethod
    public void setUp() {
        tracker = new CurrentRequestTracker();
        handler = new CurrentRequestsHandler(tracker);
    }

    @Test
    public void testStackTrace() {
        Thread.currentThread().setName("Test-Thread");
        tracker.trackRequest(req1);
        LiveHttpResponse response = handler.doHandle(req1);
        assertThat(Flux.from(response.body()).map(this::decodeUtf8String).blockFirst().contains("Test-Thread"), is(true));
    }

    @Test
    public void testStackTraceForSentRequest() {
        Thread.currentThread().setName("Test-Thread-1");
        tracker.trackRequest(req1);
        tracker.markRequestAsSent(req1);
        LiveHttpResponse response = handler.doHandle(req1);
        assertThat(Flux.from(response.body()).map(this::decodeUtf8String).blockFirst().contains("Request state: Waiting response from origin."), is(true));
    }

    @Test
    public void testWithStackTrace() {
        Thread.currentThread().setName("Test-Thread");
        tracker.trackRequest(req1);
        LiveHttpResponse response = handler.doHandle(get("/req?withStackTrace=true").build());
        assertThat(Flux.from(response.body()).map(this::decodeUtf8String).blockFirst().contains("id=" + Thread.currentThread().getId()), is(true));
    }

    @Test
    public void testWithoutStackTrace() {
        Thread.currentThread().setName("Test-Thread");
        tracker.trackRequest(req1);
        LiveHttpResponse response = handler.doHandle(req1);
        assertThat(Flux.from(response.body()).map(this::decodeUtf8String).blockFirst().contains("id=" + Thread.currentThread().getId()), is(false));
    }

    private String decodeUtf8String(Buffer buffer) {
        return new String(buffer.content(), UTF_8);
    }
}
