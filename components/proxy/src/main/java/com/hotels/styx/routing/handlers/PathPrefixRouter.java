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
package com.hotels.styx.routing.handlers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.common.Pair;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;
import com.hotels.styx.routing.RoutingObjectRecord;
import com.hotels.styx.routing.config.HttpHandlerFactory;
import com.hotels.styx.routing.config.RoutingObjectDefinition;
import com.hotels.styx.routing.config.RoutingObjectReference;
import com.hotels.styx.server.NoServiceConfiguredException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;

import static com.hotels.styx.common.Pair.pair;
import static com.hotels.styx.routing.config.RoutingSupport.missingAttributeError;
import static java.lang.String.join;
import static java.util.Comparator.comparingInt;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.toList;

public class PathPrefixRouter {
    private final ConcurrentSkipListMap<String, String> routes = new ConcurrentSkipListMap<>(
                    comparingInt(String::length)
                            .reversed()
                            .thenComparing(naturalOrder())
    );

    PathPrefixRouter(List<Pair<String, String>> routes) {
        routes.forEach(entry -> this.routes.put(entry.key(), entry.value()));
    }

    public Optional<RoutingObjectReference> route(LiveHttpRequest request) {
        String path = request.path();

        return routes.entrySet().stream()
                .filter(entry -> path.startsWith(entry.getKey()))
                .findFirst()
                .map(Map.Entry::getValue)
                .map(RoutingObjectReference::new);
    }

    public static class Factory implements HttpHandlerFactory {

        @Override
        public HttpHandler build(List<String> parents, Context context, RoutingObjectDefinition configBlock) {
            PathPrefixRouterConfig config = new JsonNodeConfig(configBlock.config()).as(PathPrefixRouterConfig.class);
            if (config.routes == null) {
                throw missingAttributeError(configBlock, join(".", parents), "routes");
            }

            PathPrefixRouter pathPrefixRouter = new PathPrefixRouter(
                    config.routes.stream()
                            .map(route -> pair(route.prefix, route.destination))
                            .collect(toList())
            );

            // TODO: Tidy this up:
            return (request, ctx) -> pathPrefixRouter
                    .route(request)
                    .flatMap(reference -> context.routeDb().get(reference.name()))
                    .map(RoutingObjectRecord::getHandler)

                    .map(handler -> handler.handle(request, ctx))
                    .orElse(Eventual.error(new NoServiceConfiguredException(request.path())));
        }

        private static class PathPrefixConfig {
            private final String prefix;
            private final String destination;

            public PathPrefixConfig(@JsonProperty("prefix") String prefix,
                                    @JsonProperty("destination") String destination) {
                this.prefix = prefix;
                this.destination = destination;
            }
        }

        private static class PathPrefixRouterConfig {
            private final List<PathPrefixConfig> routes;

            public PathPrefixRouterConfig(@JsonProperty("routes") List<PathPrefixConfig> routes) {
                this.routes = routes;
            }
        }
    }

}
