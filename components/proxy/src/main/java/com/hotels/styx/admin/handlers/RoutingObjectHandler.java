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
package com.hotels.styx.admin.handlers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.WebServiceHandler;
import com.hotels.styx.routing.RoutingMetadataDecorator;
import com.hotels.styx.routing.RoutingObjectRecord;
import com.hotels.styx.routing.config.Builtins;
import com.hotels.styx.routing.config.RoutingObjectFactory;
import com.hotels.styx.routing.config.StyxObjectDefinition;
import com.hotels.styx.routing.db.StyxObjectStore;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.hotels.styx.admin.handlers.UrlPatternRouter.placeholders;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST;
import static com.hotels.styx.api.HttpResponseStatus.CREATED;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.common.Collections.listOf;
import static com.hotels.styx.common.Collections.setOf;
import static com.hotels.styx.infrastructure.configuration.json.ObjectMappers.addStyxMixins;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Provides admin interface access to Styx routing configuration.
 */
public class RoutingObjectHandler implements WebServiceHandler {

    private static final ObjectMapper YAML_MAPPER = addStyxMixins(new ObjectMapper(new YAMLFactory()))
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(AUTO_CLOSE_SOURCE, true);

    private final UrlPatternRouter urlRouter;


    public RoutingObjectHandler(StyxObjectStore<RoutingObjectRecord> routeDatabase, RoutingObjectFactory.Context routingObjectFactoryContext) {
        urlRouter = new UrlPatternRouter.Builder()
                .get("/admin/routing/objects", (request, context) -> {
                    Map<String, RoutingObjectRecord> objects = routeDatabase.entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    String output = serialise(objects);

                    return Eventual.of(response(OK)
                            .body(output, UTF_8)
                            .build());
                })
                .get("/admin/routing/objects/:objectName", (request, context) -> {
                    String name = placeholders(context).get("objectName");

                    try {
                        String object = routeDatabase.get(name)
                                .map(RoutingObjectHandler::serialise)
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
                        StyxObjectDefinition payload = YAML_MAPPER.readValue(body, StyxObjectDefinition.class);
                        RoutingMetadataDecorator decorator = new RoutingMetadataDecorator(Builtins.build(listOf(name), routingObjectFactoryContext, payload));

                        routeDatabase.insert(name, new RoutingObjectRecord(payload.type(), setOf(payload.tags()), payload.config(), decorator))
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

    /**
     * Serializes either a single {@link com.hotels.styx.routing.config.StyxObjectDefinition} or
     * a collection of them.
     */
    private static String serialise(Object object) {
        JsonNode json = YAML_MAPPER
                .addMixIn(RoutingObjectRecord.class, RoutingObjectDefMixin.class)
                .valueToTree(object);

        try {
            return YAML_MAPPER.writeValueAsString(json);
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
        @JsonProperty("type")
        public abstract String type();

        @JsonProperty("tags")
        public abstract List<String> tags();

        @JsonProperty("config")
        public abstract JsonNode config();

        @JsonIgnore
        public abstract Object getRoutingObject();
    }

}

