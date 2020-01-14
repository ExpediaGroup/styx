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

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.WebServiceHandler;
import com.hotels.styx.proxy.plugin.NamedPlugin;

import java.util.List;
import java.util.stream.Stream;

import static com.google.common.net.MediaType.HTML_UTF_8;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

/**
 * Returns a simple HTML page with a list of plugins, split into enabled and disabled.
 */
public class PluginListHandler implements WebServiceHandler {
    private final List<NamedPlugin> plugins;

    public PluginListHandler(List<NamedPlugin> plugins) {
        this.plugins = requireNonNull(plugins);
    }

    @Override
    public Eventual<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        Stream<NamedPlugin> enabled = plugins.stream().filter(NamedPlugin::enabled);
        Stream<NamedPlugin> disabled = plugins.stream().filter(plugin -> !plugin.enabled());

        boolean needsIncludeDisabledPlugins = existDisabledPlugins();
        String output = section(needsIncludeDisabledPlugins ? "Enabled" : "Loaded", enabled)
                + (needsIncludeDisabledPlugins ? section("Disabled", disabled) : "");

        return Eventual.of(response(OK)
                .body(output, UTF_8)
                .addHeader(CONTENT_TYPE, HTML_UTF_8.toString())
                .build());
    }

    private static String section(String toggleState, Stream<NamedPlugin> plugins) {
        return format("<h3>%s</h3>", toggleState)
                + plugins.map(NamedPlugin::name)
                .map(PluginListHandler::pluginLink)
                .collect(joining());
    }

    private static String pluginLink(String name) {
        return format("<a href='/admin/plugins/%s'>%s</a><br />", name, name);
    }

    private boolean existDisabledPlugins() {
        return plugins.stream().anyMatch(it -> !it.enabled());
    }
}
