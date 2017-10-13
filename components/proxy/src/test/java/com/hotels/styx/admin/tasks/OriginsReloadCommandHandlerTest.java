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
package com.hotels.styx.admin.tasks;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.infrastructure.Registry;
import com.hotels.styx.infrastructure.Registry.ReloadListener;
import com.hotels.styx.server.HttpInterceptorContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.function.Consumer;

import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.support.api.matchers.HttpResponseBodyMatcher.hasBody;
import static com.hotels.styx.support.api.matchers.HttpResponseStatusMatcher.hasStatus;
import static com.hotels.styx.support.matchers.RegExMatcher.matchesRegex;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class OriginsReloadCommandHandlerTest {
    Registry<BackendService> registry;
    OriginsReloadCommandHandler handler;

    @BeforeMethod
    public void setUp() {
        registry = mock(Registry.class);
        handler = new OriginsReloadCommandHandler(registry);
    }

    @Test
    public void returnsWithConfirmationWhenChangesArePerformed() {
        mockRegistryReload(ReloadListener::onChangesApplied);

        HttpResponse response = makeReloadRequest();

        assertThat(response, hasStatus(OK));
        assertThat(response, hasBody("Origins reloaded successfully.\n"));
    }

    @Test
    public void returnsWithInformationWhenChangesAreUnnecessary() {
        mockRegistryReload(listener -> listener.onNoMeaningfulChanges("this test returns 'no meaningful changes'"));

        HttpResponse response = makeReloadRequest();

        assertThat(response, hasStatus(OK));
        assertThat(response, hasBody("Origins were not reloaded because this test returns 'no meaningful changes'.\n"));
    }

    @Test
    public void returnsWithInformationWhenJsonErrorOccursDuringReload() {
        mockRegistryReload(listener -> listener.onErrorDuringReload(new RuntimeException(new JsonMappingException("simulated error"))));

        HttpResponse response = makeReloadRequest();

        assertThat(response, hasStatus(BAD_REQUEST));
        assertThat(response, hasBody(matchesRegex("There was an error processing your request. It has been logged \\(ID [0-9a-f-]+\\)\\.\n")));
    }

    @Test
    public void returnsWithInformationWhenErrorDuringReload() {
        mockRegistryReload(listener -> listener.onErrorDuringReload(new RuntimeException("simulated error")));

        HttpResponse response = makeReloadRequest();

        assertThat(response, hasStatus(INTERNAL_SERVER_ERROR));
        assertThat(response, hasBody(matchesRegex("There was an error processing your request. It has been logged \\(ID [0-9a-f-]+\\)\\.\n")));
    }

    private HttpResponse makeReloadRequest() {
        return handler.handle(get("/").build()).toBlocking().first();
    }

    private void mockRegistryReload(Consumer<ReloadListener> onReload) {
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            onReload.accept((ReloadListener) args[0]);
            return null;
        }).when(registry).reload(any(ReloadListener.class));
    }
}