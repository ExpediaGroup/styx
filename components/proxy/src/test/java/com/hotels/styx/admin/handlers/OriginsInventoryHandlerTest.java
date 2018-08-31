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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.eventbus.EventBus;
import com.hotels.styx.admin.tasks.StubConnectionPool;
import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.OriginsSnapshot;
import com.hotels.styx.api.extension.RemoteHost;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancingMetricSupplier;
import com.hotels.styx.server.HttpInterceptorContext;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.extension.RemoteHost.remoteHost;
import static com.hotels.styx.infrastructure.configuration.json.ObjectMappers.addStyxMixins;
import static com.hotels.styx.support.api.BlockingObservables.waitForResponse;
import static com.hotels.styx.support.matchers.RegExMatcher.matchesRegex;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.IntStream.range;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

public class OriginsInventoryHandlerTest {
    private static final Id APP_ID = id("foo");

    @Test
    public void respondsWithCorrectSnapshot() throws IOException {
        EventBus eventBus = new EventBus();
        OriginsInventoryHandler handler = new OriginsInventoryHandler(eventBus);

        Set<Origin> activeOrigins = generateOrigins(3);
        Set<Origin> inactiveOrigins = generateOrigins(4);
        Set<Origin> disabledOrigins = generateOrigins(2);

        eventBus.post(new OriginsSnapshot(APP_ID, pool(activeOrigins), pool(inactiveOrigins), pool(disabledOrigins)));

        FullHttpResponse response = waitForResponse(handler.handle(get("/").build(), HttpInterceptorContext.create()));
        assertThat(response.bodyAs(UTF_8).split("\n").length, is(1));

        Map<Id, OriginsSnapshot> output = deserialiseJson(response.bodyAs(UTF_8));

        assertThat(output.keySet(), contains(APP_ID));

        OriginsSnapshot snapshot = output.get(APP_ID);

        assertThat(snapshot.appId(), is(APP_ID));
        assertThat(snapshot.activeOrigins(), is(activeOrigins));
        assertThat(snapshot.inactiveOrigins(), is(inactiveOrigins));
        assertThat(snapshot.disabledOrigins(), is(disabledOrigins));
    }

    @Test
    public void prettyPrintsOriginsSnapshot() {
        EventBus eventBus = new EventBus();
        OriginsInventoryHandler handler = new OriginsInventoryHandler(eventBus);

        Set<Origin> disabledOrigins = generateOrigins(2);

        eventBus.post(new OriginsSnapshot(APP_ID, pool(emptySet()), pool(emptySet()), pool(disabledOrigins)));

        FullHttpResponse response = waitForResponse(handler.handle(get("/?pretty=1").build(), HttpInterceptorContext.create()));
        assertThat(body(response).replace("\r\n", "\n"),
                matchesRegex("\\{\n" +
                        "  \"" + APP_ID + "\" : \\{\n" +
                        "    \"appId\" : \"" + APP_ID + "\",\n" +
                        "    \"activeOrigins\" : \\[ ],\n" +
                        "    \"inactiveOrigins\" : \\[ ],\n" +
                        "    \"disabledOrigins\" : \\[ \\{\n" +
                        "      \"id\" : \"origin.\",\n" +
                        "      \"host\" : \"localhost:....\"\n" +
                        "    }, \\{\n" +
                        "      \"id\" : \"origin.\",\n" +
                        "      \"host\" : \"localhost:....\"\n" +
                        "    } ]\n" +
                        "  }\n" +
                        "}"));
    }

    @Test
    public void returnsEmptyObjectWhenNoOrigins() {
        OriginsInventoryHandler handler = new OriginsInventoryHandler(new EventBus());

        FullHttpResponse response = waitForResponse(handler.handle(get("/").build(), HttpInterceptorContext.create()));

        assertThat(response.bodyAs(UTF_8), is("{}"));
    }

    private static Map<Id, OriginsSnapshot> deserialiseJson(String json) throws IOException {
        ObjectMapper mapper = addStyxMixins(new ObjectMapper());
        return mapper.readValue(json, new TypeReference<HashMap<Id, OriginsSnapshot>>() {
        });
    }

    private static Set<Origin> generateOrigins(int numberOfOrigins) {
        return range(0, numberOfOrigins)
                .mapToObj(id -> newOriginBuilder("localhost", 8080 + id)
                        .applicationId(APP_ID)
                        .id("origin" + id)
                        .build())
                .collect(toSet());
    }

    private static List<RemoteHost> pool(Set<Origin> origins) {
        return origins.stream()
                .map(StubConnectionPool::new)
                .map(pool -> remoteHost(pool.getOrigin(), mock(HttpHandler.class), mock(LoadBalancingMetricSupplier.class)))
                .collect(toList());
    }

    private String body(FullHttpResponse response){
        return response.bodyAs(Charsets.UTF_8).replace("\r\n", "\n");
    }
}