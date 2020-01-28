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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.Registry;
import com.hotels.styx.infrastructure.MemoryBackedRegistry;
import com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.IOException;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.hotels.styx.support.Support.requestContext;
import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.applications.BackendServices.newBackendServices;
import static com.hotels.styx.applications.yaml.YamlApplicationsProvider.loadFromPath;
import static com.hotels.styx.common.StyxFutures.await;
import static com.hotels.styx.infrastructure.configuration.json.ObjectMappers.addStyxMixins;
import static com.hotels.styx.support.ResourcePaths.fixturesHome;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class OriginsHandlerTest {
    private static final ObjectMapper MAPPER = addStyxMixins(new ObjectMapper().disable(FAIL_ON_UNKNOWN_PROPERTIES));

    @Test
    public void respondsToRequestWithJsonResponse() throws IOException {
        String originsFile = fixturesHome() + "conf/origins/origins-for-jsontest.yml";

        Iterable<BackendService> expected = loadFromPath(originsFile).get();

        withOriginsHandler(originsFile, handler -> {
            HttpResponse response = Mono.from(handler.handle(get("/admin/configuration/origins").build(), requestContext())).block();

            assertThat(response.status(), is(OK));
            assertThat(response.contentType(), isValue(JSON_UTF_8.toString()));

            Iterable<BackendService> result = newBackendServices(unmarshalApplications(response.bodyAs(UTF_8)));

            assertThat(result, is(expected));
        });
    }

    @Test
    public void respondsWithEmptyArrayWhenNoOrigins() {
        Registry<BackendService> backendServicesRegistry = new MemoryBackedRegistry<>();
        OriginsHandler handler = new OriginsHandler(backendServicesRegistry);

        HttpResponse response = Mono.from(handler.handle(get("/admin/configuration/origins").build(), requestContext())).block();

        assertThat(response.status(), is(OK));
        assertThat(response.contentType(), isValue(JSON_UTF_8.toString()));

        assertThat(response.bodyAs(UTF_8), is("[]"));
    }

    @Test
    public void healthCheckIsAbsentWhenNotConfigured() throws IOException {
        String originsFile = fixturesHome() + "conf/origins/origins-for-jsontest-no-healthcheck.yml";

        Iterable<BackendService> expected = loadFromPath(originsFile).get();

        withOriginsHandler(originsFile, handler -> {
            HttpResponse response = Mono.from(handler.handle(get("/admin/configuration/origins").build(), requestContext())).block();

            assertThat(response.status(), is(OK));
            assertThat(response.contentType(), isValue(JSON_UTF_8.toString()));

            String body = response.bodyAs(UTF_8);

            assertThat(body, not(containsString("healthCheck")));

            Iterable<BackendService> result = newBackendServices(unmarshalApplications(body));
            assertThat(result, is(expected));
        });
    }

    private static Iterable<BackendService> unmarshalApplications(String content) throws IOException {
        return MAPPER.readValue(content, new TypeReference<Iterable<BackendService>>() {
        });
    }

    private interface IoAction {
        void call(OriginsHandler handler) throws IOException;
    }

    private static void withOriginsHandler(String path, IoAction action) throws IOException {
        FileBackedBackendServicesRegistry backendServicesRegistry = FileBackedBackendServicesRegistry.create(path);
        await(backendServicesRegistry.start());

        try {
            OriginsHandler handler = new OriginsHandler(backendServicesRegistry);

            action.call(handler);
        } finally {
            await(backendServicesRegistry.stop());
        }
    }
}