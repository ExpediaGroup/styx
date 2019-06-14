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
import com.fasterxml.jackson.databind.JsonNode;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;
import com.hotels.styx.proxy.RouteHandlerAdapter;
import com.hotels.styx.config.schema.Schema;
import com.hotels.styx.routing.RoutingObject;
import com.hotels.styx.routing.config.Builtins;
import com.hotels.styx.routing.config.RoutingObjectFactory;
import com.hotels.styx.routing.config.RoutingObjectConfiguration;
import com.hotels.styx.routing.config.RoutingObjectDefinition;
import com.hotels.styx.server.HttpRouter;
import com.hotels.styx.server.routing.AntlrMatcher;
import com.hotels.styx.server.routing.antlr.DslFunctionResolutionError;
import com.hotels.styx.server.routing.antlr.DslSyntaxError;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY;
import static com.hotels.styx.routing.config.RoutingConfigParser.toRoutingConfigNode;
import static com.hotels.styx.config.schema.SchemaDsl.field;
import static com.hotels.styx.config.schema.SchemaDsl.list;
import static com.hotels.styx.config.schema.SchemaDsl.object;
import static com.hotels.styx.config.schema.SchemaDsl.optional;
import static com.hotels.styx.config.schema.SchemaDsl.routingObject;
import static com.hotels.styx.config.schema.SchemaDsl.string;
import static com.hotels.styx.routing.config.RoutingSupport.append;
import static com.hotels.styx.routing.config.RoutingSupport.missingAttributeError;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * Condition predicate based HTTP router.
 */
public class ConditionRouter implements HttpRouter {
    public static final Schema.FieldType SCHEMA = object(
            field("routes", list(object(
                    field("condition", string()),
                    field("destination", routingObject()))
            )),
            optional("fallback", routingObject())
    );

    private final List<Route> routes;
    private final RoutingObject fallback;

    private ConditionRouter(List<Route> routes, RoutingObject fallback) {
        this.routes = routes;
        this.fallback = fallback;
    }

    @Override
    public Optional<HttpHandler> route(LiveHttpRequest request, HttpInterceptor.Context context) {
        for (Route route : routes) {
            HttpHandler handler = route.match(request, context);
            if (handler != null) {
                return Optional.of(handler);
            }
        }

        return Optional.ofNullable(fallback);
    }

    /**
     * Builds a condition router from the yaml routing configuration.
     */
    public static class Factory implements RoutingObjectFactory {

        private static RoutingObject buildFallbackHandler(
                List<String> parents,
                Context context,
                ConditionRouterConfig config) {
            if (config.fallback == null) {
                return (request, na) -> Eventual.of(LiveHttpResponse.response(BAD_GATEWAY).build());
            } else {
                return Builtins.build(append(parents, "fallback"), context, config.fallback);
            }
        }

        private static Route buildRoute(
                List<String> parents,
                Context context,
                int index,
                String condition,
                RoutingObjectConfiguration destination) {
            try {
                String attribute = format("destination[%d]", index);
                RoutingObject handler = Builtins.build(append(parents, attribute), context, destination);
                return new Route(condition, handler);
            } catch (DslSyntaxError | DslFunctionResolutionError e) {
                String attribute = format("condition[%d]", index);
                String path = join(".", append(parents, attribute));
                String msg = format("Routing object definition of type 'ConditionRouter', attribute='%s', failed to compile routing expression condition='%s'", path, condition);
                throw new IllegalArgumentException(msg, e);
            }
        }

        @Override
        public RoutingObject build(List<String> fullName, Context context, RoutingObjectDefinition configBlock) {
            ConditionRouterConfig config = new JsonNodeConfig(configBlock.config()).as(ConditionRouterConfig.class);
            if (config.routes == null) {
                throw missingAttributeError(configBlock, join(".", fullName), "routes");
            }

            AtomicInteger index = new AtomicInteger(0);
            List<Route> routes = config.routes.stream()
                    .map(routeConfig -> buildRoute(
                            append(fullName, "routes"),
                            context,
                            index.getAndIncrement(),
                            routeConfig.condition,
                            routeConfig.destination))
                    .collect(Collectors.toList());

            RoutingObject fallbackHandler = buildFallbackHandler(fullName, context, config);

            ConditionRouter router = new ConditionRouter(routes, fallbackHandler);

            return new RouteHandlerAdapter(router) {
                @Override
                public CompletableFuture<Void> stop() {
                    fallbackHandler.stop();

                    routes.stream()
                            .map(route -> route.routingObject)
                            .forEach(RoutingObject::stop);

                    return completedFuture(null);
                }
            };
        }

        private static class ConditionRouterConfig {
            private final List<ConditionRouterRouteConfig> routes;
            private final RoutingObjectConfiguration fallback;

            private ConditionRouterConfig(@JsonProperty("routes") List<ConditionRouterRouteConfig> routes,
                                          @JsonProperty("fallback") JsonNode fallback) {
                this.routes = routes;
                this.fallback = fallback.isNull() ? null : toRoutingConfigNode(fallback);
            }
        }

        private static class ConditionRouterRouteConfig {
            private final String condition;
            private final RoutingObjectConfiguration destination;

            public ConditionRouterRouteConfig(@JsonProperty("condition") String condition,
                                              @JsonProperty("destination") JsonNode destination) {
                this.condition = condition;
                this.destination = toRoutingConfigNode(destination);
            }
        }
    }

    private static class Route {
        private final AntlrMatcher matcher;
        private final RoutingObject routingObject;

        Route(String condition, RoutingObject routingObject) {
            this.matcher = AntlrMatcher.antlrMatcher(condition);
            this.routingObject = routingObject;
        }

        public HttpHandler match(LiveHttpRequest request, HttpInterceptor.Context context) {
            return matcher.apply(request, context) ? routingObject : null;
        }
    }

}
