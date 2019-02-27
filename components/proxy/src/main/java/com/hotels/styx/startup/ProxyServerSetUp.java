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

import com.google.common.util.concurrent.Service;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.proxy.ProxyServerBuilder;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.server.HttpServer;
import com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginPipelineStatus;
import org.slf4j.Logger;

import java.util.List;

import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;
import static com.hotels.styx.startup.ProxyStatusNotifications.notifyProxyStatus;
import static com.hotels.styx.startup.extensions.PluginStatusNotifications.PLUGIN_PIPELINE_STATUS_KEY;
import static com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginPipelineStatus.ALL_PLUGINS_COMPLETE;
import static com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginPipelineStatus.AT_LEAST_ONE_PLUGIN_FAILED;
import static com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginPipelineStatus.INCOMPLETE;
import static java.lang.Thread.sleep;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Used to set-up the proxy server for Styx.
 */
public final class ProxyServerSetUp {
    private static final Logger LOG = getLogger(ProxyServerSetUp.class);

    private final PipelineFactory pipelineFactory;

    public ProxyServerSetUp(PipelineFactory pipelineFactory) {
        this.pipelineFactory = requireNonNull(pipelineFactory);
    }

    public HttpServer createProxyServer(StyxServerComponents components) throws InterruptedException {
        notifyProxyStatus(
                components.environment().configStore(),
                components.environment().configuration().proxyServerConfig());

        // TODO see https://github.com/HotelsDotCom/styx/issues/382

        while (!arePluginsLoaded(components)) {
            sleep(100L);
        }

        List<NamedPlugin> plugins = components.environment().configStore()
                .valuesStartingWith("plugins", NamedPlugin.class);

        return createProxyServer0(components, plugins);
    }

    private static boolean arePluginsLoaded(StyxServerComponents components) {
        PluginPipelineStatus status = components.environment().configStore()
                .get(PLUGIN_PIPELINE_STATUS_KEY, PluginPipelineStatus.class)
                .orElse(INCOMPLETE);

        if (status == AT_LEAST_ONE_PLUGIN_FAILED) {
            throw new IllegalStateException("One or more plugins failed to start");
        }

        return status == ALL_PLUGINS_COMPLETE;
    }

    private HttpServer createProxyServer0(StyxServerComponents components, List<NamedPlugin> plugins) {
        HttpHandler pipeline = pipelineFactory.create(components, plugins);

        HttpServer proxyServer = new ProxyServerBuilder(components.environment())
                .httpHandler(pipeline)
                .build();

        proxyServer.addListener(new PluginsNotifierOfProxyState(plugins), sameThreadExecutor());
        return proxyServer;
    }

    private static class PluginsNotifierOfProxyState extends Service.Listener {
        private final Iterable<NamedPlugin> plugins;

        PluginsNotifierOfProxyState(Iterable<NamedPlugin> plugins) {
            this.plugins = plugins;
        }

        @Override
        public void stopping(Service.State from) {
            for (NamedPlugin plugin : plugins) {
                try {
                    plugin.styxStopping();
                } catch (Exception e) {
                    LOG.error("Error stopping plugin '{}'", plugin.name(), e);
                }
            }
        }
    }
}
