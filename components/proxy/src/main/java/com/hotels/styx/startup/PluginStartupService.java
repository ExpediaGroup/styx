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

import com.hotels.styx.Environment;
import com.hotels.styx.api.extension.service.spi.AbstractStyxService;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.startup.extensions.ConfiguredPluginFactory;
import com.hotels.styx.startup.extensions.PluginStatusNotifications;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.hotels.styx.common.SequenceProcessor.processSequence;
import static com.hotels.styx.startup.extensions.PluginLoadingForStartup.loadPlugins;
import static com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginPipelineStatus.ALL_PLUGINS_COMPLETE;
import static com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginPipelineStatus.AT_LEAST_ONE_PLUGIN_FAILED;
import static com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginPipelineStatus.INCOMPLETE;
import static com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginStatus.COMPLETE;
import static com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginStatus.FAILED_WHILE_STARTING;
import static com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginStatus.STARTING;
import static java.lang.String.format;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Service to start up plugins and add them to the config store.
 */
public class PluginStartupService extends AbstractStyxService {
    public static final String PLUGIN_STATUS_KEY_FORMAT = "startup.plugins.%s";
    private static final String PLUGIN_KEY_FORMAT = "plugins.%s";

    private static final Logger LOGGER = getLogger(PluginStartupService.class);

    private final List<ConfiguredPluginFactory> pluginFactories;
    private final Environment environment;

    /**
     * Constructs an service instance.
     * If the components object has plugin factories explicitly set, those factories will be used,
     * otherwise the service will get them from the configuration.
     *
     * @param components server components
     */
    public PluginStartupService(StyxServerComponents components) {
        super("PluginStartupService");
        this.pluginFactories = components.pluginFactories().orElse(null);
        this.environment = components.environment();
    }

    protected CompletableFuture<Void> startService() {
        return CompletableFuture.runAsync(() -> {
            List<NamedPlugin> plugins = pluginFactories == null
                    ? loadPlugins(environment)
                    : loadPlugins(environment, pluginFactories);

            PluginStatusNotifications notifications = new PluginStatusNotifications(environment.configStore());
            notifications.notifyPluginPipelineStatus(INCOMPLETE);

            processSequence(plugins)
                    .map(plugin -> {
                        notifications.notifyPluginStatus(plugin.name(), STARTING);
                        plugin.styxStarting();
                        return null;
                    })

                    .onEachSuccess((plugin, ignore) -> {
                        registerPlugin(plugin);
                        notifications.notifyPluginStatus(plugin.name(), COMPLETE);
                    })

                    .onEachFailure((plugin, err) -> {
                        notifications.notifyPluginStatus(plugin.name(), FAILED_WHILE_STARTING);
                        LOGGER.error("Error starting plugin '{}'", plugin.name(), err);
                    })

                    .failuresPostProcessing(failures -> {
                        notifications.notifyPluginPipelineStatus(AT_LEAST_ONE_PLUGIN_FAILED);
                        throw new StartupException(format("%s plugins failed to start", failures.size()));
                    }).collect();

            notifications.notifyPluginPipelineStatus(ALL_PLUGINS_COMPLETE);
        });
    }

    private void registerPlugin(NamedPlugin plugin) {
        environment.configStore().set(format(PLUGIN_KEY_FORMAT, plugin.name()), plugin);
    }
}
