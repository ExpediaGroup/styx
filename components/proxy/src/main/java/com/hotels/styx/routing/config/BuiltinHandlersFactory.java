/**
 * Copyright (C) 2013-2017 Expedia Inc.
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
package com.hotels.styx.routing.config;

import com.google.common.base.Preconditions;
import com.hotels.styx.api.HttpHandler2;

import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Builds a routing object based on its actual type.
 */
public class BuiltinHandlersFactory {
    private final Map<String, HttpHandlerFactory> builders;

    public BuiltinHandlersFactory(Map<String, HttpHandlerFactory> builders) {
        this.builders = requireNonNull(builders);
    }

    public HttpHandler2 build(List<String> parents, RoutingConfigNode configBlock) {
        if (configBlock instanceof RoutingConfigDefinition) {
            RoutingConfigDefinition block = (RoutingConfigDefinition) configBlock;
            String type = block.type();

            HttpHandlerFactory factory = builders.get(type);
            Preconditions.checkArgument(factory != null, format("Unknown handler type '%s'", type));

            return factory.build(parents, this, block);
        } else {
            throw new UnsupportedOperationException("Routing config node must be an config block, not a reference");
        }
    }
}
