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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.extension.service.spi.StyxService;
import com.hotels.styx.routing.config.StyxObjectDefinition;
import com.hotels.styx.routing.db.StyxObjectStore;
import com.hotels.styx.StyxObjectRecord;
import org.apache.commons.lang.StringUtils;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.hotels.styx.support.Support.requestContext;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static com.hotels.styx.api.HttpResponseStatus.NO_CONTENT;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class ServiceProviderHandlerTest {

    @Test
    public void returnsAllProviders() throws IOException {
        StyxObjectStore<StyxObjectRecord<StyxService>> store = createTestStore();
        ServiceProviderHandler handler = new ServiceProviderHandler(store);
        HttpRequest request = HttpRequest.get("/admin/service/providers").build();
        HttpResponse response = Mono.from(handler.handle(request, requestContext())).block();

        assertThat(response.status(), equalTo(OK));

        List<StyxObjectDefinition> actualProviders = extractProviders(response.bodyAs(UTF_8));
        assertThat(actualProviders.size(), equalTo(store.entrySet().size()));
        for (StyxObjectDefinition actual :
                actualProviders) {
            Optional<StyxObjectRecord<StyxService>> rec = store.get(actual.name());
            assertTrue(rec.isPresent());
            validateProvider(actual, rec.get());
        }
    }

    @Test
    public void returnsNoContentStatusWhenNoProvidersAvailable() {
        StyxObjectStore<StyxObjectRecord<StyxService>> empty = new StyxObjectStore<>();
        ServiceProviderHandler handler = new ServiceProviderHandler(empty);
        HttpRequest request = HttpRequest.get("/admin/service/providers").build();
        HttpResponse response = Mono.from(handler.handle(request, requestContext())).block();

        assertThat(response.status(), equalTo(NO_CONTENT));
        assertFalse(response.contentLength().isPresent());
    }

    @Test
    public void returnsNamedProvider() throws IOException {
        StyxObjectStore<StyxObjectRecord<StyxService>> store = createTestStore();
        ServiceProviderHandler handler = new ServiceProviderHandler(store);
        HttpRequest request = HttpRequest.get("/admin/service/provider/object2").build();
        HttpResponse response = Mono.from(handler.handle(request, requestContext())).block();

        assertThat(response.status(), equalTo(OK));

        StyxObjectDefinition actualProvider = deserialiseProvider(response.bodyAs(UTF_8));
        assertThat(actualProvider, notNullValue());
        assertThat(actualProvider.name(), equalTo("object2"));
        validateProvider(actualProvider, store.get("object2").get());
    }

    @Test
    public void returnsNotFoundStatusWhenNamedProviderNotFound() throws IOException {
        StyxObjectStore<StyxObjectRecord<StyxService>> store = createTestStore();
        ServiceProviderHandler handler = new ServiceProviderHandler(store);
        HttpRequest request = HttpRequest.get("/admin/service/provider/nonexistent").build();
        HttpResponse response = Mono.from(handler.handle(request, requestContext())).block();

        assertThat(response.status(), equalTo(NOT_FOUND));
    }

    @Test
    public void returnsNotFoundStatusWithNonHandledUrl() {
        StyxObjectStore<StyxObjectRecord<StyxService>> empty = new StyxObjectStore<>();
        ServiceProviderHandler handler = new ServiceProviderHandler(empty);
        HttpRequest request = HttpRequest.get("/not/my/url").build();
        HttpResponse response = Mono.from(handler.handle(request, requestContext())).block();

        assertThat(response.status(), equalTo(NOT_FOUND));
    }

    private StyxObjectStore<StyxObjectRecord<StyxService>> createTestStore() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        StyxService mockService = mock(StyxService.class);
        StyxObjectStore<StyxObjectRecord<StyxService>> store = new StyxObjectStore<>();

        JsonNode config1 = mapper.readTree("{\"setting1\" : \"A\", \"setting2\" : \"A\"}");
        StyxObjectRecord<StyxService> rec1 = new StyxObjectRecord<>("Type1", new HashSet<>(Arrays.asList("this=that", "truth=false")),
                config1, mockService);
        store.insert("object1", rec1);

        JsonNode config2 = mapper.readTree("{\"setting1\" : \"B\", \"setting2\" : \"B\"}");
        StyxObjectRecord<StyxService> rec2 = new StyxObjectRecord<>("Type2", new HashSet<>(Arrays.asList("up=down", "weakness=strength")),
                config2, mockService);
        store.insert("object2", rec2);

        JsonNode config3 = mapper.readTree("{\"setting1\" : \"C\", \"setting2\" : \"C\"}");
        StyxObjectRecord<StyxService> rec3 = new StyxObjectRecord<>("Type3", new HashSet<>(Arrays.asList("black=white", "left=right")),
                config3, mockService);
        store.insert("object3", rec3);

        return store;
    }

    private StyxObjectDefinition deserialiseProvider(String yaml) {
        try {
            return ServiceProviderHandler.yamlMapper().readValue(yaml, StyxObjectDefinition.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<StyxObjectDefinition> extractProviders(String responseBody) throws IOException {
        return Arrays.stream(responseBody.split("---\n"))
                .filter(StringUtils::isNotBlank)
                .map(this::deserialiseProvider)
                .collect(Collectors.toList());
    }

    private void validateProvider(StyxObjectDefinition actual, StyxObjectRecord<StyxService> expected) {
        assertThat(actual.type(), equalTo(expected.getType()));
        assertThat(actual.tags(), containsInAnyOrder(expected.getTags().toArray()));

        JsonNode actualConfig = actual.config();
        JsonNode expectedConfig = expected.getConfig();
        assertThat(actualConfig.size(), equalTo(expectedConfig.size()));

        Iterator<String> iter = expectedConfig.fieldNames();
        while (iter.hasNext()) {
            String field = iter.next();
            assertThat(actualConfig.get(field).asText(), equalTo(expectedConfig.get(field).asText()));
        }
    }

}
