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
package com.hotels.styx.startup;

import com.hotels.styx.StartupConfig;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.infrastructure.configuration.ConfigurationParser;
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfiguration;

import static com.hotels.styx.api.io.ResourceFactory.newResource;
import static com.hotels.styx.infrastructure.configuration.ConfigurationSource.configSource;
import static com.hotels.styx.infrastructure.configuration.yaml.YamlConfigurationFormat.YAML;

/**
 * Methods for acquiring configuration from provided config files.
 */
public final class ConfigFileLoading {
    private ConfigFileLoading() {
    }

    public static Configuration configurationFromFile(StartupConfig startupConfig, String fallbackPath) {
        return new ConfigurationParser.Builder<YamlConfiguration>()
                .format(YAML)
                .overrides(System.getProperties())
                .fallbackConfigSource(configSource(newResource(fallbackPath)))
                .build()
                .parse(configSource(startupConfig.configFileLocation()));
    }
}
