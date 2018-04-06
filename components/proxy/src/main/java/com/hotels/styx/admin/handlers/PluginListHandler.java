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

import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import rx.Observable;

import java.util.List;
import java.util.stream.Stream;

import static com.google.common.net.MediaType.HTML_UTF_8;
import static com.hotels.styx.api.HttpResponse.Builder.response;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static rx.Observable.just;

/**
 * Returns a simple HTML page with a list of plugins, split into enabled and disabled.
 */
public class PluginListHandler implements HttpHandler {
    private final List<NamedPlugin> plugins;

    public PluginListHandler(Iterable<NamedPlugin> plugins) {
        this.plugins = stream(plugins.spliterator(), false).collect(toList());
    }

    @Override
    public Observable<HttpResponse> handle(HttpRequest request) {
        Stream<NamedPlugin> enabled = plugins.stream().filter(NamedPlugin::enabled);
        Stream<NamedPlugin> disabled = plugins.stream().filter(plugin -> !plugin.enabled());

        String output = section("Enabled", enabled)
                + section("Disabled", disabled);

        return just(response(OK)
                .body(output)
                .contentType(HTML_UTF_8)
                .build());
    }

    private String section(String toggleState, Stream<NamedPlugin> plugins) {
        return format("<h3>%s</h3>", toggleState)
                + plugins.map(NamedPlugin::name)
                .map(this::pluginLink)
                .collect(joining());
    }

    private String pluginLink(String name) {
        return format("<a href='/admin/plugins/%s'>%s</a><br />", name, name);
    }
}
