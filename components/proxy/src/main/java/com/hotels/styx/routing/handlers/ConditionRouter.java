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
package com.hotels.styx.routing.handlers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;
import com.hotels.styx.proxy.RouteHandlerAdapter;
import com.hotels.styx.routing.config.HttpHandlerFactory;
import com.hotels.styx.routing.config.RouteHandlerConfig;
import com.hotels.styx.routing.config.RouteHandlerDefinition;
import com.hotels.styx.routing.config.RouteHandlerFactory;
import com.hotels.styx.server.HttpRouter;
import com.hotels.styx.server.routing.AntlrMatcher;
import com.hotels.styx.server.routing.antlr.DslFunctionResolutionError;
import com.hotels.styx.server.routing.antlr.DslSyntaxError;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY;
import static com.hotels.styx.routing.config.RoutingConfigParser.toRoutingConfigNode;
import static com.hotels.styx.routing.config.RoutingSupport.append;
import static com.hotels.styx.routing.config.RoutingSupport.missingAttributeError;
import static java.lang.String.format;
import static java.lang.String.join;
import com.hotels.styx.api.HttpRequest;

/**
 * Condition predicate based HTTP router.
 */
public class ConditionRouter implements HttpRouter {
    private final List<Route> routes;
    private final HttpHandler fallback;

    private ConditionRouter(List<Route> routes, HttpHandler fallback) {
        this.routes = routes;
        this.fallback = fallback;
    }

    @Override
    public Optional<HttpHandler> route(HttpRequest request) {
        for (Route route : routes) {
            HttpHandler handler = route.match(request);
            if (handler != null) {
                return Optional.of(handler);
            }
        }

        return Optional.ofNullable(fallback);
    }

    private static class Route {
        private final AntlrMatcher matcher;
        private final HttpHandler handler;

        Route(String condition, HttpHandler handler) {
            this.matcher = AntlrMatcher.antlrMatcher(condition);
            this.handler = handler;
        }

        public HttpHandler match(HttpRequest request) {
            return matcher.apply(request) ? handler : null;
        }
    }

    /**
     * Builds a condition router from the yaml routing configuration.
     */
    public static class ConfigFactory implements HttpHandlerFactory {
        private static class ConditionRouterConfig {
            private final List<ConditionRouterRouteConfig> routes;
            private final RouteHandlerConfig fallback;

            private ConditionRouterConfig(@JsonProperty("routes") List<ConditionRouterRouteConfig> routes,
                                          @JsonProperty("fallback") JsonNode fallback) {
                this.routes = routes;
                this.fallback = fallback.isNull() ? null : toRoutingConfigNode(fallback);
            }

        }

        private static class ConditionRouterRouteConfig {
            private final String condition;
            private final RouteHandlerConfig destination;

            public ConditionRouterRouteConfig(@JsonProperty("condition") String condition,
                                              @JsonProperty("destination") JsonNode destination) {
                this.condition = condition;
                this.destination = toRoutingConfigNode(destination);
            }
        }

        public HttpHandler build(List<String> parents,
                                  RouteHandlerFactory routeHandlerFactory,
                                  RouteHandlerDefinition configBlock
        ) {
            ConditionRouterConfig config = new JsonNodeConfig(configBlock.config()).as(ConditionRouterConfig.class);
            if (config.routes == null) {
                throw missingAttributeError(configBlock, join(".", parents), "routes");
            }

            AtomicInteger index = new AtomicInteger(0);
            List<Route> routes = config.routes.stream()
                    .map(routeConfig -> buildRoute(append(parents, "routes"), routeHandlerFactory, index.getAndIncrement(), routeConfig.condition, routeConfig.destination))
                    .collect(Collectors.toList());

            return new RouteHandlerAdapter(new ConditionRouter(routes, buildFallbackHandler(parents, routeHandlerFactory, config)));
        }

        private static HttpHandler buildFallbackHandler(List<String> parents, RouteHandlerFactory routeHandlerFactory, ConditionRouterConfig config) {
            if (config.fallback == null) {
                return (request, context) -> StyxObservable.of(HttpResponse.response(BAD_GATEWAY).build());
            } else {
                return routeHandlerFactory.build(append(parents, "fallback"), config.fallback);
            }
        }

        private static Route buildRoute(List<String> parents, RouteHandlerFactory routeHandlerFactory, int index, String condition, RouteHandlerConfig destination) {
            try {
                String attribute = format("destination[%d]", index);
                HttpHandler handler = routeHandlerFactory.build(append(parents, attribute), destination);
                return new Route(condition, handler);
            } catch (DslSyntaxError | DslFunctionResolutionError e) {
                String attribute = format("condition[%d]", index);
                String path = join(".", append(parents, attribute));
                String msg = format("Routing object definition of type 'ConditionRouter', attribute='%s', failed to compile routing expression condition='%s'", path, condition);
                throw new IllegalArgumentException(msg, e);
            }
        }

    }
}
