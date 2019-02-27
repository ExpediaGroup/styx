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
package com.hotels.styx.startup.extensions;

import com.hotels.styx.configstore.ConfigStore;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * For setting the plugin startup status in the config store.
 */
public class PluginStatusNotifications {
    public static final String PLUGIN_STATUS_KEY_PREFIX = "startup.plugins";
    public static final String PLUGIN_STATUS_KEY_FORMAT = PLUGIN_STATUS_KEY_PREFIX + ".%s";
    public static final String PLUGIN_PIPELINE_STATUS_KEY = "startup.plugin-pipeline";

    private final ConfigStore configStore;

    /**
     * Construct a new instance.
     *
     * @param configStore config store.
     */
    public PluginStatusNotifications(ConfigStore configStore) {
        this.configStore = requireNonNull(configStore);
    }

    /**
     * Notifies of plugin status using config store.
     *
     * @param pluginName name of plugin
     * @param status     status
     */
    public void notifyPluginStatus(String pluginName, PluginStatus status) {
        configStore.set(format(PLUGIN_STATUS_KEY_FORMAT, pluginName), status);
    }

    /**
     * Notifies of overall plugin pipeline status using config store.
     *
     * @param status status
     */
    public void notifyPluginPipelineStatus(PluginPipelineStatus status) {
        configStore.set(PLUGIN_PIPELINE_STATUS_KEY, status);
    }

    /**
     * Status for overall plugin pipeline during start-up.
     */
    public enum PluginPipelineStatus {
        INCOMPLETE, ALL_PLUGINS_COMPLETE, AT_LEAST_ONE_PLUGIN_FAILED
    }

    /**
     * Status for a plugin to be in during start-up.
     */
    public enum PluginStatus {
        LOADING_CLASSES("incomplete:loading-classes"),
        LOADED_CLASSES("incomplete:loaded-classes"),
        CONSTRUCTING("incomplete:constructing"),
        CONSTRUCTED("incomplete:constructed"),
        LIFECYCLE_STARTING("incomplete:lifecycle-starting"),
        COMPLETE("complete"),
        FAILED_WHILE_LOADING_CLASSES("failed:loading-classes"),
        FAILED_WHILE_CONSTRUCTING("failed:constructing"),
        FAILED_WHILE_LIFECYCLE_STARTING("failed:lifecycle-starting");

        private final String description;

        PluginStatus(String description) {
            this.description = requireNonNull(description);
        }

        @Override
        public String toString() {
            return description;
        }
    }
}
