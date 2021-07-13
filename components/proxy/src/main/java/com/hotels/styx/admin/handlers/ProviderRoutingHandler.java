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
package com.hotels.styx.admin.handlers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.hotels.styx.StyxObjectRecord;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.WebServiceHandler;
import com.hotels.styx.api.configuration.ObjectStore;
import com.hotels.styx.api.extension.service.spi.StyxService;
import com.hotels.styx.common.http.handler.HttpStreamer;
import com.hotels.styx.routing.db.StyxObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.hotels.styx.admin.handlers.UrlPatternRouter.placeholders;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.infrastructure.configuration.json.ObjectMappers.addStyxMixins;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Routes admin requests to the admin endpoints of each {@link com.hotels.styx.api.extension.service.spi.StyxService}
 * in the Provider {@link ObjectStore}, and to the index page that organizes and lists these endpoints.
 * This handler registers as a watcher on the store, and will keep the endpoint list up to date as the store data changes.
 */
public class ProviderRoutingHandler implements WebServiceHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProviderRoutingHandler.class);
    private static final int MEGABYTE = 1024 * 1024;
    private static final ObjectMapper YAML_MAPPER = addStyxMixins(new ObjectMapper(new YAMLFactory()))
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(AUTO_CLOSE_SOURCE, true);

    private final String pathPrefix;
    private volatile UrlPatternRouter router;

    /**
     * Create a new handler for the given provider object store, with provider admin URLs mounted
     * under the path "pathPrefix/providerName".
     * @param pathPrefix the path prefix added to each provider admin URL
     * @param providerDb the provider object store
     */
    public ProviderRoutingHandler(String pathPrefix, StyxObjectStore<? extends StyxObjectRecord<? extends StyxService>> providerDb) {
        this.pathPrefix = pathPrefix;
        Flux.from(providerDb.watch()).subscribe(
                this::refreshRoutes,
                error -> LOG.error("Error in providerDB subscription", error));
    }

    @Override
    public Eventual<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        return router.handle(request, context);
    }

    private void refreshRoutes(ObjectStore<? extends StyxObjectRecord<? extends StyxService>> db) {
        LOG.debug("Refreshing provider admin endpoint routes");
        router = buildRouter(db);
    }

    private UrlPatternRouter buildRouter(ObjectStore<? extends StyxObjectRecord<? extends StyxService>> db) {
        UrlPatternRouter.Builder routeBuilder = new UrlPatternRouter.Builder(pathPrefix)
            .get("", new ProviderListHandler(db))
            .get("objects", (request, context) -> handleRequestForAllObjects(db))
            .get("objects/:objectName", (request, context) -> {
                String name = placeholders(context).get("objectName");
                return handleRequestForOneObject(db, name);
            });

        db.entrySet().forEach(entry -> {
            String providerName = entry.getKey();
            entry.getValue().getStyxService().adminInterfaceHandlers(pathPrefix + "/" + providerName)
                .forEach((relPath, handler) ->
                    routeBuilder.get(providerName + "/" + relPath, new HttpStreamer(MEGABYTE, handler))
                );
        });

        return routeBuilder.build();
    }

    private Eventual<HttpResponse> handleRequestForAllObjects(ObjectStore<? extends StyxObjectRecord<? extends StyxService>> db) {
        Map<String, StyxObjectRecord> objects = db.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        String output = serialise(objects);

        return Eventual.of(response(OK)
                .body(output, UTF_8)
                .build());
    }

    private Eventual<HttpResponse> handleRequestForOneObject(ObjectStore<? extends StyxObjectRecord<? extends StyxService>> db,
                                                             String name) {
        try {
            String object = db.get(name)
                    .map(ProviderRoutingHandler::serialise)
                    .orElseThrow(ProviderRoutingHandler.ResourceNotFoundException::new);

            return Eventual.of(response(OK).body(object, UTF_8).build());
        } catch (ProviderRoutingHandler.ResourceNotFoundException e) {
            return Eventual.of(response(NOT_FOUND).build());
        }
    }

    /**
     * Serializes either a single {@link com.hotels.styx.routing.config.StyxObjectDefinition} or
     * a collection of them.
     */
    private static String serialise(Object object) {
        JsonNode json = YAML_MAPPER
                .addMixIn(StyxObjectRecord.class, ProviderObjectDefMixin.class)
                .valueToTree(object);

        try {
            return YAML_MAPPER.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static class ResourceNotFoundException extends RuntimeException {
    }

    private abstract static class ProviderObjectDefMixin {

        @JsonProperty("type")
        public abstract String type();

        @JsonProperty("tags")
        public abstract List<String> tags();

        @JsonProperty("config")
        public abstract JsonNode config();

        @JsonIgnore
        public abstract Object getStyxService();
    }


}
