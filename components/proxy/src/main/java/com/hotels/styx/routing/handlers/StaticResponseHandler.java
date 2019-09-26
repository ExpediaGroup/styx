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
import com.hotels.styx.api.Buffer;
import com.hotels.styx.api.ByteStream;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHeaders;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.config.schema.Schema;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;
import com.hotels.styx.routing.RoutingObject;
import com.hotels.styx.routing.config.RoutingObjectFactory;
import com.hotels.styx.routing.config.StyxObjectDefinition;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.hotels.styx.api.HttpResponseStatus.statusWithCode;
import static com.hotels.styx.api.LiveHttpResponse.response;
import static com.hotels.styx.config.schema.SchemaDsl.field;
import static com.hotels.styx.config.schema.SchemaDsl.integer;
import static com.hotels.styx.config.schema.SchemaDsl.list;
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
            optional("content", string()),
            optional("headers", list(object(
                    field("name", string()),
                    field("value", string())
            ))));

    private final int status;
    private final String text;
    private final HttpHeaders headers;

    public StaticResponseHandler(int status, String text, HttpHeaders headers) {
        this.status = status;
        this.text = text;
        this.headers = headers;
    }

    @Override
    public Eventual<LiveHttpResponse> handle(LiveHttpRequest request, HttpInterceptor.Context context) {
        return Eventual.of(response(statusWithCode(status))
                .body(new ByteStream(Flux.just(new Buffer(text, UTF_8))))
                .headers(headers)
                .build());
    }

    private static class StaticResponseConfig {
        private final int status;
        private final String response;
        private final List<HttpHeaderConfig> headers;

        public StaticResponseConfig(@JsonProperty("status") int status,
                                    @JsonProperty("content") String content,
                                    @JsonProperty("headers") List<HttpHeaderConfig> headers) {
            this.status = status;
            this.response = content;
            this.headers = headers;
        }
    }

    private static class HttpHeaderConfig {
        private String name;
        private String value;

        public HttpHeaderConfig(@JsonProperty("name") String name,
                                @JsonProperty("value") String value) {
            this.name = name;
            this.value = value;
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

            HttpHeaders nettyHeaders = buildNettyHeaders(config);
            return new StaticResponseHandler(config.status, config.response, nettyHeaders);
        }

        private HttpHeaders buildNettyHeaders(StaticResponseConfig config) {
            List<HttpHeaderConfig> headerConfig = config.headers == null
                    ? Collections.emptyList()
                    : config.headers;

            HttpHeaders.Builder headersBuilder = new HttpHeaders.Builder();
            for (HttpHeaderConfig header : headerConfig) {
                headersBuilder.add(header.name, header.value);
            }
            return headersBuilder.build();
        }
    }
}
