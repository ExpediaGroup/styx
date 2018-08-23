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
package com.hotels.styx;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Service;
import com.hotels.styx.admin.AdminServerConfig;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.Configuration.MapBackedConfiguration;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.Registry;
import com.hotels.styx.api.extension.service.spi.StyxService;
import com.hotels.styx.infrastructure.MemoryBackedRegistry;
import com.hotels.styx.infrastructure.RegistryServiceAdapter;
import com.hotels.styx.proxy.ProxyServerConfig;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.server.HttpConnectorConfig;
import com.hotels.styx.startup.ProxyServerSetUp;
import com.hotels.styx.startup.StyxServerComponents;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import io.netty.util.ResourceLeakDetector;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static ch.qos.logback.classic.Level.ERROR;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.util.concurrent.Service.State.FAILED;
import static com.hotels.styx.api.configuration.Configuration.EMPTY_CONFIGURATION;
import static com.hotels.styx.common.HostAndPorts.freePort;
import static com.hotels.styx.proxy.plugin.NamedPlugin.namedPlugin;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static io.netty.util.ResourceLeakDetector.Level.DISABLED;
import static java.lang.System.currentTimeMillis;
import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StyxServerTest {
    private LoggingTestSupport log;
    private LoggingTestSupport pssLog;

    @BeforeMethod
    public void setUp() {
        log = new LoggingTestSupport(StyxServer.class);
        pssLog = new LoggingTestSupport(ProxyServerSetUp.class);
    }

    @AfterMethod
    public void removeAppender() {
        log.stop();
        pssLog.stop();
    }

    @Test
    public void invokesPluginLifecycleMethods() {
        Plugin pluginMock1 = mock(Plugin.class);
        Plugin pluginMock2 = mock(Plugin.class);

        NamedPlugin plugin1 = namedPlugin("mockplugin1", pluginMock1);
        NamedPlugin plugin2 = namedPlugin("mockplugin2", pluginMock2);

        StyxServer styxServer = styxServerWithPlugins(plugin1, plugin2);
        try {
            styxServer.startAsync().awaitRunning();
            verify(pluginMock1).styxStarting();
            verify(pluginMock2).styxStarting();

            styxServer.stopAsync().awaitTerminated();
            verify(pluginMock1).styxStopping();
            verify(pluginMock2).styxStopping();
        } finally {
            stopIfRunning(styxServer);
        }
    }

    @Test
    public void disablesResourceLeakDetectionByDefault() {
        StyxServerComponents config = new StyxServerComponents.Builder()
                .configuration(EMPTY_CONFIGURATION)
                .additionalServices(ImmutableMap.of("backendServiceRegistry", new RegistryServiceAdapter(new MemoryBackedRegistry<>())))
                .build();

        new StyxServer(config);

        assertThat(ResourceLeakDetector.getLevel(), is(DISABLED));
    }

    @Test
    public void stopsTheServerWhenPluginFailsToStart() {
        StyxServer styxServer = null;
        try {
            NamedPlugin plugin1 = failingPlugin("foo");
            NamedPlugin plugin2 = namedPlugin("mockplugin3", mock(Plugin.class));

            styxServer = styxServerWithPlugins(plugin1, plugin2);

            Service service = styxServer.startAsync();
            eventually(() -> assertThat(service.state(), is(FAILED)));

            assertThat(pssLog.log(), hasItem(
                    loggingEvent(ERROR, "Error starting plugin 'foo'", RuntimeException.class, "Plugin start test error: foo")));

            assertThat(styxServer.state(), is(FAILED));
        } finally {
            stopIfRunning(styxServer);
        }
    }

    @Test
    public void allPluginsAreStartedEvenIfSomeFail() {
        StyxServer styxServer = null;
        try {
            Plugin pluginMock2 = mock(Plugin.class);
            Plugin pluginMock4 = mock(Plugin.class);

            styxServer = styxServerWithPlugins(
                    failingPlugin("plug1"),
                    namedPlugin("plug2", pluginMock2),
                    failingPlugin("plug3"),
                    namedPlugin("plug4", pluginMock4)
            );

            Service service = styxServer.startAsync();
            eventually(() -> assertThat(service.state(), is(FAILED)));

            assertThat(pssLog.log(), hasItem(loggingEvent(ERROR, "Error starting plugin 'plug1'", RuntimeException.class, "Plugin start test error: plug1")));
            assertThat(pssLog.log(), hasItem(loggingEvent(ERROR, "Error starting plugin 'plug3'", RuntimeException.class, "Plugin start test error: plug3")));

            verify(pluginMock2).styxStarting();
            verify(pluginMock4).styxStarting();
        } finally {
            stopIfRunning(styxServer);
        }
    }

    @Test
    public void systemExitIsCalledWhenCreateStyxServerFails() {
        Runtime runtime = captureSystemExit(() -> StyxServer.main(new String[0]));
        verify(runtime).exit(1);
    }

    @Test
    public void serverDoesNotStartIfServiceFails() {
        StyxServer styxServer = null;
        try {
            StyxService testService = registryThatFailsToStart();
            styxServer = styxServerWithBackendServiceRegistry(testService);

            Service serverService = styxServer.startAsync();
            eventually(() -> assertThat(serverService.state(), is(FAILED)));

            assertThat(styxServer.state(), is(FAILED));
        } finally {
            stopIfRunning(styxServer);
        }
    }

    private static StyxService registryThatFailsToStart() {
        Registry<BackendService> registry = mock(Registry.class);
        when(registry.get()).thenReturn(emptyList());

        return new RegistryServiceAdapter(registry) {
            @Override
            protected CompletableFuture<Void> startService() {
                CompletableFuture<Void> future = new CompletableFuture<>();
                future.completeExceptionally(new IllegalStateException("Just a test"));
                return future;
            }
        };
    }

    private static StyxServer styxServerWithPlugins(NamedPlugin... plugins) {
        return styxServerWithPlugins(newArrayList(plugins));
    }

    private static StyxServer styxServerWithPlugins(List<NamedPlugin> pluginsList) {
        StyxServerComponents config = new StyxServerComponents.Builder()
                .configuration(styxConfig(EMPTY_CONFIGURATION))
                .additionalServices(ImmutableMap.of("backendServiceRegistry", new RegistryServiceAdapter(new MemoryBackedRegistry<>())))
                .plugins(pluginsList)
                .build();

        return new StyxServer(config);
    }

    private static StyxServer styxServerWithBackendServiceRegistry(StyxService backendServiceRegistry) {
        StyxServerComponents config = new StyxServerComponents.Builder()
                .configuration(styxConfig(EMPTY_CONFIGURATION))
                .additionalServices(ImmutableMap.of("backendServiceRegistry", backendServiceRegistry))
                .build();

        return new StyxServer(config);
    }

    private static StyxConfig styxConfig(Configuration baseConfiguration) {
        ProxyServerConfig proxyConfig = new ProxyServerConfig.Builder()
                .setHttpConnector(new HttpConnectorConfig(freePort()))
                .build();

        AdminServerConfig adminConfig = new AdminServerConfig.Builder()
                .setHttpConnector(new HttpConnectorConfig(freePort()))
                .build();

        Configuration config = new MapBackedConfiguration(baseConfiguration)
                .set("admin", adminConfig)
                .set("proxy", proxyConfig);

        return new StyxConfig(config);
    }

    private static void stopIfRunning(StyxServer styxServer) {
        if (styxServer != null && styxServer.isRunning()) {
            styxServer.stopAsync().awaitTerminated();
        }
    }

    private static NamedPlugin failingPlugin(String id) {
        return namedPlugin(id, new NonStarterPlugin(id));
    }

    static class NonStarterPlugin implements Plugin {
        private final String id;

        NonStarterPlugin(String id) {
            this.id = id;
        }

        @Override
        public StyxObservable<HttpResponse> intercept(HttpRequest request, Chain chain) {
            return null;
        }

        @Override
        public void styxStarting() {
            throw new RuntimeException("Plugin start test error: " + id);
        }
    }

    private static void eventually(Runnable block) {
        long startTime = currentTimeMillis();
        while (currentTimeMillis() - startTime < 3000) {
            try {
                block.run();
                return;
            } catch (Exception e) {
                // pass
            }
        }
        throw new AssertionError("Eventually block did not complete in 3 seconds.");
    }

    private static Runtime captureSystemExit(Runnable block) {
        try {
            Runtime originalRuntime = Runtime.getRuntime();
            Field runtimeField = Runtime.class.getDeclaredField("currentRuntime");
            Runtime mockRuntime = mock(Runtime.class);
            try {
                runtimeField.setAccessible(true);
                runtimeField.set(Runtime.class, mockRuntime);
                block.run();
            } finally {
                runtimeField.set(Runtime.class, originalRuntime);
                runtimeField.setAccessible(false);
            }

            return mockRuntime;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
