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
package com.hotels.styx.admin.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.common.http.handler.BaseHttpHandler;
import com.hotels.styx.configstore.ConfigStore;
import com.hotels.styx.startup.ProxyStatusNotifications.ProxyStatus;
import com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginStatus;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static com.hotels.styx.startup.ProxyStatusNotifications.PROXY_HTTPS_STATUS_KEY;
import static com.hotels.styx.startup.ProxyStatusNotifications.PROXY_HTTP_STATUS_KEY;
import static com.hotels.styx.startup.extensions.PluginStatusNotifications.PLUGIN_STATUS_KEY_PREFIX;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A handler that provides information on the status of the proxy server and plugins start-up.
 */
public class StartupStatusHandler extends BaseHttpHandler {
    private static final Logger LOGGER = getLogger(StartupStatusHandler.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ConfigStore configStore;

    public StartupStatusHandler(ConfigStore configStore) {
        this.configStore = requireNonNull(configStore);
    }

    @Override
    protected LiveHttpResponse doHandle(LiveHttpRequest request) {
        Map<String, String> connectors = new LinkedHashMap<>();
        Map<String, String> plugins = new HashMap<>();

        connectors.put("http", configStore.get(PROXY_HTTP_STATUS_KEY, ProxyStatus.class)
                .map(Object::toString)
                .orElse("unknown"));

        connectors.put("https", configStore.get(PROXY_HTTPS_STATUS_KEY, ProxyStatus.class)
                .map(Object::toString)
                .orElse("unknown"));

        configStore.startingWith(PLUGIN_STATUS_KEY_PREFIX, PluginStatus.class).forEach(entry -> {
            String pluginName = entry.key().substring(entry.key().lastIndexOf('.') + 1);
            plugins.put(pluginName, entry.value().toString());
        });

        try {
            String body = OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(ImmutableMap.of(
                            "connectors", connectors,
                            "plugins", plugins));

            return response()
                    .body(body, UTF_8)
                    .disableCaching()
                    .build()
                    .stream();
        } catch (JsonProcessingException e) {
            LOGGER.error("Could not generate response", e);
            return response(INTERNAL_SERVER_ERROR)
                    .body(e.getMessage(), UTF_8)
                    .build()
                    .stream();
        }
    }

}
