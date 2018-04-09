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
package com.hotels.styx.config;

import com.hotels.styx.infrastructure.configuration.ConfigurationParser;
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfiguration;

import java.util.Map;

import static com.hotels.styx.infrastructure.configuration.ConfigurationSource.configSource;
import static com.hotels.styx.infrastructure.configuration.yaml.YamlConfigurationFormat.YAML;
import static java.util.Collections.emptyMap;

public final class Config {
    private Config() {
    }

    public static YamlConfiguration config(String string) {
        return parser(emptyMap())
                .parse(configSource(string));
    }

    private static ConfigurationParser<YamlConfiguration> parser(Map overrides) {
        return new ConfigurationParser.Builder<YamlConfiguration>()
                .overrides(overrides)
                .format(YAML)
                .build();
    }
}
