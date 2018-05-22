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

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.configstore.ConfigStore;
import com.hotels.styx.support.api.HttpMessageBodies;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.common.Result.failure;
import static com.hotels.styx.common.Result.success;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ProxyStatusHandlerTest {
    private HttpRequest request;
    private ProxyStatusHandler handler;
    private ConfigStore configStore;

    @BeforeMethod
    public void setUp() {
        request = get("/").build();
        configStore = new ConfigStore();
        handler = new ProxyStatusHandler(configStore);
    }

    @Test
    public void initiallyResponseIsIncomplete() {
        String response = handler.handle(request)
                .map(HttpMessageBodies::bodyAsString)
                .toBlocking()
                .single();

        assertThat(response, is(""
                + "{\n"
                + "  \"status\":\"INCOMPLETE\"\n"
                + "}"
                + "\n"));
    }

    @Test
    public void afterConfigIsUpdatedSuccessfullyResponseIsStarted() {
        configStore.set("server.started.proxy", success());

        String response = handler.handle(request)
                .map(HttpMessageBodies::bodyAsString)
                .toBlocking()
                .single();

        assertThat(response, is(""
                + "{\n"
                + "  \"status\":\"STARTED\"\n"
                + "}"
                + "\n"));
    }

    @Test
    public void afterConfigIsUpdatedUnsuccessfullyResponseIsFailed() {
        configStore.set("server.started.proxy", failure());

        String response = handler.handle(request)
                .map(HttpMessageBodies::bodyAsString)
                .toBlocking()
                .single();

        assertThat(response, is(""
                + "{\n"
                + "  \"status\":\"FAILED\"\n"
                + "}"
                + "\n"));
    }
}