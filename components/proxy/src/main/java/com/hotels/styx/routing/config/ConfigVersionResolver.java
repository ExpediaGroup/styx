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
package com.hotels.styx.routing.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.hotels.styx.StyxConfig;

import static com.hotels.styx.routing.config.ConfigVersionResolver.Version.ROUTING_CONFIG_V1;
import static com.hotels.styx.routing.config.ConfigVersionResolver.Version.ROUTING_CONFIG_V2;
import static java.util.Objects.requireNonNull;

/**
 * Works out the Styx configuration version for backwards compatibility purposes.
 */
public class ConfigVersionResolver {
    private final StyxConfig config;

    /**
     * Routing configuration version.
     */
    public enum Version {
        ROUTING_CONFIG_V1,
        ROUTING_CONFIG_V2
    };

    public ConfigVersionResolver(StyxConfig config) {
        this.config = requireNonNull(config);
    }

    public Version version() {
        if (config.get("httpPipeline", JsonNode.class).isPresent()) {
            return ROUTING_CONFIG_V2;
        } else {
            return ROUTING_CONFIG_V1;
        }
    }
}
