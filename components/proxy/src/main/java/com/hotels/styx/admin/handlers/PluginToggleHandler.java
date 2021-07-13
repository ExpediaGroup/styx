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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.HttpResponseStatus;
import com.hotels.styx.api.WebServiceHandler;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import org.slf4j.Logger;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.api.HttpMethod.GET;
import static com.hotels.styx.api.HttpMethod.PUT;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST;
import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static com.hotels.styx.api.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Handler that will enable and disable plugins.
 */
public class PluginToggleHandler implements WebServiceHandler {
    private static final Logger LOGGER = getLogger(PluginToggleHandler.class);

    private static final Pattern URL_PATTERN = Pattern.compile(".*/([^/]+)/enabled/?");
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final List<NamedPlugin> plugins;

    /**
     * Construct an instance given the plugins that you want to be able to enable and disable.
     *
     * @param plugins List of all plugins
     */
    public PluginToggleHandler(List<NamedPlugin> plugins) {
        this.plugins = requireNonNull(plugins);
    }

    @Override
    public Eventual<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        return getCurrentOrPutNewState(request, context)
                .onError(cause -> handleErrors(cause, context));
    }

    private Eventual<HttpResponse> getCurrentOrPutNewState(HttpRequest request, HttpInterceptor.Context context) {
        if (GET.equals(request.method())) {
            return getCurrentState(request, context);
        } else if (PUT.equals(request.method())) {
            return putNewState(request, context);
        } else {
            return Eventual.of(response(METHOD_NOT_ALLOWED).build());
        }
    }

    private Eventual<HttpResponse> getCurrentState(HttpRequest request, HttpInterceptor.Context context) {
        return Eventual.of(request)
                .map(this::plugin)
                .map(PluginToggleHandler::currentState)
                .map(state -> responseWith(OK, state.toString()));
    }

    private static PluginEnabledState currentState(NamedPlugin plugin) {
        return plugin.enabled() ? PluginEnabledState.ENABLED : PluginEnabledState.DISABLED;
    }

    private Eventual<HttpResponse> putNewState(HttpRequest request, HttpInterceptor.Context context) {
        return Eventual.of(request)
                .flatMap(this::requestedUpdate)
                .map(PluginToggleHandler::applyUpdate);
    }

    private Eventual<RequestedUpdate> requestedUpdate(HttpRequest request) {
        return requestedNewState(request)
                .map(state -> {
                    NamedPlugin plugin = plugin(request);

                    return new RequestedUpdate(plugin, state);
                });
    }

    private static HttpResponse applyUpdate(RequestedUpdate requestedUpdate) {
        boolean changed = requestedUpdate.apply();

        String message = responseMessage(requestedUpdate, changed);

        return responseWith(OK, message);
    }

    private static String responseMessage(RequestedUpdate requestedUpdate, boolean changed) {
        String message = changed ? wasChangedMessage(requestedUpdate) : wasNotChangedMessage(requestedUpdate);

        try {
            return JSON_MAPPER.writeValueAsString(new JsonResponse(requestedUpdate, message));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static String wasNotChangedMessage(RequestedUpdate requestedUpdate) {
        return format("State of '%s' was already '%s'", requestedUpdate.plugin().name(), requestedUpdate.newState());
    }

    private static String wasChangedMessage(RequestedUpdate requestedUpdate) {
        return format("State of '%s' changed to '%s'", requestedUpdate.plugin().name(), requestedUpdate.newState());
    }

    private static Matcher urlMatcher(HttpRequest request) {
        Matcher matcher = URL_PATTERN.matcher(request.path());

        if (!matcher.matches()) {
            throw new BadPluginToggleRequestException("Invalid URL");
        }
        return matcher;
    }

    private static Eventual<PluginEnabledState> requestedNewState(HttpRequest request) {
        return Eventual.of(PluginEnabledState.fromBoolean(parseToBoolean(request.bodyAs(UTF_8))));
    }

    private static HttpResponse responseWith(HttpResponseStatus status, String message) {
        return HttpResponse.response(status)
                .body(message + "\n", UTF_8)
                .addHeader(CONTENT_TYPE, PLAIN_TEXT_UTF_8.toString())
                .disableCaching()
                .build();
    }

    private static Eventual<HttpResponse> handleErrors(Throwable e, HttpInterceptor.Context context) {
        if (e instanceof PluginNotFoundException) {
            return Eventual.of(responseWith(NOT_FOUND, e.getMessage()));
        }

        if (e instanceof BadPluginToggleRequestException) {
            return Eventual.of(responseWith(BAD_REQUEST, e.getMessage()));
        }

        LOGGER.error("Plugin toggle error", e);
        return Eventual.of(responseWith(INTERNAL_SERVER_ERROR, ""));
    }

    private NamedPlugin plugin(HttpRequest request) {
        Matcher matcher = urlMatcher(request);
        String pluginName = matcher.group(1);
        return plugin(pluginName);
    }

    private static boolean parseToBoolean(String string) {
        switch (string.toLowerCase()) {
            case "true":
                return true;
            case "false":
                return false;
            default:
                throw new BadPluginToggleRequestException("No such state: only 'true' and 'false' are valid.");
        }
    }

    private NamedPlugin plugin(String pluginName) {
        return plugins.stream().filter(p -> p.name().equals(pluginName)).findFirst()
                .orElseThrow(() -> new PluginNotFoundException("No such plugin: pluginName=" + pluginName));
    }

    private enum PluginEnabledState {
        ENABLED, DISABLED;

        @Override
        public String toString() {
            return name().toLowerCase();
        }

        public boolean isEnabled() {
            return this == ENABLED;
        }

        public boolean matches(NamedPlugin plugin) {
            return plugin.enabled() == isEnabled();
        }

        public static PluginEnabledState fromBoolean(boolean enabled) {
            return enabled ? ENABLED : DISABLED;
        }

        public static int maxContentBytes() {
            return Stream.of(PluginEnabledState.values())
                    .map(PluginEnabledState::toString)
                    .map(String::getBytes)
                    .map(bytes -> bytes.length)
                    .max(naturalOrder())
                    .orElseThrow(IllegalStateException::new);
        }
    }

    private static class RequestedUpdate {
        private final NamedPlugin plugin;
        private final PluginEnabledState newState;

        RequestedUpdate(NamedPlugin plugin, PluginEnabledState newState) {
            this.plugin = plugin;
            this.newState = newState;
        }

        NamedPlugin plugin() {
            return plugin;
        }

        PluginEnabledState newState() {
            return newState;
        }

        boolean isPluginAlreadyInDesiredState() {
            return newState.matches(plugin);
        }

        boolean apply() {
            if (isPluginAlreadyInDesiredState()) {
                return false;
            }

            plugin.setEnabled(newState.isEnabled());
            return true;
        }

        @JsonProperty("name")
        public String name() {
            return plugin.name();
        }

        @JsonProperty("state")
        public String state() {
            return newState.toString();
        }
    }

    private static class JsonResponse {
        private final RequestedUpdate requestedUpdate;
        private final String message;

        JsonResponse(RequestedUpdate requestedUpdate, String message) {
            this.requestedUpdate = requestedUpdate;
            this.message = message;
        }

        @JsonProperty("plugin")
        public RequestedUpdate plugin() {
            return requestedUpdate;
        }

        @JsonProperty("message")
        public String message() {
            return message;
        }
    }

    private static class BadPluginToggleRequestException extends RuntimeException {
        BadPluginToggleRequestException(String message) {
            super(message);
        }
    }

    private static class PluginNotFoundException extends RuntimeException {
        PluginNotFoundException(String message) {
            super(message);
        }
    }
}
