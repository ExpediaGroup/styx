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

import com.google.common.util.concurrent.Service;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.proxy.ProxyServerBuilder;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.server.HttpServer;
import org.slf4j.Logger;

import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;
import static java.lang.String.format;
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

    public HttpServer createProxyServer(StyxServerComponents config) {
        HttpHandler pipeline = pipelineFactory.create(config);

        HttpServer proxyServer = new ProxyServerBuilder(config.environment())
                .httpHandler(pipeline)
                .onStartup(() -> initialisePlugins(config.plugins()))
                .build();

        proxyServer.addListener(new PluginsNotifierOfProxyState(config.plugins()), sameThreadExecutor());
        return proxyServer;
    }

    private static void initialisePlugins(Iterable<NamedPlugin> plugins) {
        int exceptions = 0;

        for (NamedPlugin plugin : plugins) {
            try {
                plugin.styxStarting();
            } catch (Exception e) {
                exceptions++;
                LOG.error("Error starting plugin '{}'", plugin.name(), e);
            }
        }

        if (exceptions > 0) {
            throw new RuntimeException(format("%s plugins failed to start", exceptions));
        }
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
