/**
 * Copyright (C) 2013-2018 Expedia Inc.
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
package com.hotels.styx.server;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.google.common.util.concurrent.Service;
import com.hotels.styx.StyxConfig;
import com.hotels.styx.StyxServer;
import com.hotels.styx.StyxServerBuilder;
import com.hotels.styx.admin.AdminServerConfig;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.Configuration.MapBackedConfiguration;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import com.hotels.styx.infrastructure.MemoryBackedRegistry;
import com.hotels.styx.infrastructure.RegistryServiceAdapter;
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig;
import com.hotels.styx.proxy.ProxyServerBuilder;
import com.hotels.styx.proxy.ProxyServerConfig;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.proxy.plugin.PluginSuppliers;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import io.netty.util.ResourceLeakDetector;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observable;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static ch.qos.logback.classic.Level.ERROR;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.util.concurrent.Service.State.FAILED;
import static com.hotels.styx.api.configuration.Configuration.EMPTY_CONFIGURATION;
import static com.hotels.styx.api.support.HostAndPorts.freePort;
import static com.hotels.styx.proxy.plugin.NamedPlugin.namedPlugin;
import static com.hotels.styx.support.ResourcePaths.fixturesHome;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static io.netty.util.ResourceLeakDetector.Level.DISABLED;
import static java.lang.System.currentTimeMillis;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class StyxServerTest {
    Path FIXTURES_CLASS_PATH = fixturesHome(StyxServerTest.class, "/plugins");

    private LoggingTestSupport log;

    @BeforeMethod
    public void setUp() {
        log = new LoggingTestSupport(StyxServer.class);
    }

    @AfterMethod
    public void removeAppender() {
        log.stop();
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
        new StyxServerBuilder(new StyxConfig())
                .additionalServices("backendServiceRegistry", new RegistryServiceAdapter(new MemoryBackedRegistry <>()))
                .build();

        assertThat(ResourceLeakDetector.getLevel(), is(DISABLED));
    }

    @Test
    public void stopsTheServerWhenPluginFailsToStart() throws InterruptedException {
        StyxServer styxServer = null;
        try {
            NamedPlugin plugin1 = failingPlugin("foo");
            NamedPlugin plugin2 = namedPlugin("mockplugin3", mock(Plugin.class));

            styxServer = styxServerWithPlugins(plugin1, plugin2);

            Service service = styxServer.startAsync();
            eventually(() -> assertThat(service.state(), is(FAILED)));

            assertThat(log.log(), hasItem(
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

            assertThat(log.log(), hasItem(loggingEvent(ERROR, "Error starting plugin 'plug1'", RuntimeException.class, "Plugin start test error: plug1")));
            assertThat(log.log(), hasItem(loggingEvent(ERROR, "Error starting plugin 'plug3'", RuntimeException.class, "Plugin start test error: plug3")));

            verify(pluginMock2).styxStarting();
            verify(pluginMock4).styxStarting();
        } finally {
            stopIfRunning(styxServer);
        }
    }

    @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "1 plugins could not be loaded")
    public void stopsTheServerWhenPluginCannotBeLoaded() {
        String yaml = "" +
                "plugins:\n" +
                "  active: myPlugin\n" +
                "  all:\n" +
                "    myPlugin:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.server.StyxServerTest$FailPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "";

        buildStyxServerAndExpectException(yaml, log ->
                assertThat(log, hasItem(loggingEvent(ERROR, "Could not load plugin myPlugin: com.hotels.styx.server.StyxServerTest\\$FailPluginFactory", RuntimeException.class, "PluginFactory test exception thrown"))));
    }

    @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "2 plugins could not be loaded")
    public void allPluginsAreStartedEvenIfSomeCannotBeLoaded() {
        String yaml = "" +
                "plugins:\n" +
                "  active: plug1,plug2,plug3,plug4\n" +
                "  all:\n" +
                "    plug1:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.server.StyxServerTest$FailPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "    plug2:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.server.StyxServerTest$OkPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "    plug3:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.server.StyxServerTest$FailPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "    plug4:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.server.StyxServerTest$OkPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "";

        buildStyxServerAndExpectException(yaml, log -> {
            assertThat(log, hasItem(loggingEvent(ERROR, "Could not load plugin plug1: com.hotels.styx.server.StyxServerTest\\$FailPluginFactory", RuntimeException.class, "PluginFactory test exception thrown")));
            assertThat(log, hasItem(loggingEvent(ERROR, "Could not load plugin plug3: com.hotels.styx.server.StyxServerTest\\$FailPluginFactory", RuntimeException.class, "PluginFactory test exception thrown")));
        });
    }

    private static void buildStyxServerAndExpectException(String yaml, Consumer<List<ILoggingEvent>> assertions) {
        LoggingTestSupport pluginSuppliersLog = new LoggingTestSupport(PluginSuppliers.class);

        try {
            styxServerWithYaml(yaml);
        } catch (RuntimeException e) {
            assertions.accept(pluginSuppliersLog.log());
            throw e;
        } finally {
            pluginSuppliersLog.stop();
        }
    }

    // Instantiated reflexively.
    public static class FailPluginFactory implements PluginFactory {
        @Override
        public Plugin create(Environment environment) {
            throw new RuntimeException("PluginFactory test exception thrown");
        }
    }

    public static class OkPluginFactory implements PluginFactory {
        @Override
        public Plugin create(Environment environment) {
            return new OkPlugin();
        }
    }

    private static StyxServer styxServerWithPlugins(NamedPlugin... plugins) {
        return styxServerWithPlugins(newArrayList(plugins));
    }

    private static StyxServer styxServerWithPlugins(List<NamedPlugin> pluginsList) {
        return new StyxServerBuilder(styxConfig(EMPTY_CONFIGURATION))
                .pluginsSupplier(() -> pluginsList)
                .additionalServices("backendServiceRegistry", new RegistryServiceAdapter(new MemoryBackedRegistry<>()))
                .build();
    }

    private static void styxServerWithYaml(String yaml) {
        new StyxServerBuilder(styxConfig(new YamlConfig(yaml)))
                .build();
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
        public Observable<HttpResponse> intercept(HttpRequest request, HttpInterceptor.Chain chain) {
            return null;
        }

        @Override
        public void styxStarting() {
            throw new RuntimeException("Plugin start test error: " + id);
        }
    }

    static class OkPlugin implements Plugin {
        @Override
        public Observable<HttpResponse> intercept(HttpRequest request, Chain chain) {
            return chain.proceed(request);
        }

        @Override
        public void styxStarting() {
            LoggerFactory.getLogger(ProxyServerBuilder.class).info("Plugin started");
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
}
