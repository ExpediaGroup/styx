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
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.extension.OriginsChangeListener;
import com.hotels.styx.api.extension.OriginsSnapshot;
import com.hotels.styx.client.origincommands.GetOriginsInventorySnapshot;
import com.hotels.styx.common.http.handler.BaseHttpHandler;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.hotels.styx.admin.support.Json.PRETTY_PRINTER;
import static com.hotels.styx.api.FullHttpResponse.response;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.infrastructure.configuration.json.ObjectMappers.addStyxMixins;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Returns an origins inventory snapshot in an HTTP response.
 */
public class OriginsInventoryHandler extends BaseHttpHandler implements OriginsChangeListener {
    private static final Logger LOG = getLogger(OriginsInventoryHandler.class);

    private final ObjectMapper mapper = addStyxMixins(new ObjectMapper()).disable(FAIL_ON_EMPTY_BEANS)
            .setDefaultPrettyPrinter(PRETTY_PRINTER);

    private final Map<Id, OriginsSnapshot> originsInventorySnapshotMap = new ConcurrentHashMap<>();

    /**
     * Construct an instance.
     *
     * @param eventBus an event-bus to listen to for inventory state changes
     */
    public OriginsInventoryHandler(EventBus eventBus) {
        eventBus.register(this);
        eventBus.post(new GetOriginsInventorySnapshot());
    }

    @Override
    protected HttpResponse doHandle(HttpRequest request) {
        return response(OK)
                .addHeader(CONTENT_TYPE, JSON_UTF_8.toString())
                .disableCaching()
                .body(content(isPrettyPrint(request)), UTF_8)
                .build()
                .toStreamingResponse();
    }

    private String content(boolean pretty) {
        return originsInventorySnapshotMap.isEmpty() ? "{}" : marshall(originsInventorySnapshotMap, pretty);
    }

    private String marshall(Map<Id, OriginsSnapshot> originsInventorySnapshotMap, boolean pretty) {
        try {
            return writer(pretty).writeValueAsString(originsInventorySnapshotMap);
        } catch (JsonProcessingException e) {
            return e.getMessage();
        }
    }

    private ObjectWriter writer(boolean prettyPrint) {
        return prettyPrint
                ? this.mapper.writerWithDefaultPrettyPrinter()
                : this.mapper.writer();
    }

    private static boolean isPrettyPrint(HttpRequest request) {
        return request.queryParam("pretty").isPresent();
    }

    @Subscribe
    @Override
    public void originsChanged(OriginsSnapshot snapshot) {
        LOG.debug("received origins inventory state change {}", snapshot);
        originsInventorySnapshotMap.put(snapshot.appId(), snapshot);
    }
}
