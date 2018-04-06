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
package com.hotels.styx.routing.handlers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hotels.styx.api.HttpHandler2;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;
import com.hotels.styx.routing.config.HttpHandlerFactory;
import com.hotels.styx.routing.config.RouteHandlerDefinition;
import com.hotels.styx.routing.config.RouteHandlerFactory;
import io.netty.handler.codec.http.HttpResponseStatus;
import rx.Observable;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.hotels.styx.api.HttpResponse.Builder.response;

/**
 * A HTTP handler for returning a static response.
 */
public class StaticResponseHandler implements HttpHandler2 {
    private final int status;
    private final String text;

    private StaticResponseHandler(int status, String text) {
        this.status = status;
        this.text = text;
    }

    @Override
    public Observable<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        return Observable.just(response(HttpResponseStatus.valueOf(status)).body(text).build());
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
    public static class ConfigFactory implements HttpHandlerFactory {
        public HttpHandler2 build(List<String> parents, RouteHandlerFactory builders, RouteHandlerDefinition configBlock) {
            checkNotNull(configBlock.config());

            StaticResponseConfig config = new JsonNodeConfig(configBlock.config())
                    .as(StaticResponseConfig.class);

            return new StaticResponseHandler(config.status, config.response);
        }
    }
}
