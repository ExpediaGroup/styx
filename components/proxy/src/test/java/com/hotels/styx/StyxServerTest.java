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
package com.hotels.styx;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Service;
import com.hotels.styx.admin.AdminServerConfig;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.Configuration.MapBackedConfiguration;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.Registry;
import com.hotels.styx.api.extension.service.spi.StyxService;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.infrastructure.MemoryBackedRegistry;
import com.hotels.styx.infrastructure.RegistryServiceAdapter;
import com.hotels.styx.proxy.ProxyServerConfig;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.server.HttpConnectorConfig;
import com.hotels.styx.startup.StyxServerComponents;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import io.netty.util.ResourceLeakDetector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static ch.qos.logback.classic.Level.ERROR;
import static ch.qos.logback.classic.Level.INFO;
import static com.google.common.util.concurrent.Service.State.FAILED;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.LiveHttpResponse.response;
import static com.hotels.styx.api.configuration.Configuration.EMPTY_CONFIGURATION;
import static com.hotels.styx.proxy.plugin.NamedPlugin.namedPlugin;
import static com.hotels.styx.support.ResourcePaths.fixturesHome;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static io.netty.util.ResourceLeakDetector.Level.DISABLED;
import static java.lang.System.clearProperty;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.setProperty;
import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestInstance(PER_CLASS)
public class StyxServerTest {
    private LoggingTestSupport log;
    private LoggingTestSupport pssLog;

    @BeforeEach
    public void setUp() {
        log = new LoggingTestSupport(StyxServer.class);
        pssLog = new LoggingTestSupport(StyxServer.class);
    }

    @AfterEach
    public void removeAppender() {
        log.stop();
        pssLog.stop();
    }

    @BeforeAll
    public void bypassLogbackConfigurer() {
        // Only affects running StyxServer from main method
        setProperty("UNIT_TESTING_MODE", "true");
    }

    @AfterAll
    public void doneBypassingLogbackConfigurer() {
        // Only affects running StyxServer from main method
        clearProperty("UNIT_TESTING_MODE");
    }

    @Test
    public void invokesPluginLifecycleMethods() {
        Plugin pluginMock1 = mock(Plugin.class);
        Plugin pluginMock2 = mock(Plugin.class);

        StyxServer styxServer = styxServerWithPlugins(ImmutableMap.of(
                "mockplugin1", pluginMock1,
                "mockplugin2", pluginMock2
        ));
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
    public void serverDoesNotStartUntilPluginsAreStarted() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        StyxServer styxServer = styxServerWithPlugins(ImmutableMap.of(
                "slowlyStartingPlugin", new Plugin() {
                    @Override
                    public void styxStarting() {
                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request1, Chain chain) {
                        return Eventual.of(response(OK).build());
                    }
                }
        ));

        try {
            styxServer.startAsync();

            Thread.sleep(10);
            assertThat(styxServer.proxyHttpAddress(), nullValue());

            latch.countDown();

            eventually(() -> assertThat(styxServer.proxyHttpAddress().getPort(), is(greaterThan(0))));
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
            styxServer = styxServerWithPlugins(ImmutableMap.of(
                    "foo", new NonStarterPlugin("foo"),
                    "mockplugin3", mock(Plugin.class)));

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

            styxServer = styxServerWithPlugins(ImmutableMap.of(
                    "plug1", new NonStarterPlugin("plug1"),
                    "plug2", pluginMock2,
                    "plug3", new NonStarterPlugin("plug3"),
                    "plug4", pluginMock4));

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

    @Test
    public void startsFromMain() {
        try {
            setProperty("STYX_HOME", fixturesHome());
            StyxServer.main(new String[0]);

            eventually(() -> assertThat(log.log(), hasItem(loggingEvent(INFO, "Started Styx server in \\d+ ms"))));
        } finally {
            clearProperty("STYX_HOME");
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

    private static StyxServer styxServerWithPlugins(Map<String, Plugin> plugins) {
        StyxServerComponents config = new StyxServerComponents.Builder()
                .configuration(styxConfig())
                .additionalServices(ImmutableMap.of("backendServiceRegistry", new RegistryServiceAdapter(new MemoryBackedRegistry<>())))
                .plugins(plugins)
                .build();

        return new StyxServer(config);
    }

    private static StyxServer styxServerWithBackendServiceRegistry(StyxService backendServiceRegistry) {
        StyxServerComponents config = new StyxServerComponents.Builder()
                .configuration(styxConfig())
                .additionalServices(ImmutableMap.of("backendServiceRegistry", backendServiceRegistry))
                .build();

        return new StyxServer(config);
    }

    private static StyxConfig styxConfig() {
        ProxyServerConfig proxyConfig = new ProxyServerConfig.Builder()
                .setHttpConnector(new HttpConnectorConfig(0))
                .build();

        AdminServerConfig adminConfig = new AdminServerConfig.Builder()
                .setHttpConnector(new HttpConnectorConfig(0))
                .build();

        Configuration config = new MapBackedConfiguration(EMPTY_CONFIGURATION)
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
        public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {
            return null;
        }

        @Override
        public void styxStarting() {
            throw new RuntimeException("Plugin start test error: " + id);
        }
    }

    private static void eventually(Runnable block) {
        long startTime = currentTimeMillis();
        Throwable lastError = null;
        while (currentTimeMillis() - startTime < 3000) {
            try {
                block.run();
                return;
            } catch (AssertionError | Exception e) {
                lastError = e;
            }
        }
        throw new AssertionError("Eventually block did not complete in 3 seconds.", lastError);
    }

}
