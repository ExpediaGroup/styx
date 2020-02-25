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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.WebServiceHandler;
import com.hotels.styx.routing.RoutingMetadataDecorator;
import com.hotels.styx.routing.RoutingObject;
import com.hotels.styx.routing.RoutingObjectRecord;
import com.hotels.styx.routing.RoutingObjectYamlRecord;
import com.hotels.styx.routing.config.Builtins;
import com.hotels.styx.routing.config2.StyxObject;
import com.hotels.styx.routing.db.StyxObjectStore;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.hotels.styx.admin.handlers.UrlPatternRouter.placeholders;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST;
import static com.hotels.styx.api.HttpResponseStatus.CREATED;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.routing.handlers2.SerialisersKt.objectMmapper;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Provides admin interface access to Styx routing configuration.
 */
public class RoutingObjectHandler implements WebServiceHandler {
    private static final Logger LOGGER = getLogger(RoutingObjectHandler.class);

    private final UrlPatternRouter urlRouter;


    public RoutingObjectHandler(
            StyxObjectStore<RoutingObjectRecord<RoutingObject>> routeDatabase,
            StyxObject.Context routingObjectFactoryContext,
            Map<String, Builtins.StyxObjectDescriptor<StyxObject<RoutingObject>>> routingObjectDescriptors) {

        urlRouter = new UrlPatternRouter.Builder()
                .get("/admin/routing/objects", (request, context) -> {
                    String output = routeDatabase.entrySet()
                            .stream()
                            .map(entry -> serialise(entry.getValue(), routingObjectDescriptors))
                            .collect(joining("\n"));

                    return Eventual.of(response(OK)
                            .body(output, UTF_8)
                            .build());
                })
                .get("/admin/routing/objects/:objectName", (request, context) -> {
                    String name = placeholders(context).get("objectName");

                    try {
                        String object = routeDatabase.get(name)
                                .map(record -> serialise(record, routingObjectDescriptors))
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
                        ObjectMapper mapper = objectMmapper(routingObjectDescriptors);

                        RoutingObjectYamlRecord<RoutingObject> payload = mapper.readValue(
                                body,
                                new TypeReference<RoutingObjectYamlRecord<RoutingObject>>() {
                                });

                        routeDatabase.insert(name, new RoutingObjectRecord<>(
                                payload.getType(),
                                payload.getTags(),
                                payload.getConfig(),
                                new RoutingMetadataDecorator(payload.getConfig().build(routingObjectFactoryContext))))
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

    private static String serialise(RoutingObjectRecord<RoutingObject> record,
                                    Map<String, Builtins.StyxObjectDescriptor<StyxObject<RoutingObject>>> routingObjectDescriptors) {
        ObjectMapper mapper = objectMmapper(routingObjectDescriptors);
        try {
            return mapper.writeValueAsString(record);
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

