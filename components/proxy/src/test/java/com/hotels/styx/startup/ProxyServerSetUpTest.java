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
import com.hotels.styx.StyxPipelineFactory;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.Configuration.MapBackedConfiguration;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.proxy.ProxyServerConfig;
import com.hotels.styx.server.HttpConnectorConfig;
import com.hotels.styx.server.HttpServer;
import com.hotels.styx.server.HttpsConnectorConfig;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.IntStream.range;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @BeforeEach
    public void startRecordingLogs() {
        log = new LoggingTestSupport(ProxyServerSetUp.class);
    }

    @AfterEach
    public void stopRecordingLogs() {
        log.stop();
    }

    @AfterEach
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
    public void createsServer() throws TimeoutException {
        StyxServerComponents components = new StyxServerComponents.Builder()
                .styxConfig(new StyxConfig(config))
                .build();

        StyxPipelineFactory styxPipelineFactory = mockStyxPipelineFactory();

        server = new ProxyServerSetUp(styxPipelineFactory).createProxyServer(components);

        server.startAsync().awaitRunning(3, SECONDS);

        assertThat(server.httpAddress().getPort(), is(server.httpAddress().getPort()));
        assertThat(server.httpsAddress().getPort(), is(server.httpsAddress().getPort()));
    }

    @Test
    public void notifiesPluginsOnStart() throws TimeoutException {
        Plugin plugin1 = mock(Plugin.class);
        Plugin plugin2 = mock(Plugin.class);

        StyxServerComponents components = new StyxServerComponents.Builder()
                .styxConfig(new StyxConfig(config))
                .plugins(nameByIndex(plugin1, plugin2))
                .build();

        StyxPipelineFactory styxPipelineFactory = mockStyxPipelineFactory();

        server = new ProxyServerSetUp(styxPipelineFactory).createProxyServer(components);

        server.startAsync().awaitRunning(3, SECONDS);

        verify(plugin1).styxStarting();
        verify(plugin2).styxStarting();
    }

    @Test
    public void notifiesPluginsOnStop() throws TimeoutException {
        Plugin plugin1 = mock(Plugin.class);
        Plugin plugin2 = mock(Plugin.class);

        StyxServerComponents components = new StyxServerComponents.Builder()
                .styxConfig(new StyxConfig(config))
                .plugins(nameByIndex(plugin1, plugin2))
                .build();

        StyxPipelineFactory styxPipelineFactory = mockStyxPipelineFactory();

        server = new ProxyServerSetUp(styxPipelineFactory).createProxyServer(components);

        server.startAsync().awaitRunning(3, SECONDS);
        server.stopAsync().awaitTerminated(3, SECONDS);

        verify(plugin1).styxStopping();
        verify(plugin2).styxStopping();
    }

    @Test
    public void logsPluginStartupFailures() {
        Plugin plugin2 = mock(Plugin.class);
        Plugin plugin4 = mock(Plugin.class);

        doThrow(new IllegalStateException("Dummy")).when(plugin2).styxStarting();
        doThrow(new IllegalArgumentException("Dummy")).when(plugin4).styxStarting();

        StyxServerComponents components = new StyxServerComponents.Builder()
                .styxConfig(new StyxConfig(config))
                .plugins(nameByIndex(mock(Plugin.class), plugin2, mock(Plugin.class), plugin4))
                .build();

        StyxPipelineFactory styxPipelineFactory = mockStyxPipelineFactory();

        server = new ProxyServerSetUp(styxPipelineFactory).createProxyServer(components);

        expect(() -> server.startAsync().awaitRunning(3, SECONDS), IllegalStateException.class);

        assertThat(log.toString(), containsString("Error starting plugin 'plugin2'"));
        assertThat(log.toString(), containsString("Error starting plugin 'plugin4'"));
    }

    @Test
    public void attemptsToStartAllPluginsBeforeFailing() {
        Plugin plugin1 = mock(Plugin.class);
        Plugin plugin2 = mock(Plugin.class);
        Plugin plugin3 = mock(Plugin.class);
        Plugin plugin4 = mock(Plugin.class);

        doThrow(new IllegalStateException("Dummy")).when(plugin2).styxStarting();

        StyxServerComponents components = new StyxServerComponents.Builder()
                .styxConfig(new StyxConfig(config))
                .plugins(nameByIndex(plugin1, plugin2, plugin3, plugin4))
                .build();

        StyxPipelineFactory styxPipelineFactory = mockStyxPipelineFactory();

        server = new ProxyServerSetUp(styxPipelineFactory).createProxyServer(components);

        expect(() -> server.startAsync().awaitRunning(3, SECONDS), IllegalStateException.class);

        verify(plugin1).styxStarting();
        verify(plugin2).styxStarting();
        verify(plugin3).styxStarting();
        verify(plugin4).styxStarting();
    }

    @Test
    public void logsPluginShutdownFailures() throws TimeoutException {
        Plugin plugin2 = mock(Plugin.class);
        Plugin plugin4 = mock(Plugin.class);

        doThrow(new IllegalStateException("Dummy")).when(plugin2).styxStopping();
        doThrow(new IllegalArgumentException("Dummy")).when(plugin4).styxStopping();

        StyxServerComponents components = new StyxServerComponents.Builder()
                .styxConfig(new StyxConfig(config))
                .plugins(nameByIndex(mock(Plugin.class), plugin2, mock(Plugin.class), plugin4))
                .build();

        StyxPipelineFactory styxPipelineFactory = mockStyxPipelineFactory();

        server = new ProxyServerSetUp(styxPipelineFactory).createProxyServer(components);

        server.startAsync().awaitRunning(3, SECONDS);

        server.stopAsync().awaitTerminated(3, SECONDS);

        assertThat(log.toString(), containsString("Error stopping plugin 'plugin2'"));
        assertThat(log.toString(), containsString("Error stopping plugin 'plugin4'"));
    }

    private static StyxPipelineFactory mockStyxPipelineFactory() {
        StyxPipelineFactory styxPipelineFactory = mock(StyxPipelineFactory.class);
        HttpHandler pipeline = mock(HttpHandler.class);
        when(styxPipelineFactory.create()).thenReturn(pipeline);
        return styxPipelineFactory;
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