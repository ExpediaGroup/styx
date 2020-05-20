/*
  Copyright (C) 2013-2020 Expedia Inc.

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
import com.fasterxml.jackson.databind.JsonNode;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.config.schema.Schema;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;
import com.hotels.styx.routing.RoutingObject;
import com.hotels.styx.routing.RoutingObjectReference;
import com.hotels.styx.routing.config.Builtins;
import com.hotels.styx.routing.config.RoutingObjectFactory;
import com.hotels.styx.routing.config.StyxObjectConfiguration;
import com.hotels.styx.routing.config.StyxObjectDefinition;
import com.hotels.styx.routing.config.StyxObjectReference;
import com.hotels.styx.server.NoServiceConfiguredException;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.hotels.styx.config.schema.SchemaDsl.field;
import static com.hotels.styx.config.schema.SchemaDsl.list;
import static com.hotels.styx.config.schema.SchemaDsl.object;
import static com.hotels.styx.config.schema.SchemaDsl.routingObject;
import static com.hotels.styx.config.schema.SchemaDsl.string;
import static com.hotels.styx.routing.config.RoutingConfigParser.toRoutingConfigNode;
import static com.hotels.styx.routing.config.RoutingSupport.missingAttributeError;
import static java.lang.String.join;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

/**
 * Makes a routing decision based on a request path prefix.
 * <p>
 * Chooses a destination according to longest matching path prefix.
 * The destination can be a routing object reference or an inline definition.
 */
public class PathPrefixRouter implements RoutingObject {
    private final PrefixRoute[] routes;

    PathPrefixRouter(PrefixRoute[] routes) {
        this.routes = routes;
    }

    @Override
    public Eventual<LiveHttpResponse> handle(LiveHttpRequest request, HttpInterceptor.Context context) {
        String path = request.path();

        for (PrefixRoute route : routes) {
            if (path.startsWith(route.prefix)) {
                return route.routingObject.handle(request, context);
            }
        }

        return Eventual.error(new NoServiceConfiguredException(path));
    }

    @Override
    public CompletableFuture<Void> stop() {
        CompletableFuture[] stopFutures = new CompletableFuture[routes.length];
        for (int i = 0; i < routes.length; i++) {
            stopFutures[i] = routes[i].routingObject.stop();
        }
        return CompletableFuture.allOf(stopFutures);
    }

    static class PrefixRoute implements Comparable<PrefixRoute> {
        private final String prefix;
        private final RoutingObject routingObject;

        PrefixRoute(String prefix, RoutingObject routingObject) {
            this.prefix = requireNonNull(prefix);
            this.routingObject = requireNonNull(routingObject);
        }

        @Override
        public int compareTo(@NotNull PrefixRoute o) {
            if (prefix.length() > o.prefix.length()) {
                return -1;
            } else if (prefix.length() < o.prefix.length()) {
                return 1;
            } else {
                return prefix.compareTo(o.prefix);
            }
        }
    }

    /**
     * A factory for constructing PathPrefixRouter objects.
     */
    public static class Factory implements RoutingObjectFactory {

        @Override
        public RoutingObject build(List<String> fullName, Context context, StyxObjectDefinition configBlock) {
            PathPrefixRouterConfig config = new JsonNodeConfig(configBlock.config()).as(PathPrefixRouterConfig.class);
            if (config.routes == null) {
                throw missingAttributeError(configBlock, join(".", fullName), "routes");
            }

            PrefixRoute[] routes = new PrefixRoute[config.routes().size()];
            int i = 0;
            for (PathPrefixConfig routeConfig : config.routes()) {
                String prefix = routeConfig.prefix();
                RoutingObject route = Builtins.build(singletonList(""), context, routeConfig.destination());
                routes[i++] = new PrefixRoute(prefix, route);
            }
            Arrays.sort(routes);

            return new PathPrefixRouter(routes);
        }
    }

    public static final Schema.FieldType SCHEMA = object(
            field("routes", list(object(
                    field("prefix", string()),
                    field("destination", routingObject()))
            ))
    );

    /**
     * PathPrefixRouter configuration.
     */
    public static class PathPrefixConfig {
        private final String prefix;
        private final StyxObjectConfiguration destination;

        public PathPrefixConfig(@JsonProperty("prefix") String prefix,
                                @JsonProperty("destination") JsonNode destination) {
            this.prefix = prefix;
            this.destination = toRoutingConfigNode(destination);
        }

        public String prefix() {
            return prefix;
        }

        public StyxObjectConfiguration destination() {
            return destination;
        }
    }

    /**
     * PathPrefixRouter configuration.
     */
    public static class PathPrefixRouterConfig {
        private final List<PathPrefixConfig> routes;

        public PathPrefixRouterConfig(@JsonProperty("routes") List<PathPrefixConfig> routes) {
            this.routes = routes;
        }

        public List<PathPrefixConfig> routes() {
            return routes;
        }
    }
}
