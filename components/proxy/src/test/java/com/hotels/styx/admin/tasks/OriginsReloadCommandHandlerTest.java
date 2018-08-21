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
package com.hotels.styx.admin.tasks;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.Registry;
import com.hotels.styx.api.extension.service.spi.Registry.ReloadResult;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.CompletableFuture;

import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST;
import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.extension.service.spi.Registry.ReloadResult.reloaded;
import static com.hotels.styx.api.extension.service.spi.Registry.ReloadResult.unchanged;
import static com.hotels.styx.support.api.BlockingObservables.waitForResponse;
import static com.hotels.styx.support.matchers.RegExMatcher.matchesRegex;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        mockRegistryReload(completedFuture(reloaded("ok")));

        FullHttpResponse response = waitForResponse(handler.handle(get("/").build(), mock(HttpInterceptor.Context.class)));

        assertThat(response.status(), is(OK));
        assertThat(response.bodyAs(UTF_8), is("Origins reloaded successfully.\n"));
    }

    @Test
    public void returnsWithInformationWhenChangesAreUnnecessary() {
        mockRegistryReload(completedFuture(unchanged("this test returns 'no meaningful changes'")));

        FullHttpResponse response = waitForResponse(handler.handle(get("/").build(), mock(HttpInterceptor.Context.class)));

        assertThat(response.status(), is(OK));
        assertThat(response.bodyAs(UTF_8), is("Origins were not reloaded because this test returns 'no meaningful changes'.\n"));
    }

    @Test
    public void returnsWithInformationWhenJsonErrorOccursDuringReload() {
        mockRegistryReload(failedFuture(new RuntimeException(new JsonMappingException("simulated error"))));

        FullHttpResponse response = waitForResponse(handler.handle(get("/").build(), mock(HttpInterceptor.Context.class)));

        assertThat(response.status(), is(BAD_REQUEST));
        assertThat(response.bodyAs(UTF_8), is(matchesRegex("There was an error processing your request. It has been logged \\(ID [0-9a-f-]+\\)\\.\n")));
    }

    @Test
    public void returnsWithInformationWhenErrorDuringReload() {
        mockRegistryReload(failedFuture(new RuntimeException(new RuntimeException("simulated error"))));

        FullHttpResponse response = waitForResponse(handler.handle(get("/").build(), mock(HttpInterceptor.Context.class)));

        assertThat(response.status(), is(INTERNAL_SERVER_ERROR));
        assertThat(response.bodyAs(UTF_8), is(matchesRegex("There was an error processing your request. It has been logged \\(ID [0-9a-f-]+\\)\\.\n")));
    }

    private CompletableFuture<ReloadResult> failedFuture(RuntimeException simulated_error) {
        CompletableFuture<ReloadResult> future = new CompletableFuture<>();
        future.completeExceptionally(simulated_error);
        return future;
    }

    private void mockRegistryReload(CompletableFuture<ReloadResult> value) {
        when(registry.reload()).thenReturn(value);
    }
}