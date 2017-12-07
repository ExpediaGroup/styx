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

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.messages.FullHttpResponse;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static com.hotels.styx.api.HttpRequest.Builder.put;
import static com.hotels.styx.proxy.plugin.NamedPlugin.namedPlugin;
import static com.hotels.styx.support.api.BlockingObservables.waitForResponse;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

public class PluginToggleHandlerTest {
    private NamedPlugin initiallyEnabled;
    private NamedPlugin initiallyDisabled;
    private PluginToggleHandler handler;

    @BeforeMethod
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
        HttpRequest request = put("/foo/off/enabled").body("true").build();

        FullHttpResponse<String> response = waitForResponse(handler.handle(request));

        assertThat(response.status(), is(OK));
        assertThat(response.body(), is("{\"message\":\"State of 'off' changed to 'enabled'\",\"plugin\":{\"name\":\"off\",\"state\":\"enabled\"}}\n"));
        assertThat(initiallyEnabled.enabled(), is(true));
        assertThat(initiallyDisabled.enabled(), is(true));
    }

    @Test
    public void disablesEnabledPlugin() {
        HttpRequest request = put("/foo/on/enabled").body("false").build();

        FullHttpResponse<String> response = waitForResponse(handler.handle(request));

        assertThat(response.status(), is(OK));
        assertThat(response.body(), is("{\"message\":\"State of 'on' changed to 'disabled'\",\"plugin\":{\"name\":\"on\",\"state\":\"disabled\"}}\n"));
        assertThat(initiallyEnabled.enabled(), is(false));
        assertThat(initiallyDisabled.enabled(), is(false));
    }

    @Test
    public void notifiesWhenPluginAlreadyDisabled() {
        HttpRequest request = put("/foo/off/enabled").body("false").build();

        FullHttpResponse<String> response = waitForResponse(handler.handle(request));

        assertThat(response.status(), is(OK));
        assertThat(response.body(), is("{\"message\":\"State of 'off' was already 'disabled'\",\"plugin\":{\"name\":\"off\",\"state\":\"disabled\"}}\n"));
        assertThat(initiallyEnabled.enabled(), is(true));
        assertThat(initiallyDisabled.enabled(), is(false));
    }

    @Test
    public void notifiesWhenPluginAlreadyEnabled() {
        HttpRequest request = put("/foo/on/enabled").body("true").build();

        FullHttpResponse<String> response = waitForResponse(handler.handle(request));

        assertThat(response.status(), is(OK));
        assertThat(response.body(), is("{\"message\":\"State of 'on' was already 'enabled'\",\"plugin\":{\"name\":\"on\",\"state\":\"enabled\"}}\n"));
        assertThat(initiallyEnabled.enabled(), is(true));
        assertThat(initiallyDisabled.enabled(), is(false));
    }

    @Test
    public void saysBadRequestWhenUrlIsInvalid() {
        HttpRequest request = put("/foo//enabled").body("true").build();

        FullHttpResponse<String> response = waitForResponse(handler.handle(request));

        assertThat(response.status(), is(BAD_REQUEST));
        assertThat(response.body(), is("Invalid URL\n"));
        assertThat(initiallyEnabled.enabled(), is(true));
        assertThat(initiallyDisabled.enabled(), is(false));
    }

    @Test
    public void saysBadRequestWhenNoStateSpecified() {
        HttpRequest request = put("/foo/on/enabled").build();

        FullHttpResponse<String> response = waitForResponse(handler.handle(request));

        assertThat(response.status(), is(BAD_REQUEST));
        assertThat(response.body(), is("No such state: only 'true' and 'false' are valid.\n"));
        assertThat(initiallyEnabled.enabled(), is(true));
        assertThat(initiallyDisabled.enabled(), is(false));
    }

    @Test
    public void saysBadRequestWhenPluginDoesNotExist() {
        HttpRequest request = put("/foo/nonexistent/enabled").body("true").build();

        FullHttpResponse<String> response = waitForResponse(handler.handle(request));

        assertThat(response.status(), is(NOT_FOUND));
        assertThat(response.body(), is("No such plugin\n"));
        assertThat(initiallyEnabled.enabled(), is(true));
        assertThat(initiallyDisabled.enabled(), is(false));
    }

    @Test
    public void saysBadRequestWhenValueIsInvalid() {
        HttpRequest request = put("/foo/off/enabled").body("invalid").build();

        FullHttpResponse<String> response = waitForResponse(handler.handle(request));

        assertThat(response.status(), is(BAD_REQUEST));
        assertThat(response.body(), is("No such state: only 'true' and 'false' are valid.\n"));
        assertThat(initiallyEnabled.enabled(), is(true));
        assertThat(initiallyDisabled.enabled(), is(false));
    }
}