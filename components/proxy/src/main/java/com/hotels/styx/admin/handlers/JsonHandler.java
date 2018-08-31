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

import com.fasterxml.jackson.databind.Module;
import com.google.common.annotations.VisibleForTesting;
import com.hotels.styx.admin.CachingSupplier;
import com.hotels.styx.admin.dashboard.JsonSupplier;
import com.hotels.styx.admin.handlers.json.JsonReformatter;
import com.hotels.styx.api.Clock;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.common.http.handler.BaseHttpHandler;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.hotels.styx.api.Clocks.systemClock;
import static com.hotels.styx.api.FullHttpResponse.response;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Handler for returning JSON. If a cache expiration value is present, the JSON is not regenerated on every call, unless
 * the specified duration has passed since the last generation.
 *
 * @param <E> type of object to map into JSON
 */
public class JsonHandler<E> extends BaseHttpHandler {
    private static final Logger LOG = getLogger(JsonHandler.class);

    private final Supplier<String> jsonSupplier;
    private final Supplier<String> prettyJsonSupplier;
    private final Supplier<String> reformatSupplier;

    /**
     * Constructs an instance.
     *
     * @param data            an object to convert to JSON upon each update
     * @param cacheExpiration time between updates
     * @param modules         modules for object mapper
     */
    public JsonHandler(E data, Optional<Duration> cacheExpiration, Module... modules) {
        this(() -> data, cacheExpiration, modules);
    }

    /**
     * Constructs an instance.
     *
     * @param dataSupplier    a supplier that provides an object to convert to JSON upon each update
     * @param cacheExpiration time between updates
     * @param modules         modules for object mapper
     */
    public JsonHandler(Supplier<E> dataSupplier, Optional<Duration> cacheExpiration, Module... modules) {
        this(dataSupplier, cacheExpiration, systemClock(), modules);
    }

    @VisibleForTesting
    JsonHandler(Supplier<E> dataSupplier, Optional<Duration> cacheExpiration, Clock clock, Module... modules) {
        if (cacheExpiration.isPresent()) {
            LOG.debug("{} instantiated with cache expiration of {}", getClass().getSimpleName(), cacheExpiration.get());
        } else {
            LOG.debug("{} instantiated with no caching", getClass().getSimpleName());
        }

        this.jsonSupplier = cachedSupplier(cacheExpiration, clock, JsonSupplier.create(dataSupplier, false, modules));
        this.prettyJsonSupplier = cachedSupplier(cacheExpiration, clock, JsonSupplier.create(dataSupplier, true, modules));
        this.reformatSupplier = cachedSupplier(cacheExpiration, clock, () -> JsonReformatter.reformat(jsonSupplier.get()));
    }

    @Override
    protected HttpResponse doHandle(HttpRequest request) {
        try {
            String jsonContent = jsonSupplier(request).get();

            return response(OK)
                    .disableCaching()
                    .addHeader(CONTENT_TYPE, JSON_UTF_8.toString())
                    .body(jsonContent, UTF_8)
                    .build()
                    .toStreamingResponse();

        } catch (Exception e) {
            return response(INTERNAL_SERVER_ERROR)
                    .body(e.getMessage(), UTF_8)
                    .build()
                    .toStreamingResponse();
        }
    }

    private Supplier<String> jsonSupplier(HttpRequest request) {
        if (request.queryParam("reformat").isPresent()) {
            return reformatSupplier;
        }

        return request.queryParam("pretty").isPresent() ? prettyJsonSupplier : jsonSupplier;
    }

    private static Supplier<String> cachedSupplier(Optional<Duration> cacheExpiration, Clock clock, Supplier<String> uncachedSupplier) {
        return cacheExpiration
                .map(expiration -> (Supplier<String>) new CachingSupplier<>(uncachedSupplier, expiration, clock))
                .orElse(uncachedSupplier);
    }

}
