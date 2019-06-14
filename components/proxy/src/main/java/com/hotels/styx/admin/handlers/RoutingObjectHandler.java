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
package com.hotels.styx.admin.handlers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableSet;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.WebServiceHandler;
import com.hotels.styx.routing.RoutingObjectAdapter;
import com.hotels.styx.routing.RoutingObjectRecord;
import com.hotels.styx.routing.config.Builtins;
import com.hotels.styx.routing.config.RoutingObjectFactory;
import com.hotels.styx.routing.config.RoutingObjectDefinition;
import com.hotels.styx.routing.db.StyxObjectStore;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;

import static com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.hotels.styx.admin.handlers.UrlPatternRouter.placeholders;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST;
import static com.hotels.styx.api.HttpResponseStatus.CREATED;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.infrastructure.configuration.json.ObjectMappers.addStyxMixins;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Provides admin interface access to Styx routing configuration.
 */
public class RoutingObjectHandler implements WebServiceHandler {
    private static final Logger LOGGER = getLogger(RoutingObjectHandler.class);

    private static final ObjectMapper YAML_MAPPER = addStyxMixins(new ObjectMapper(new YAMLFactory()))
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(AUTO_CLOSE_SOURCE, true);

    private final UrlPatternRouter urlRouter;


    public RoutingObjectHandler(StyxObjectStore<RoutingObjectRecord> routeDatabase, RoutingObjectFactory.Context routingObjectFactoryContext) {
        urlRouter = new UrlPatternRouter.Builder()
                .get("/admin/routing/objects", (request, context) -> {
                    String output = routeDatabase.entrySet()
                            .stream()
                            .map(entry -> serialise(entry.getKey(), entry.getValue()))
                            .collect(joining("\n"));

                    return Eventual.of(response(OK)
                            .body(output, UTF_8)
                            .build());
                })
                .get("/admin/routing/objects/:objectName", (request, context) -> {
                    String name = placeholders(context).get("objectName");

                    try {
                        String object = routeDatabase.get(name)
                                .map(record -> serialise(name, record))
                                .orElseThrow(ResourceNotFoundException::new);

                        return Eventual.of(response(OK).body(object, UTF_8).build());
                    } catch (ResourceNotFoundException e) {
                        return Eventual.of(response(NOT_FOUND).build());
                    }
                })
                .put("/admin/routing/objects/:objectName", (request, context) -> {
                    String body = request.bodyAs(UTF_8);
                    String name = placeholders(context).get("objectName");

                    try {
                        RoutingObjectDefinition payload = YAML_MAPPER.readValue(body, RoutingObjectDefinition.class);
                        RoutingObjectAdapter adapter = new RoutingObjectAdapter(Builtins.build(emptyList(), routingObjectFactoryContext, payload));

                        routeDatabase.insert(name, new RoutingObjectRecord(payload.type(), ImmutableSet.copyOf(payload.tags()), payload.config(), adapter))
                                .ifPresent(previous -> previous.getRoutingObject().stop());

                        return Eventual.of(response(CREATED).build());
                    } catch (IOException | RuntimeException cause) {
                        return Eventual.of(response(BAD_REQUEST).body(cause.toString(), UTF_8).build());
                    }
                })
                .delete("/admin/routing/objects/:objectName", (request, context) -> {
                    String name = placeholders(context).get("objectName");

                    return routeDatabase.remove(name)
                            .map(previous -> previous.getRoutingObject().stop())
                            .map(previous -> Eventual.of(response(OK).build()))
                            .orElse(Eventual.of(response(NOT_FOUND).build()));
                })
                .build();
    }

    private static String serialise(String name, RoutingObjectRecord app) {
        JsonNode node = YAML_MAPPER
                .addMixIn(RoutingObjectDefinition.class, RoutingObjectDefMixin.class)
                .valueToTree(new RoutingObjectDefinition(name, app.getType(), emptyList(), app.getConfig()));

        ((ObjectNode) node).set("config", app.getConfig());

        try {
            return YAML_MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Eventual<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        return urlRouter.handle(request, context);
    }

    private static class ResourceNotFoundException extends RuntimeException {
    }

    private abstract static class RoutingObjectDefMixin {
        @JsonProperty("name")
        public abstract String name();

        @JsonProperty("type")
        public abstract String type();

        @JsonProperty("tags")
        public abstract List<String> tags();
    }

}

