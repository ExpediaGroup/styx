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
import com.hotels.styx.api.messages.FullHttpResponse;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.client.applications.BackendServices;
import com.hotels.styx.infrastructure.MemoryBackedRegistry;
import com.hotels.styx.infrastructure.Registry;
import com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry;
import com.hotels.styx.support.ApplicationConfigurationMatcher;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.io.ResourceFactory.newResource;
import static com.hotels.styx.applications.yaml.YamlApplicationsProvider.loadFromPath;
import static com.hotels.styx.common.StyxFutures.await;
import static com.hotels.styx.support.ResourcePaths.fixturesHome;
import static com.hotels.styx.support.api.BlockingObservables.waitForResponse;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.util.stream.StreamSupport.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static java.nio.charset.StandardCharsets.UTF_8;

public class OriginsHandlerTest {
    static final ObjectMapper MAPPER = new ObjectMapper().disable(FAIL_ON_UNKNOWN_PROPERTIES);
    static final String ORIGINS_FILE = fixturesHome() + "conf/origins/origins-for-jsontest.yml";

    final BackendServices backendServices = loadFromPath(ORIGINS_FILE).get();
    final FileBackedBackendServicesRegistry backendServicesRegistry = new FileBackedBackendServicesRegistry(newResource(ORIGINS_FILE));
    final OriginsHandler handler = new OriginsHandler(backendServicesRegistry);

    @BeforeClass
    public void startRegistry() {
        await(backendServicesRegistry.start());
    }

    @AfterClass
    public void stopRegistry() {
        await(backendServicesRegistry.stop());
    }

    @Test
    public void respondsToRequestWithJsonResponse() throws IOException {
        FullHttpResponse response = waitForResponse(handler.handle(get("/admin/configuration/origins").build()));

        assertThat(response.status(), is(OK));
        assertThat(response.contentType(), isValue(JSON_UTF_8.toString()));

        assertThat(response.bodyAs(UTF_8), unmarshalApplications(response.bodyAs(UTF_8)), containsInAnyOrder(expected()));
    }

    @Test
    public void respondsWithEmptyArrayWhenNoOrigins() throws IOException {
        Registry<BackendService> backendServicesRegistry = new MemoryBackedRegistry<>();
        OriginsHandler handler = new OriginsHandler(backendServicesRegistry);

        FullHttpResponse response = waitForResponse(handler.handle(get("/admin/configuration/origins").build()));

        assertThat(response.status(), is(OK));
        assertThat(response.contentType(), isValue(JSON_UTF_8.toString()));

        assertThat(response.bodyAs(UTF_8), is("[]"));
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
}