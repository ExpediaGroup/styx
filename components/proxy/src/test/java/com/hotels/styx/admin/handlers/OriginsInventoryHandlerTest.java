/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.admin.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.google.common.eventbus.EventBus;
import com.hotels.styx.admin.tasks.StubConnectionPool;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.client.OriginsInventorySnapshot;
import com.hotels.styx.api.messages.FullHttpResponse;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static com.hotels.styx.support.api.BlockingObservables.waitForResponse;
import static com.hotels.styx.support.matchers.RegExMatcher.matchesRegex;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.IntStream.range;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

public class OriginsInventoryHandlerTest {
    private static final Id APP_ID = id("foo");

    @Test
    public void respondsWithCorrectSnapshot() throws IOException {
        EventBus eventBus = new EventBus();
        OriginsInventoryHandler handler = new OriginsInventoryHandler(eventBus);

        Set<Origin> activeOrigins = generateOrigins(3);
        Set<Origin> inactiveOrigins = generateOrigins(4);
        Set<Origin> disabledOrigins = generateOrigins(2);

        eventBus.post(new OriginsInventorySnapshot(APP_ID, pool(activeOrigins), pool(inactiveOrigins), pool(disabledOrigins)));

        FullHttpResponse<String> response = waitForResponse(handler.handle(get("/").build()));
        assertThat(response.body().split("\n").length, is(1));

        Map<Id, OriginsInventorySnapshot> output = deserialiseJson(response.body());

        assertThat(output.keySet(), contains(APP_ID));

        OriginsInventorySnapshot snapshot = output.get(APP_ID);

        assertThat(snapshot.appId(), is(APP_ID));
        assertThat(snapshot.activeOrigins(), is(activeOrigins));
        assertThat(snapshot.inactiveOrigins(), is(inactiveOrigins));
        assertThat(snapshot.disabledOrigins(), is(disabledOrigins));
    }

    @Test
    public void prettyPrintsOriginsSnapshot() throws Exception {
        EventBus eventBus = new EventBus();
        OriginsInventoryHandler handler = new OriginsInventoryHandler(eventBus);

        Set<Origin> disabledOrigins = generateOrigins(2);

        eventBus.post(new OriginsInventorySnapshot(APP_ID, pool(emptySet()), pool(emptySet()), pool(disabledOrigins)));

        FullHttpResponse<String> response = waitForResponse(handler.handle(get("/?pretty=1").build()));
        assertThat(response.body(), matchesRegex("\\{\n" +
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

        FullHttpResponse<String> response = waitForResponse(handler.handle(get("/").build()));

        assertThat(response.body(), is("{}"));
    }

    private static Map<Id, OriginsInventorySnapshot> deserialiseJson(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        MapType type = mapper.getTypeFactory().constructMapType(Map.class, Id.class, OriginsInventorySnapshot.class);
        return mapper.readValue(json, type);
    }

    private static Set<Origin> generateOrigins(int numberOfOrigins) {
        return range(0, numberOfOrigins)
                .mapToObj(id -> newOriginBuilder("localhost", 8080 + id)
                        .applicationId(APP_ID)
                        .id("origin" + id)
                        .build())
                .collect(toSet());
    }

    private static Collection<ConnectionPool> pool(Set<Origin> origins) {
        return origins.stream()
                .map(StubConnectionPool::new)
                .collect(toList());
    }
}