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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.routing.config.BuiltinInterceptorsFactory;
import com.hotels.styx.routing.config.HttpHandlerFactory;
import com.hotels.styx.routing.config.RoutingObjectConfiguration;
import com.hotels.styx.routing.config.RoutingObjectDefinition;
import com.hotels.styx.routing.config.RoutingObjectReference;
import com.hotels.styx.server.track.CurrentRequestTracker;
import com.hotels.styx.server.track.RequestTracker;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hotels.styx.routing.config.RoutingSupport.append;
import static com.hotels.styx.routing.config.RoutingSupport.missingAttributeError;
import static java.lang.String.join;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.StreamSupport.stream;

/**
 * A HTTP handler that contains HTTP interceptor pipeline.
 */
public class HttpInterceptorPipeline implements HttpHandler {
    private final StandardHttpPipeline handler;

    public HttpInterceptorPipeline(List<HttpInterceptor> interceptors, HttpHandler handler, boolean trackRequests) {
        RequestTracker tracker = trackRequests ? CurrentRequestTracker.INSTANCE : RequestTracker.NO_OP;
        this.handler = new StandardHttpPipeline(interceptors, handler, tracker);
    }

    @Override
    public Eventual<LiveHttpResponse> handle(LiveHttpRequest request, HttpInterceptor.Context context) {
        return handler.handle(request, context);
    }

    /**
     * An yaml config based builder for HttpInterceptorPipeline.
     */
    public static class Factory implements HttpHandlerFactory {

        private static List<RoutingObjectConfiguration> styxHttpPipeline(JsonNode pipeline) {
            return stream(pipeline.spliterator(), false)
                    .map(Factory::toRoutingConfigNode)
                    .collect(Collectors.toList());
        }

        private static RoutingObjectConfiguration toRoutingConfigNode(JsonNode jsonNode) {
            if (jsonNode.getNodeType() == JsonNodeType.STRING) {
                return new RoutingObjectReference(jsonNode.asText());
            } else if (jsonNode.getNodeType() == JsonNodeType.OBJECT) {
                String name = ofNullable(jsonNode.get("name"))
                        .map(JsonNode::asText)
                        .orElse("");
                String type = requireNonNull(jsonNode.get("type").asText());
                JsonNode conf = jsonNode.get("config");
                return new RoutingObjectDefinition(name, type, conf);
            }
            throw new IllegalArgumentException("Invalid configuration. Expected a reference (string) or a configuration block.");
        }

        @Override
        public HttpHandler build(List<String> parents, Context context, RoutingObjectDefinition configBlock) {
            JsonNode pipeline = configBlock.config().get("pipeline");
            List<HttpInterceptor> interceptors = getHttpInterceptors(append(parents, "pipeline"), toMap(context.plugins()), context.builtinInterceptorsFactory(), pipeline);

            RoutingObjectDefinition handlerConfig = new JsonNodeConfig(configBlock.config())
                    .get("handler", RoutingObjectDefinition.class)
                    .orElseThrow(() -> missingAttributeError(configBlock, join(".", parents), "handler"));

            return new HttpInterceptorPipeline(
                    interceptors,
                    context.factory().build(append(parents, "handler"), handlerConfig),
                    context.requestTracking());
        }

        private List<HttpInterceptor> getHttpInterceptors(
                List<String> parents,
                Map<String, NamedPlugin> plugins,
                BuiltinInterceptorsFactory interceptorFactory,
                JsonNode pipeline) {
            if (pipeline == null || pipeline.isNull()) {
                return ImmutableList.of();
            }
            List<RoutingObjectConfiguration> interceptorConfigs = styxHttpPipeline(pipeline);
            ensureValidPluginReferences(parents, plugins, interceptorConfigs);
            return interceptorConfigs.stream()
                    .map(node -> {
                        if (node instanceof RoutingObjectReference) {
                            String name = ((RoutingObjectReference) node).name();
                            return plugins.get(name);
                        } else {
                            RoutingObjectDefinition block = (RoutingObjectDefinition) node;
                            return interceptorFactory.build(block);
                        }
                    })
                    .collect(Collectors.toList());
        }

        private void ensureValidPluginReferences(List<String> parents, Map<String, NamedPlugin> plugins, List<RoutingObjectConfiguration> interceptors) {
            interceptors.forEach(node -> {
                if (node instanceof RoutingObjectReference) {
                    String name = ((RoutingObjectReference) node).name();
                    if (!plugins.containsKey(name)) {
                        throw new IllegalArgumentException(String.format("No such plugin or interceptor exists, attribute='%s', name='%s'",
                                join(".", parents), name));
                    }
                }
            });
        }

        private Map<String, NamedPlugin> toMap(Iterable<NamedPlugin> plugins) {
            return stream(plugins.spliterator(), false)
                    .collect(Collectors.toMap(NamedPlugin::name, identity()));
        }
    }

}
