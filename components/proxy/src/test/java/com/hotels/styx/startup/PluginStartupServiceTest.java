/*
  Copyright (C) 2013-2019 Expedia Inc.

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

import com.google.common.collect.ImmutableMap;
import com.hotels.styx.StyxConfig;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.extension.service.spi.ServiceFailureException;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.configstore.ConfigStore;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginPipelineStatus;
import com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginStatus;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static com.hotels.styx.api.LiveHttpResponse.response;
import static com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginPipelineStatus.ALL_PLUGINS_COMPLETE;
import static com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginStatus.COMPLETE;
import static com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginStatus.FAILED_WHILE_LIFECYCLE_STARTING;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.IntStream.range;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.fail;

public class PluginStartupServiceTest {
    private LoggingTestSupport log;

    @BeforeMethod
    public void startRecordingLogs() {
        log = new LoggingTestSupport(PluginStartupService.class);
    }

    @AfterMethod
    public void stopRecordingLogs() {
        log.stop();
    }

    @Test
    public void startsPlugins() throws InterruptedException, ExecutionException, TimeoutException {
        Plugin plugin1 = (request, chain) -> Eventual.of(response().build());
        Plugin plugin2 = (request, chain) -> Eventual.of(response().build());

        StyxServerComponents components = new StyxServerComponents.Builder()
                .styxConfig(new StyxConfig())
                .plugins(ImmutableMap.of(
                        "foo", plugin1,
                        "bar", plugin2))
                .build();

        new PluginStartupService(components).start().get(1, SECONDS);

        ConfigStore configStore = components.environment().configStore();

        assertThat(configStore.get("startup.plugin-pipeline", PluginPipelineStatus.class), isValue(ALL_PLUGINS_COMPLETE));

        assertThat(configStore.get("plugins.foo", NamedPlugin.class).map(NamedPlugin::originalPlugin), isValue(plugin1));
        assertThat(configStore.get("plugins.bar", NamedPlugin.class).map(NamedPlugin::originalPlugin), isValue(plugin2));

        assertThat(configStore.get("startup.plugins.foo", PluginStatus.class), isValue(COMPLETE));
        assertThat(configStore.get("startup.plugins.bar", PluginStatus.class), isValue(COMPLETE));
    }

    @Test
    public void logsPluginStartupFailures() throws InterruptedException, TimeoutException {
        Plugin plugin2 = mock(Plugin.class);
        Plugin plugin4 = mock(Plugin.class);

        doThrow(new IllegalStateException("Dummy")).when(plugin2).styxStarting();
        doThrow(new IllegalArgumentException("Dummy")).when(plugin4).styxStarting();

        StyxServerComponents components = new StyxServerComponents.Builder()
                .styxConfig(new StyxConfig())
                .plugins(nameByIndex(mock(Plugin.class), plugin2, mock(Plugin.class), plugin4))
                .build();

        try {
            new PluginStartupService(components).start().get(1, SECONDS);
            fail("No exception thrown");
        } catch (ExecutionException e) {
            if(e.getCause() instanceof ServiceFailureException) {
                assertThat(log.toString(), containsString("Error starting plugin 'plugin2'"));
                assertThat(log.toString(), containsString("Error starting plugin 'plugin4'"));
            } else {
                fail("Wrong exception type", e.getCause());
            }
        }

        assertThat(log.toString(), containsString("Error starting plugin 'plugin2'"));
        assertThat(log.toString(), containsString("Error starting plugin 'plugin4'"));

        ConfigStore configStore = components.environment().configStore();

        assertThat(configStore.get("startup.plugins.plugin1", PluginStatus.class), isValue(COMPLETE));
        assertThat(configStore.get("startup.plugins.plugin2", PluginStatus.class), isValue(FAILED_WHILE_LIFECYCLE_STARTING));
        assertThat(configStore.get("startup.plugins.plugin3", PluginStatus.class), isValue(COMPLETE));
        assertThat(configStore.get("startup.plugins.plugin4", PluginStatus.class), isValue(FAILED_WHILE_LIFECYCLE_STARTING));
    }

    @Test
    public void attemptsToStartAllPluginsBeforeFailing() throws TimeoutException, InterruptedException {
        Plugin plugin1 = mock(Plugin.class);
        Plugin plugin2 = mock(Plugin.class);
        Plugin plugin3 = mock(Plugin.class);
        Plugin plugin4 = mock(Plugin.class);

        doThrow(new IllegalStateException("Dummy")).when(plugin2).styxStarting();

        StyxServerComponents components = new StyxServerComponents.Builder()
                .styxConfig(new StyxConfig())
                .plugins(nameByIndex(plugin1, plugin2, plugin3, plugin4))
                .build();

        try {
            new PluginStartupService(components).start().get(1, SECONDS);
            fail("No exception thrown");
        } catch (ExecutionException e) {
            verify(plugin1).styxStarting();
            verify(plugin2).styxStarting();
            verify(plugin3).styxStarting();
            verify(plugin4).styxStarting();
        }

        ConfigStore configStore = components.environment().configStore();

        assertThat(configStore.get("startup.plugins.plugin1", PluginStatus.class), isValue(COMPLETE));
        assertThat(configStore.get("startup.plugins.plugin2", PluginStatus.class), isValue(FAILED_WHILE_LIFECYCLE_STARTING));
        assertThat(configStore.get("startup.plugins.plugin3", PluginStatus.class), isValue(COMPLETE));
        assertThat(configStore.get("startup.plugins.plugin4", PluginStatus.class), isValue(COMPLETE));
    }

    private static Map<String, Plugin> nameByIndex(Plugin... plugins) {
        return range(0, plugins.length)
                .boxed()
                .collect(toMap(
                        index -> "plugin" + (index + 1),
                        index -> plugins[index]
                ));
    }
}