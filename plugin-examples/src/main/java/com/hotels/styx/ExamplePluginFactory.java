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
package com.hotels.styx;

import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.plugins.spi.PluginFactory;

/**
 * The factory is used to construct your plugin. You can do this however you like.
 */
public class ExamplePluginFactory implements PluginFactory {
    /**
     * The environment object will provide this plugin with the configuration you created in the YAML file.
     *
     * @param environment environment as described above
     * @return created plugin
     */
    @Override
    public Plugin create(PluginFactory.Environment environment) {
        /* Note that it is not necessary for the Config to be a separate object from the plugin.
         * You could have your configuration object implement "Plugin" and simply return that,
         * If you consider it more suited to your use case.
         *
         * However, using a separate config object has the advantage that your PluginFactory is
         * free to select a Plugin implementation at runtime.
         */
        ExamplePluginConfig config = environment.pluginConfig(ExamplePluginConfig.class);

        return new ExamplePlugin(config);
    }
}
