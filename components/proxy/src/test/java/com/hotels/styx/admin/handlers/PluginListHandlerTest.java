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

import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static com.hotels.styx.support.Support.requestContext;
import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.plugins.spi.Plugin.PASS_THROUGH;
import static com.hotels.styx.proxy.plugin.NamedPlugin.namedPlugin;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class PluginListHandlerTest {
    @Test
    public void showsEnabledAndDisabledPlugins() {
        NamedPlugin one = namedPlugin("one", PASS_THROUGH);
        NamedPlugin two = namedPlugin("two", PASS_THROUGH);
        NamedPlugin three = namedPlugin("three", PASS_THROUGH);
        NamedPlugin four = namedPlugin("four", PASS_THROUGH);

        two.setEnabled(false);
        three.setEnabled(false);

        List<NamedPlugin> plugins = asList(one, two, three, four);
        PluginListHandler handler = new PluginListHandler(plugins);

        HttpResponse response = Mono.from(handler.handle(get("/").build(), requestContext())).block();

        assertThat(response.status(), is(OK));
        assertThat(response.bodyAs(UTF_8), is("" +
                "<h3>Enabled</h3>" +
                "<a href='/admin/plugins/one'>one</a><br />" +
                "<a href='/admin/plugins/four'>four</a><br />" +
                "<h3>Disabled</h3>" +
                "<a href='/admin/plugins/two'>two</a><br />" +
                "<a href='/admin/plugins/three'>three</a><br />"));
    }

    @Test
    public void showsLoadedPlugins() {
        NamedPlugin one = namedPlugin("one", PASS_THROUGH);
        NamedPlugin two = namedPlugin("two", PASS_THROUGH);

        List<NamedPlugin> plugins = asList(one, two);
        PluginListHandler handler = new PluginListHandler(plugins);

        HttpResponse response = Mono.from(
                handler.handle(get("/").build(),
                        requestContext())).block();
        assertThat(response.status(), is(OK));
        assertThat(response.bodyAs(UTF_8), is("" +
                "<h3>Loaded</h3>" +
                "<a href='/admin/plugins/one'>one</a><br />" +
                "<a href='/admin/plugins/two'>two</a><br />"));
    }
}