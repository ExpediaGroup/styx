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
package com.hotels.styx.admin.handlers;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.server.track.CurrentRequestTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static com.hotels.styx.support.Support.requestContext;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CurrentRequestsHandlerTest {

    private LiveHttpRequest req1 = LiveHttpRequest.get("/requestId1").build();

    private CurrentRequestTracker tracker = new CurrentRequestTracker();
    private CurrentRequestsHandler handler;
    private HttpRequest adminRequest = HttpRequest.get("/admin/x").build();

    @BeforeEach
    public void setUp() {
        tracker = new CurrentRequestTracker();
        handler = new CurrentRequestsHandler(tracker);
    }

    @Test
    public void testStackTrace() {
        Thread.currentThread().setName("Test-Thread");
        tracker.trackRequest(req1);
        HttpResponse response = Mono.from(handler.handle(adminRequest, requestContext())).block();
        assertThat(response.bodyAs(UTF_8).contains("Test-Thread"), is(true));
    }

    @Test
    public void testStackTraceForSentRequest() {
        Thread.currentThread().setName("Test-Thread-1");
        tracker.trackRequest(req1);
        tracker.markRequestAsSent(req1);
        HttpResponse response = Mono.from(handler.handle(adminRequest, requestContext())).block();
        assertThat(response.bodyAs(UTF_8).contains("Request state: Waiting response from origin."), is(true));
    }

    @Test
    public void testWithStackTrace() {
        Thread.currentThread().setName("Test-Thread");
        tracker.trackRequest(req1);
        HttpResponse response = Mono.from(handler.handle(HttpRequest.get("/req?withStackTrace=true").build(), requestContext())).block();

        assertTrue(response.bodyAs(UTF_8).contains("Thread Info:"));
        assertTrue(response.bodyAs(UTF_8).contains("id=" + Thread.currentThread().getId() + " state"));
    }

    @Test
    public void testWithoutStackTrace() {
        tracker.trackRequest(req1);
        HttpResponse response = Mono.from(handler.handle(adminRequest, requestContext())).block();

        String body = response.bodyAs(UTF_8);

        assertFalse(body.contains(format("id=%d state", Thread.currentThread().getId())),
                format("This is received response body: ```%s```", body));
    }
}
