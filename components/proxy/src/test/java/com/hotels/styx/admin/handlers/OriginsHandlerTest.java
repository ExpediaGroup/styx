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
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.support.ApplicationConfigurationMatcher;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.client.applications.BackendServices;
import com.hotels.styx.infrastructure.MemoryBackedRegistry;
import com.hotels.styx.infrastructure.Registry;
import com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry;
import com.hotels.styx.server.HttpInterceptorContext;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.hotels.styx.support.api.HttpMessageBodies.bodyAsString;
import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.io.ResourceFactory.newResource;
import static com.hotels.styx.support.api.matchers.HttpResponseStatusMatcher.hasStatus;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static com.hotels.styx.applications.yaml.YamlApplicationsProvider.loadFromPath;
import static com.hotels.styx.support.ResourcePaths.fixturesHome;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.StreamSupport.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

public class OriginsHandlerTest {
    static final ObjectMapper MAPPER = new ObjectMapper().disable(FAIL_ON_UNKNOWN_PROPERTIES);
    static final String ORIGINS_FILE = fixturesHome() + "conf/origins/origins-for-jsontest.yml";

    final BackendServices backendServices = loadFromPath(ORIGINS_FILE).get();
    final FileBackedBackendServicesRegistry backendServicesRegistry = new FileBackedBackendServicesRegistry(newResource(ORIGINS_FILE));
    final OriginsHandler handler = new OriginsHandler(backendServicesRegistry);

    @BeforeClass
    public void startRegistry() {
        backendServicesRegistry.startAsync().awaitRunning();
    }

    @AfterClass
    public void stopRegistry() {
        backendServicesRegistry.stopAsync().awaitTerminated();
    }

    @Test
    public void respondsToRequestWithJsonResponse() throws IOException {
        HttpResponse response = handle(get("/admin/configuration/origins").build());

        assertThat(response, hasStatus(OK));
        assertThat(response.contentType(), isValue(JSON_UTF_8.toString()));

        String content = bodyAsString(response);
        assertThat(content, unmarshalApplications(content), containsInAnyOrder(expected()));
    }

    @Test
    public void respondsWithEmptyArrayWhenNoOrigins() throws IOException {
        Registry<BackendService> backendServicesRegistry = new MemoryBackedRegistry<>();
        OriginsHandler handler = new OriginsHandler(backendServicesRegistry);

        HttpResponse response = handle(handler, get("/admin/configuration/origins").build());

        assertThat(response, hasStatus(OK));
        assertThat(response.contentType(), isValue(JSON_UTF_8.toString()));

        String content = bodyAsString(response);
        assertThat(content, is("[]"));
    }

    private ApplicationConfigurationMatcher[] expected() {
        return matchersFor(backendServices);
    }

    private static ApplicationConfigurationMatcher[] matchersFor(BackendServices backendServices) {
        return stream(backendServices.spliterator(), false)
                .map(ApplicationConfigurationMatcher::matcherFor)
                .toArray(ApplicationConfigurationMatcher[]::new);
    }

    private static BackendServices unmarshalApplications(String content) throws IOException {
        return MAPPER.readValue(content, BackendServices.class);
    }

    private HttpResponse handle(HttpRequest request) {
        return handle(this.handler, request);
    }

    private static HttpResponse handle(OriginsHandler handler, HttpRequest request) {
        return handler.handle(request).flatMap(response ->
                response.decode(buffer -> buffer.toString(UTF_8), 0x10000))
                .map(decodedResponse -> decodedResponse.responseBuilder()
                        .body(decodedResponse.body())
                        .build())
                .toBlocking()
                .single();
    }
}