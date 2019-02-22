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
package com.hotels.styx.startup.extensions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import org.slf4j.Logger;

import java.util.Map;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A demo plugin.
 */
public class DemoPlugin implements Plugin {
    private static final Logger LOGGER = getLogger(DemoPlugin.class);

    private final DemoConfig config;

    /**
     * Construct demo plugin.
     *
     * @param config configuration
     */
    private DemoPlugin(DemoConfig config) {
        this.config = requireNonNull(config);
        LOGGER.info("Demo plugin constructed");
    }

    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {
        LOGGER.info("Demo plugin propagating request down the chain");

        return chain.proceed(request).map(response -> {
            LOGGER.info("Demo plugin has propagating response from the chain");
            return response.newBuilder()
                    .header("Demo-Plugin", config.responseHeaderValue)
                    .build();
        });
    }

    @Override
    public Map<String, HttpHandler> adminInterfaceHandlers() {
        return ImmutableMap.of(
                "example", adminHandler()
        );
    }

    private HttpHandler adminHandler() {
        return (request, context) -> {
            LOGGER.info("Demo plugin serving admin page");

            return Eventual.of(
                    HttpResponse.response().header(CONTENT_TYPE, PLAIN_TEXT_UTF_8)
                            .body("This is an admin page provided by a demo plugin used to test Styx's plugin functionality. Text from config=" + config.adminText, UTF_8)
                            .build()
                            .stream());
        };
    }

    /**
     * Factory for constructing demo plugin.
     */
    public static class Factory implements PluginFactory {
        @Override
        public Plugin create(Environment environment) {
            return new DemoPlugin(environment.pluginConfig(DemoConfig.class));
        }
    }

    public static class DemoConfig {
        private final String adminText;
        private final String responseHeaderValue;

        @JsonCreator
        public DemoConfig(
                @JsonProperty("adminText") String adminText,
                @JsonProperty("responseHeaderValue") String responseHeaderValue) {
            this.adminText = adminText;
            this.responseHeaderValue = responseHeaderValue;
        }
    }
}
