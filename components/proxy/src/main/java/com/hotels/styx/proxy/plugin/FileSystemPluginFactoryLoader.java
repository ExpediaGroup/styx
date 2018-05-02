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
package com.hotels.styx.proxy.plugin;

import com.hotels.styx.api.configuration.ConfigurationException;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import com.hotels.styx.spi.config.SpiExtension;

import static com.hotels.styx.spi.ExtensionObjectFactory.EXTENSION_OBJECT_FACTORY;
import static java.lang.String.format;

/**
 * Loads a named plugin from file system.
 *
 */
public class FileSystemPluginFactoryLoader implements PluginFactoryLoader {
    @Override
    public PluginFactory load(SpiExtension spiExtension) {
        return newPluginFactory(spiExtension);
    }

    private static PluginFactory newPluginFactory(SpiExtension extensionConfig) {
        try {
            return EXTENSION_OBJECT_FACTORY.newInstance(extensionConfig.factory(), PluginFactory.class);
        } catch (Exception e) {
            throw new ConfigurationException(format("Could not load a plugin factory for configuration=%s", extensionConfig), e);
        }
    }
}
