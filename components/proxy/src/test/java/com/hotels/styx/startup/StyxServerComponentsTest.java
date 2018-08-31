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
package com.hotels.styx.startup;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.hotels.styx.Environment;
import com.hotels.styx.StyxConfig;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.extension.service.spi.StyxService;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.startup.StyxServerComponents.LoggingSetUp;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static com.hotels.styx.api.FullHttpResponse.response;
import static com.hotels.styx.proxy.plugin.NamedPlugin.namedPlugin;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class StyxServerComponentsTest {
    @Test
    public void setsUpLoggingOnBuild() {
        LoggingSetUp loggingSetUp = mock(LoggingSetUp.class);

        new StyxServerComponents.Builder()
                .styxConfig(new StyxConfig())
                .loggingSetUp(loggingSetUp)
                .build();

        verify(loggingSetUp).setUp(any(Environment.class));
    }

    @Test
    public void loadsPlugins() {
        StyxServerComponents components = new StyxServerComponents.Builder()
                .styxConfig(new StyxConfig())
                .plugins(env -> ImmutableList.of(
                        namedPlugin("plugin1", stubPlugin("MyResponse1")),
                        namedPlugin("plugin2", stubPlugin("MyResponse2"))))
                .build();

        List<NamedPlugin> plugins = components.plugins();
        List<String> names = plugins.stream().map(NamedPlugin::name).collect(toList());

        assertThat(names, contains("plugin1", "plugin2"));
    }

    @Test
    public void loadsServices() {
        StyxServerComponents components = new StyxServerComponents.Builder()
                .styxConfig(new StyxConfig())
                .services(env -> ImmutableMap.of(
                        "service1", mock(StyxService.class),
                        "service2", mock(StyxService.class)))
                .build();

        Map<String, StyxService> services = components.services();

        assertThat(services.keySet(), containsInAnyOrder("service1", "service2"));
    }

    @Test
    public void exposesAdditionalServices() {
        StyxServerComponents components = new StyxServerComponents.Builder()
                .styxConfig(new StyxConfig())
                .additionalServices(ImmutableMap.of(
                        "service1", mock(StyxService.class),
                        "service2", mock(StyxService.class)))
                .build();

        Map<String, StyxService> services = components.services();

        assertThat(services.keySet(), containsInAnyOrder("service1", "service2"));
    }

    @Test
    public void createsEnvironment() {
        Configuration config = new Configuration.MapBackedConfiguration()
                .set("foo", "abc")
                .set("bar", "def");

        StyxServerComponents components = new StyxServerComponents.Builder()
                .styxConfig(new StyxConfig(config))
                .build();

        Environment environment = components.environment();

        assertThat(environment.styxConfig().get("foo", String.class), isValue("abc"));
        assertThat(environment.styxConfig().get("bar", String.class), isValue("def"));

        assertThat(environment.eventBus(), is(notNullValue()));
        assertThat(environment.metricRegistry(), is(notNullValue()));
    }

    private static Plugin stubPlugin(String response) {
        return (request, chain) -> StyxObservable.of(response().body(response, UTF_8).build().toStreamingResponse());
    }
}