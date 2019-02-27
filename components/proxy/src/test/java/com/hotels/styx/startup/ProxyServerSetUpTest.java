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

import com.hotels.styx.StyxConfig;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.Configuration.MapBackedConfiguration;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.proxy.ProxyServerConfig;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.server.HttpConnectorConfig;
import com.hotels.styx.server.HttpServer;
import com.hotels.styx.server.HttpsConnectorConfig;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.IntStream.range;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.fail;

public class ProxyServerSetUpTest {

    private final ProxyServerConfig proxyServerConfig = new ProxyServerConfig.Builder()
            .setHttpConnector(new HttpConnectorConfig(0))
            .setHttpsConnector(new HttpsConnectorConfig.Builder()
                    .port(0)
                    .build())
            .build();

    private final Configuration config = new MapBackedConfiguration()
            .set("proxy", proxyServerConfig);

    private HttpServer server;
    private LoggingTestSupport log;

    @BeforeMethod
    public void startRecordingLogs() {
        log = new LoggingTestSupport(ProxyServerSetUp.class);
    }

    @AfterMethod
    public void stopRecordingLogs() {
        log.stop();
    }

    @AfterMethod
    public void stopServer() throws TimeoutException {
        if (server != null) {
            try {
                server.stopAsync().awaitTerminated(3, SECONDS);
            } catch (IllegalStateException e) {
                // Caused if the server is already in the 'FAILED' state.
            }
        }
    }

    @Test
    public void createsServer() throws TimeoutException, InterruptedException {
        StyxServerComponents components = new StyxServerComponents.Builder()
                .styxConfig(new StyxConfig(config))
                .build();
        signalPluginsLoaded(components);

        PipelineFactory pipelineFactory = mockPipelineFactory(components);

        server = new ProxyServerSetUp(pipelineFactory).createProxyServer(components);

        server.startAsync().awaitRunning(3, SECONDS);

        assertThat(server.httpAddress().getPort(), is(server.httpAddress().getPort()));
        assertThat(server.httpsAddress().getPort(), is(server.httpsAddress().getPort()));
    }

    @Test
    public void notifiesPluginsOnStart() throws TimeoutException, InterruptedException {
        Plugin plugin1 = mock(Plugin.class);
        Plugin plugin2 = mock(Plugin.class);

        StyxServerComponents components = new StyxServerComponents.Builder()
                .styxConfig(new StyxConfig(config))
                .plugins(nameByIndex(plugin1, plugin2))
                .build();
        signalPluginsLoaded(components);

        PipelineFactory pipelineFactory = mockPipelineFactory(components);

        server = new ProxyServerSetUp(pipelineFactory).createProxyServer(components);

        server.startAsync().awaitRunning(3, SECONDS);

        verify(plugin1).styxStarting();
        verify(plugin2).styxStarting();
    }

    @Test
    public void notifiesPluginsOnStop() throws TimeoutException, InterruptedException {
        Plugin plugin1 = mock(Plugin.class);
        Plugin plugin2 = mock(Plugin.class);

        StyxServerComponents components = new StyxServerComponents.Builder()
                .styxConfig(new StyxConfig(config))
                .plugins(nameByIndex(plugin1, plugin2))
                .build();
        signalPluginsLoaded(components);

        PipelineFactory pipelineFactory = mockPipelineFactory(components);

        server = new ProxyServerSetUp(pipelineFactory).createProxyServer(components);

        server.startAsync().awaitRunning(3, SECONDS);
        server.stopAsync().awaitTerminated(3, SECONDS);

        verify(plugin1).styxStopping();
        verify(plugin2).styxStopping();
    }

    @Test
    public void logsPluginShutdownFailures() throws TimeoutException, InterruptedException {
        Plugin plugin2 = mock(Plugin.class);
        Plugin plugin4 = mock(Plugin.class);

        doThrow(new IllegalStateException("Dummy")).when(plugin2).styxStopping();
        doThrow(new IllegalArgumentException("Dummy")).when(plugin4).styxStopping();

        StyxServerComponents components = new StyxServerComponents.Builder()
                .styxConfig(new StyxConfig(config))
                .plugins(nameByIndex(mock(Plugin.class), plugin2, mock(Plugin.class), plugin4))
                .build();
        signalPluginsLoaded(components);

        PipelineFactory pipelineFactory = mockPipelineFactory(components);

        server = new ProxyServerSetUp(pipelineFactory).createProxyServer(components);

        server.startAsync().awaitRunning(3, SECONDS);

        server.stopAsync().awaitTerminated(3, SECONDS);

        assertThat(log.toString(), containsString("Error stopping plugin 'plugin2'"));
        assertThat(log.toString(), containsString("Error stopping plugin 'plugin4'"));
    }

    private static void signalPluginsLoaded(StyxServerComponents components) {
        try {
            new PluginStartupService(components).start().get(20, SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    private static PipelineFactory mockPipelineFactory(StyxServerComponents components) {
        PipelineFactory pipelineFactory = mock(PipelineFactory.class);
        HttpHandler pipeline = mock(HttpHandler.class);
        List<NamedPlugin> plugins = components.environment().configStore().valuesStartingWith("plugins", NamedPlugin.class);
        when(pipelineFactory.create(components, plugins)).thenReturn(pipeline);
        return pipelineFactory;
    }

    // Allows us to check for exceptions without letting them escape the test method
    private static void expect(Task task, Class<? extends Exception> exceptionType) {
        try {
            task.execute();
        } catch (Exception e) {
            assertThat(e, is(instanceOf(exceptionType)));
            return;
        }

        fail("Expected " + exceptionType.getName() + " to be thrown");
    }

    private static Map<String, Plugin> nameByIndex(Plugin... plugins) {
        return range(0, plugins.length)
                .boxed()
                .collect(toMap(
                        index -> "plugin" + (index + 1),
                        index -> plugins[index]
                ));
    }

    private interface Task {
        void execute() throws Exception;
    }
}