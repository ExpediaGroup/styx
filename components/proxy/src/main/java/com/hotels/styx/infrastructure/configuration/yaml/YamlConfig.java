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
package com.hotels.styx.infrastructure.configuration.yaml;

import com.hotels.styx.api.Resource;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.ConversionException;
import com.hotels.styx.infrastructure.configuration.ConfigurationParser;
import com.hotels.styx.infrastructure.configuration.ConfigurationProvider;

import java.util.Map;
import java.util.Optional;

import static com.hotels.styx.infrastructure.configuration.yaml.YamlConfigurationFormat.YAML;
import static java.util.Collections.emptyMap;

/**
 * Yaml-based configuration object.
 */
public class YamlConfig implements Configuration {
    private final YamlConfiguration config;

    /**
     * Constructs an instance by parsing YAML from a string.
     *
     * @param yaml a YAML string
     */
    public YamlConfig(String yaml) {
        this(yaml, emptyMap());
    }

    public YamlConfig(String yaml, Map<String, String> overrides) {
       this.config = new ConfigurationParser.Builder<YamlConfiguration>()
               .format(YAML)
               .overrides(overrides)
               .build()
               .parse(ConfigurationProvider.from(yaml));
    }

    /**
     * Constructs an instance by parsing YAML from a resource.
     *
     * @param resource resource
     */
    public YamlConfig(Resource resource) {
        this(resource, emptyMap());
    }

    /**
     * Constructs an instance by parsing YAML from a resource. Parsed properties can be overridden by
     * supplying alternative values in the overrides map.
     *
     * @param resource  resource
     * @param overrides overrides
     */
    public YamlConfig(Resource resource, Map overrides) {
        this.config = new ConfigurationParser.Builder<YamlConfiguration>()
                .format(YAML)
                .overrides((Map<String, String>) overrides)
                .build()
                .parse(ConfigurationProvider.from(resource));
    }

    @Override
    public <X> Optional<X> get(String key, Class<X> type) {
        return config.get(key, type);
    }

    @Override
    public <X> X as(Class<X> type) throws ConversionException {
        return config.as(type);
    }
}
