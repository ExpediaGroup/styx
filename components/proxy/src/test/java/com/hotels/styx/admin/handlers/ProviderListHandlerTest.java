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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.hotels.styx.StyxObjectRecord;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.extension.service.spi.AbstractStyxService;
import com.hotels.styx.api.extension.service.spi.StyxService;
import com.hotels.styx.common.http.handler.HttpContentHandler;
import com.hotels.styx.routing.db.StyxObjectStore;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Map;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.hotels.styx.support.Support.requestContext;
import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.stringContainsInOrder;

public class ProviderListHandlerTest {

    @Test
    public void showsEndpointsForAllConfiguredProviders() throws JsonProcessingException {
        JsonNode config = new ObjectMapper().readTree("{\"setting1\" : \"A\", \"setting2\" : \"A\"}");

        StyxObjectStore<StyxObjectRecord<StyxService>> store = new StyxObjectStore<>();
        store.insert("Service-A1", new StyxObjectRecord<>("ServiceA", new HashSet<>(), config, new SampleServiceA("Service-A-1")));
        store.insert("Service-A2", new StyxObjectRecord<>("ServiceA", new HashSet<>(), config, new SampleServiceA("Service-A-2")));
        store.insert("Service-B", new StyxObjectRecord<>("ServiceB", new HashSet<>(), config, new SampleServiceB("Service-B")));

        ProviderListHandler handler = new ProviderListHandler(store);

        HttpResponse response = Mono.from(handler.handle(get("/").build(), requestContext())).block();
        assertThat(response.status(), equalTo(OK));
        assertThat(response.bodyAs(UTF_8), stringContainsInOrder(
                "Service-A1 (ServiceA)",
                "<a href=\"/admin/providers/Service-A1/status\">/admin/providers/Service-A1/status</a>",
                "Service-A2 (ServiceA)",
                "<a href=\"/admin/providers/Service-A2/status\">/admin/providers/Service-A2/status</a>",
                "Service-B (ServiceB)",
                "<a href=\"/admin/providers/Service-B/withslash/\">/admin/providers/Service-B/withslash/</a>",
                "<a href=\"/admin/providers/Service-B/noslash\">/admin/providers/Service-B/noslash</a>",
                "<a href=\"/admin/providers/Service-B/\">/admin/providers/Service-B/</a>"
        ));
    }

    static class SampleServiceA extends AbstractStyxService {

        public SampleServiceA(String name) {
            super(name);
        }
    }

    static class SampleServiceB extends AbstractStyxService {

        public SampleServiceB(String name) {
            super(name);
        }

        @Override
        public Map<String, HttpHandler> adminInterfaceHandlers(String namespace) {
            return ImmutableMap.of(
                    "withslash/", new HttpContentHandler(PLAIN_TEXT_UTF_8.toString(), UTF_8, () -> "with slash"),
                    "noslash", new HttpContentHandler(PLAIN_TEXT_UTF_8.toString(), UTF_8, () -> "no slash"),
                    "/", new HttpContentHandler(PLAIN_TEXT_UTF_8.toString(), UTF_8, () -> "just a slash"));
        }
    }
}
