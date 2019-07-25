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
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.config.schema.Schema;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;
import com.hotels.styx.routing.RoutingObject;
import com.hotels.styx.routing.config.RoutingObjectFactory;
import com.hotels.styx.routing.config.StyxObjectDefinition;

import java.util.List;

import static com.hotels.styx.api.EarlyReturn.returnEarlyWithResponse;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.statusWithCode;
import static com.hotels.styx.config.schema.SchemaDsl.field;
import static com.hotels.styx.config.schema.SchemaDsl.integer;
import static com.hotels.styx.config.schema.SchemaDsl.object;
import static com.hotels.styx.config.schema.SchemaDsl.optional;
import static com.hotels.styx.config.schema.SchemaDsl.string;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * A HTTP handler for returning a static response.
 */
public class StaticResponseHandler implements RoutingObject {
    public static final Schema.FieldType SCHEMA = object(
            field("status", integer()),
            optional("content", string()));

    private final int status;
    private final String text;

    public StaticResponseHandler(int status, String text) {
        this.status = status;
        this.text = text;
    }

    @Override
    public Eventual<LiveHttpResponse> handle(LiveHttpRequest request, HttpInterceptor.Context context) {
        return returnEarlyWithResponse(request,
                response(statusWithCode(status))
                        .body(text, UTF_8)
                        .build()
                        .stream());
    }

    private static class StaticResponseConfig {
        private final int status;
        private final String response;

        public StaticResponseConfig(@JsonProperty("status") int status,
                                    @JsonProperty("content") String content) {
            this.status = status;
            this.response = content;
        }
    }

    /**
     * Builds a static response handler from Yaml configuration.
     */
    public static class Factory implements RoutingObjectFactory {
        public RoutingObject build(List<String> fullName, Context context, StyxObjectDefinition configBlock) {
            requireNonNull(configBlock.config());

            StaticResponseConfig config = new JsonNodeConfig(configBlock.config())
                    .as(StaticResponseConfig.class);

            return new StaticResponseHandler(config.status, config.response);
        }
    }
}
