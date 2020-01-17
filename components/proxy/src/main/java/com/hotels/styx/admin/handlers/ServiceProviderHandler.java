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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.WebServiceHandler;
import com.hotels.styx.api.extension.service.spi.StyxService;
import com.hotels.styx.routing.config.StyxObjectDefinition;
import com.hotels.styx.routing.db.StyxObjectStore;
import com.hotels.styx.StyxObjectRecord;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

import static com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.hotels.styx.admin.handlers.UrlPatternRouter.placeholders;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static com.hotels.styx.api.HttpResponseStatus.NO_CONTENT;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.infrastructure.configuration.json.ObjectMappers.addStyxMixins;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Admin interface access to service provider information, esp. Health Check monitors.
 */
public class ServiceProviderHandler implements WebServiceHandler {

    private static final Logger LOGGER = getLogger(ServiceProviderHandler.class);

    private static final ObjectMapper YAML_MAPPER = addStyxMixins(new ObjectMapper(new YAMLFactory()))
            .addMixIn(StyxObjectDefinition.class, ServiceProviderDefMixin.class)
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(AUTO_CLOSE_SOURCE, true);

    private final UrlPatternRouter urlRouter;

    public ServiceProviderHandler(StyxObjectStore<StyxObjectRecord<StyxService>> providerDatabase) {
        urlRouter = new UrlPatternRouter.Builder()
                .get("/admin/service/providers", (request, context) -> {
                    try {
                        String output = providerDatabase.entrySet()
                                .stream()
                                .map(entry -> serialise(entry.getKey(), entry.getValue()))
                                .collect(joining("\n"));

                        HttpResponse response = output.length() > 0
                                ? response(OK).body(output, UTF_8).build()
                                : response(NO_CONTENT).build();
                        return Eventual.of(response);
                    } catch (RuntimeException cause) {
                        LOGGER.error("Unable to construct response for list of service providers", cause);
                        return Eventual.of(response(BAD_REQUEST).body(cause.toString(), UTF_8).build());
                    }
                })
                .get("/admin/service/provider/:providerName", (request, context) -> {
                    String name = placeholders(context).get("providerName");

                    try {
                        Optional<String> object = providerDatabase.get(name)
                                .map(record -> serialise(name, record));

                        HttpResponse response = object.isPresent()
                                ? response(OK).body(object.get(), UTF_8).build()
                                : response(NOT_FOUND).build();
                        return Eventual.of(response);
                    } catch (RuntimeException cause) {
                        LOGGER.error("Unable to construct response for service provider [" + name + "]", cause);
                        return Eventual.of(response(BAD_REQUEST).body(cause.toString(), UTF_8).build());
                    }
                })
                .build();
    }

    @Override
    public Eventual<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        return urlRouter.handle(request, context);
    }

    public static ObjectMapper yamlMapper() {
        return YAML_MAPPER.copy();
    }

    private static String serialise(String name, StyxObjectRecord<StyxService> record) {
        List<String> tags = ImmutableList.copyOf(record.getTags());
        StyxObjectDefinition objectDef =
                new StyxObjectDefinition(name, record.getType(), tags, record.getConfig());

        JsonNode node = YAML_MAPPER.valueToTree(objectDef);
        ((ObjectNode) node).set("config", record.getConfig());

        try {
            return YAML_MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private abstract static class ServiceProviderDefMixin {
        @JsonProperty("name")
        public abstract String name();

        @JsonProperty("type")
        public abstract String type();

        @JsonProperty("tags")
        public abstract List<String> tags();
    }
}
