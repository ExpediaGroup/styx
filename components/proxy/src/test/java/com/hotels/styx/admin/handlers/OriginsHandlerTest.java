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
import com.hotels.styx.api.messages.FullHttpResponse;
import com.hotels.styx.api.service.BackendService;
import com.hotels.styx.api.service.spi.Registry;
import com.hotels.styx.infrastructure.MemoryBackedRegistry;
import com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.messages.HttpResponseStatus.OK;
import static com.hotels.styx.applications.yaml.YamlApplicationsProvider.loadFromPath;
import static com.hotels.styx.client.applications.BackendServices.newBackendServices;
import static com.hotels.styx.common.StyxFutures.await;
import static com.hotels.styx.support.ResourcePaths.fixturesHome;
import static com.hotels.styx.support.api.BlockingObservables.waitForResponse;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class OriginsHandlerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper().disable(FAIL_ON_UNKNOWN_PROPERTIES);

    private static final String ORIGINS_FILE = fixturesHome() + "conf/origins/origins-for-jsontest.yml";
    private static final String ORIGINS_FILE_NO_HEALTH_CHECK = fixturesHome() + "conf/origins/origins-for-jsontest-no-healthcheck.yml";

    private final FileBackedBackendServicesRegistry backendServicesRegistry = FileBackedBackendServicesRegistry.create(ORIGINS_FILE);
    private final FileBackedBackendServicesRegistry backendServicesRegistryNoHealthCheck = FileBackedBackendServicesRegistry.create(ORIGINS_FILE_NO_HEALTH_CHECK);

    private final OriginsHandler handler = new OriginsHandler(backendServicesRegistry);
    private final OriginsHandler handlerNoHealthCheck = new OriginsHandler(backendServicesRegistryNoHealthCheck);

    @BeforeClass
    public void startRegistry() {
        await(backendServicesRegistry.start());
    }

    @BeforeClass
    public void startRegistryNHC() {
        await(backendServicesRegistryNoHealthCheck.start());
    }

    @AfterClass
    public void stopRegistry() {
        await(backendServicesRegistry.stop());
    }

    @AfterClass
    public void stopRegistryNHC() {
        await(backendServicesRegistryNoHealthCheck.stop());
    }

    @Test
    public void respondsToRequestWithJsonResponse() throws IOException {
        FullHttpResponse response = waitForResponse(handler.handle(get("/admin/configuration/origins").build()));

        assertThat(response.status(), is(OK));
        assertThat(response.contentType(), isValue(JSON_UTF_8.toString()));

        Iterable<BackendService> result = newBackendServices(unmarshalApplications(response.bodyAs(UTF_8)));

        Iterable<BackendService> backendServices = loadFromPath(ORIGINS_FILE).get();

        assertThat(result, is(backendServices));
    }

    @Test
    public void respondsWithEmptyArrayWhenNoOrigins() {
        Registry<BackendService> backendServicesRegistry = new MemoryBackedRegistry<>();
        OriginsHandler handler = new OriginsHandler(backendServicesRegistry);

        FullHttpResponse response = waitForResponse(handler.handle(get("/admin/configuration/origins").build()));

        assertThat(response.status(), is(OK));
        assertThat(response.contentType(), isValue(JSON_UTF_8.toString()));

        assertThat(response.bodyAs(UTF_8), is("[]"));
    }

    @Test
    public void healthCheckIsAbsentWhenNotConfigured() throws IOException {
        FullHttpResponse response = waitForResponse(handlerNoHealthCheck.handle(get("/admin/configuration/origins").build()));

        assertThat(response.status(), is(OK));
        assertThat(response.contentType(), isValue(JSON_UTF_8.toString()));

        String body = response.bodyAs(UTF_8);

        System.out.println("BODY = " + body);

        Iterable<BackendService> result = newBackendServices(unmarshalApplications(body));

        Iterable<BackendService> backendServices = loadFromPath(ORIGINS_FILE_NO_HEALTH_CHECK).get();

        assertThat(result, is(backendServices));
        assertThat(body, not(containsString("healthCheck")));
    }

    private static Iterable<BackendService> unmarshalApplications(String content) throws IOException {
        return MAPPER.readValue(content, new TypeReference<Iterable<BackendService>>(){});
    }
}