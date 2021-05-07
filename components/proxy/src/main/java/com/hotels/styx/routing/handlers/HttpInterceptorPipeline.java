/*
  Copyright (C) 2013-2021 Expedia Inc.

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
import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.config.schema.Schema;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.routing.RoutingObject;
import com.hotels.styx.routing.config.Builtins;
import com.hotels.styx.routing.config.HttpInterceptorFactory;
import com.hotels.styx.routing.config.RoutingObjectFactory;
import com.hotels.styx.routing.config.RoutingConfigParser;
import com.hotels.styx.routing.config.StyxObjectConfiguration;
import com.hotels.styx.routing.config.StyxObjectDefinition;
import com.hotels.styx.routing.config.StyxObjectReference;
import com.hotels.styx.server.track.CurrentRequestTracker;
import com.hotels.styx.server.track.RequestTracker;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.hotels.styx.config.schema.SchemaDsl.field;
import static com.hotels.styx.config.schema.SchemaDsl.list;
import static com.hotels.styx.config.schema.SchemaDsl.object;
import static com.hotels.styx.config.schema.SchemaDsl.optional;
import static com.hotels.styx.config.schema.SchemaDsl.or;
import static com.hotels.styx.config.schema.SchemaDsl.routingObject;
import static com.hotels.styx.config.schema.SchemaDsl.string;
import static com.hotels.styx.routing.config.RoutingConfigParser.toRoutingConfigNode;
import static com.hotels.styx.routing.config.RoutingSupport.append;
import static com.hotels.styx.routing.config.RoutingSupport.missingAttributeError;
import static java.lang.String.join;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.StreamSupport.stream;

/**
 * A HTTP handler that contains HTTP interceptor pipeline.
 */
public class HttpInterceptorPipeline implements RoutingObject {
    public static final Schema.FieldType SCHEMA = object(
            optional("pipeline", or(string(), list(string()))),
            field("handler", routingObject())
    );

    private final RoutingObject handler;
    private final StandardHttpPipeline pipeline;

    // todo find out where this is called from that does not instrument the plugins
    public HttpInterceptorPipeline(List<HttpInterceptor> interceptors, RoutingObject handler, boolean trackRequests) {
        RequestTracker tracker = trackRequests ? CurrentRequestTracker.INSTANCE : RequestTracker.NO_OP;
        this.handler = requireNonNull(handler);
        this.pipeline = new StandardHttpPipeline(interceptors, handler, tracker);
    }

    @Override
    public Eventual<LiveHttpResponse> handle(LiveHttpRequest request, HttpInterceptor.Context context) {
        return pipeline.handle(request, context);
    }

    @Override
    public CompletableFuture<Void> stop() {
        return handler.stop();
    }

    /**
     * An yaml config based builder for HttpInterceptorPipeline.
     */
    public static class Factory implements RoutingObjectFactory {

        // Note: `pipeline` has to represent a JSON array:
        @Override
        public RoutingObject build(List<String> fullName, Context context, StyxObjectDefinition configBlock) {
            JsonNode pipeline = configBlock.config().get("pipeline");
            List<HttpInterceptor> interceptors = getHttpInterceptors(append(fullName, "pipeline"), toMap(context.plugins()), context.interceptorFactories(), pipeline);

            JsonNode handlerConfig = new JsonNodeConfig(configBlock.config())
                    .get("handler", JsonNode.class)
                    .orElseThrow(() -> missingAttributeError(configBlock, join(".", fullName), "handler"));

            String classes = interceptors.stream().map(it -> it.getClass().getSimpleName()).collect(Collectors.joining(","));
            LoggerFactory.getLogger(getClass()).info(">>> HttpInterceptorPipeline is used: "+classes);

            return new HttpInterceptorPipeline(
                    interceptors,
                    Builtins.build(append(fullName, "handler"), context, toRoutingConfigNode(handlerConfig)),
                    context.requestTracking());
        }

        private static List<HttpInterceptor> getHttpInterceptors(
                List<String> parents,
                Map<String, NamedPlugin> plugins,
                Map<String, HttpInterceptorFactory> interceptorFactories,
                JsonNode pipeline) {
            if (pipeline == null || pipeline.isNull()) {
                return ImmutableList.of();
            }
            List<StyxObjectConfiguration> interceptorConfigs = styxHttpPipeline(pipeline);
            ensureValidPluginReferences(parents, plugins, interceptorConfigs);
            return interceptorConfigs.stream()
                    .map(node -> {
                        if (node instanceof StyxObjectReference) {
                            String name = ((StyxObjectReference) node).name();
                            return plugins.get(name);
                        } else {
                            StyxObjectDefinition block = (StyxObjectDefinition) node;
                            return Builtins.build(block, interceptorFactories);
                        }
                    })
                    .collect(Collectors.toList());
        }

        private static List<StyxObjectConfiguration> styxHttpPipeline(JsonNode pipeline) {
            if (pipeline.isTextual()) {
                String[] names = pipeline.textValue().split(",");
                return Arrays.stream(names)
                        .map(String::trim)
                        .filter(it -> !it.isEmpty())
                        .map(StyxObjectReference::new)
                        .collect(Collectors.toList());

            } else {
                return stream(pipeline.spliterator(), false)
                        .map(RoutingConfigParser::toRoutingConfigNode)
                        .collect(Collectors.toList());
            }
        }

        private static void ensureValidPluginReferences(List<String> parents, Map<String, NamedPlugin> plugins, List<StyxObjectConfiguration> interceptors) {
            interceptors.forEach(node -> {
                if (node instanceof StyxObjectReference) {
                    String name = ((StyxObjectReference) node).name();
                    if (!plugins.containsKey(name)) {
                        throw new IllegalArgumentException(String.format("No such plugin or interceptor exists, attribute='%s', name='%s'",
                                join(".", parents), name));
                    }
                }
            });
        }

        private static Map<String, NamedPlugin> toMap(Iterable<NamedPlugin> plugins) {
            return stream(plugins.spliterator(), false)
                    .collect(Collectors.toMap(NamedPlugin::name, identity()));
        }
    }

}
