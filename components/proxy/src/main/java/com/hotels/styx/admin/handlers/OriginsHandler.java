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
package com.hotels.styx.admin.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.common.http.handler.BaseHttpHandler;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.Registry;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.hotels.styx.api.FullHttpResponse.response;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.infrastructure.configuration.json.ObjectMappers.addStyxMixins;
import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * Provides origins configuration in the form of JSON.
 */
public class OriginsHandler extends BaseHttpHandler {
    private final ObjectMapper mapper = addStyxMixins(new ObjectMapper().setSerializationInclusion(NON_NULL));

    private final Registry<BackendService> backendServicesRegistry;

    public OriginsHandler(Registry<BackendService> backendServicesRegistry) {
        this.backendServicesRegistry = requireNonNull(backendServicesRegistry, "backendServicesRegistry cannot be null");
    }

    @Override
    protected HttpResponse doHandle(HttpRequest request) {
        Iterable<BackendService> backendServices = backendServicesRegistry.get();

        return jsonResponse(backendServices, isPrettyPrint(request));
    }

    private HttpResponse jsonResponse(Object object, boolean prettyPrint) {
        try {
            String jsonContent = marshal(object, prettyPrint);
            return response(OK)
                    .disableCaching()
                    .addHeader(CONTENT_TYPE, JSON_UTF_8.toString())
                    .body(jsonContent, UTF_8)
                    .build()
                    .toStreamingResponse();

        } catch (JsonProcessingException e) {
            return response(INTERNAL_SERVER_ERROR)
                    .body(e.getMessage(), UTF_8)
                    .build()
                    .toStreamingResponse();
        }
    }

    private String marshal(Object object, boolean prettyPrint) throws JsonProcessingException {
        return writer(prettyPrint).writeValueAsString(object);
    }

    private ObjectWriter writer(boolean prettyPrint) {
        return prettyPrint
                ? this.mapper.writerWithDefaultPrettyPrinter()
                : this.mapper.writer();
    }

    private boolean isPrettyPrint(HttpRequest request) {
        return request.queryParam("pretty").isPresent();
    }
}
