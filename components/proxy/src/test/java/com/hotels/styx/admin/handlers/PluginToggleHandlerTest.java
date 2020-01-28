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

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static com.hotels.styx.support.Support.requestContext;
import static com.hotels.styx.api.HttpRequest.put;
import static com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.proxy.plugin.NamedPlugin.namedPlugin;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

public class PluginToggleHandlerTest {
    private NamedPlugin initiallyEnabled;
    private NamedPlugin initiallyDisabled;
    private PluginToggleHandler handler;

    @BeforeEach
    public void setUp() {
        initiallyEnabled = namedPlugin("on", mock(Plugin.class));
        initiallyDisabled = namedPlugin("off", mock(Plugin.class));

        initiallyEnabled.setEnabled(true);
        initiallyDisabled.setEnabled(false);

        List<NamedPlugin> plugins = asList(initiallyEnabled, initiallyDisabled);

        handler = new PluginToggleHandler(plugins);
    }

    @Test
    public void enablesDisabledPlugin() {
        HttpRequest request = put("/foo/off/enabled").body("true", UTF_8).build();

        HttpResponse response = Mono.from(handler.handle(request, requestContext())).block();

        assertThat(response.status(), is(OK));
        assertThat(body(response), is("{\"message\":\"State of 'off' changed to 'enabled'\",\"plugin\":{\"name\":\"off\",\"state\":\"enabled\"}}"));
        assertThat(initiallyEnabled.enabled(), is(true));
        assertThat(initiallyDisabled.enabled(), is(true));
    }

    @Test
    public void disablesEnabledPlugin() {
        HttpRequest request = put("/foo/on/enabled").body("false", UTF_8).build();

        HttpResponse response = Mono.from(handler.handle(request, requestContext())).block();

        assertThat(response.status(), is(OK));
        assertThat(body(response), is("{\"message\":\"State of 'on' changed to 'disabled'\",\"plugin\":{\"name\":\"on\",\"state\":\"disabled\"}}"));
        assertThat(initiallyEnabled.enabled(), is(false));
        assertThat(initiallyDisabled.enabled(), is(false));
    }

    @Test
    public void notifiesWhenPluginAlreadyDisabled() {
        HttpRequest request = put("/foo/off/enabled").body("false", UTF_8).build();

        HttpResponse response = Mono.from(handler.handle(request, requestContext())).block();

        assertThat(response.status(), is(OK));
        assertThat(body(response), is("{\"message\":\"State of 'off' was already 'disabled'\",\"plugin\":{\"name\":\"off\",\"state\":\"disabled\"}}"));
        assertThat(initiallyEnabled.enabled(), is(true));
        assertThat(initiallyDisabled.enabled(), is(false));
    }

    @Test
    public void notifiesWhenPluginAlreadyEnabled() {
        HttpRequest request = put("/foo/on/enabled").body("true", UTF_8).build();

        HttpResponse response = Mono.from(handler.handle(request, requestContext())).block();

        assertThat(response.status(), is(OK));
        assertThat(body(response), is("{\"message\":\"State of 'on' was already 'enabled'\",\"plugin\":{\"name\":\"on\",\"state\":\"enabled\"}}"));
        assertThat(initiallyEnabled.enabled(), is(true));
        assertThat(initiallyDisabled.enabled(), is(false));
    }

    @Test
    public void saysBadRequestWhenUrlIsInvalid() {
        HttpRequest request = put("/foo//enabled").body("true", UTF_8).build();

        HttpResponse response = Mono.from(handler.handle(request, requestContext())).block();

        assertThat(response.status(), is(BAD_REQUEST));
        assertThat(body(response), is("Invalid URL"));
        assertThat(initiallyEnabled.enabled(), is(true));
        assertThat(initiallyDisabled.enabled(), is(false));
    }

    @Test
    public void saysBadRequestWhenNoStateSpecified() {
        HttpRequest request = put("/foo/on/enabled").build();

        HttpResponse response = Mono.from(handler.handle(request, requestContext())).block();

        assertThat(response.status(), is(BAD_REQUEST));
        assertThat(body(response), is("No such state: only 'true' and 'false' are valid."));
        assertThat(initiallyEnabled.enabled(), is(true));
        assertThat(initiallyDisabled.enabled(), is(false));
    }

    @Test
    public void saysBadRequestWhenPluginDoesNotExist() {
        HttpRequest request = put("/foo/nonexistent/enabled").body("true", UTF_8).build();

        HttpResponse response = Mono.from(handler.handle(request, requestContext())).block();

        assertThat(response.status(), is(NOT_FOUND));
        assertThat(body(response), is("No such plugin: pluginName=nonexistent"));
        assertThat(initiallyEnabled.enabled(), is(true));
        assertThat(initiallyDisabled.enabled(), is(false));
    }

    @Test
    public void saysBadRequestWhenValueIsInvalid() {
        HttpRequest request = put("/foo/off/enabled").body("invalid", UTF_8).build();

        HttpResponse response = Mono.from(handler.handle(request, requestContext())).block();

        assertThat(response.status(), is(BAD_REQUEST));
        assertThat(body(response), is("No such state: only 'true' and 'false' are valid."));
        assertThat(initiallyEnabled.enabled(), is(true));
        assertThat(initiallyDisabled.enabled(), is(false));
    }

    private static String body(HttpResponse response) {
        return response.bodyAs(UTF_8).trim();
    }
}