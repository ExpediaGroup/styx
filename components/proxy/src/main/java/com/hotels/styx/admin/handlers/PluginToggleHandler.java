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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.api.HttpResponseStatus;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.google.common.base.Throwables.propagate;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST;
import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static com.hotels.styx.api.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.HttpMethod.GET;
import static com.hotels.styx.api.HttpMethod.PUT;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.naturalOrder;
import static org.slf4j.LoggerFactory.getLogger;
import com.hotels.styx.api.HttpRequest;

/**
 * Handler that will enable and disable plugins.
 */
public class PluginToggleHandler implements HttpHandler {
    private static final Logger LOGGER = getLogger(PluginToggleHandler.class);

    private static final Pattern URL_PATTERN = Pattern.compile(".*/([^/]+)/enabled/?");
    private static final int MAX_CONTENT_SIZE = PluginEnabledState.maxContentBytes();
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final Map<String, NamedPlugin> plugins;

    /**
     * Construct an instance given the plugins that you want to be able to enable and disable.
     *
     * @param namedPlugins the plugins
     */
    public PluginToggleHandler(Iterable<NamedPlugin> namedPlugins) {
        this.plugins = new HashMap<>();

        namedPlugins.forEach(namedPlugin ->
                this.plugins.put(namedPlugin.name(), namedPlugin));
    }

    @Override
    public StyxObservable<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        return getCurrentOrPutNewState(request, context)
                .onError(cause -> handleErrors(cause, context));
    }

    private StyxObservable<HttpResponse> getCurrentOrPutNewState(HttpRequest request, HttpInterceptor.Context context) {
        if (GET.equals(request.method())) {
            return getCurrentState(request, context);
        } else if (PUT.equals(request.method())) {
            return putNewState(request, context);
        } else {
            return StyxObservable.of(response(METHOD_NOT_ALLOWED).build());
        }
    }

    private StyxObservable<HttpResponse> getCurrentState(HttpRequest request, HttpInterceptor.Context context) {
        return StyxObservable.of(request)
                .map(this::plugin)
                .map(this::currentState)
                .map(state -> responseWith(OK, state.toString()));
    }

    private PluginEnabledState currentState(NamedPlugin plugin) {
        return plugin.enabled() ? PluginEnabledState.ENABLED : PluginEnabledState.DISABLED;
    }

    private StyxObservable<HttpResponse> putNewState(HttpRequest request, HttpInterceptor.Context context) {
        return StyxObservable.of(request)
                .flatMap(this::requestedUpdate)
                .map(this::applyUpdate);
    }

    private StyxObservable<RequestedUpdate> requestedUpdate(HttpRequest request) {
        return requestedNewState(request)
                .map(state -> {
                    NamedPlugin plugin = plugin(request);

                    return new RequestedUpdate(plugin, state);
                });
    }

    private HttpResponse applyUpdate(RequestedUpdate requestedUpdate) {
        boolean changed = requestedUpdate.apply();

        String message = responseMessage(requestedUpdate, changed);

        return responseWith(OK, message);
    }

    private String responseMessage(RequestedUpdate requestedUpdate, boolean changed) {
        String message = changed ? wasChangedMessage(requestedUpdate) : wasNotChangedMessage(requestedUpdate);

        try {
            return JSON_MAPPER.writeValueAsString(new JsonResponse(requestedUpdate, message));
        } catch (JsonProcessingException e) {
            throw propagate(e);
        }
    }

    private String wasNotChangedMessage(RequestedUpdate requestedUpdate) {
        return format("State of '%s' was already '%s'", requestedUpdate.plugin().name(), requestedUpdate.newState());
    }

    private String wasChangedMessage(RequestedUpdate requestedUpdate) {
        return format("State of '%s' changed to '%s'", requestedUpdate.plugin().name(), requestedUpdate.newState());
    }

    private NamedPlugin plugin(HttpRequest request) {
        Matcher matcher = urlMatcher(request);
        String pluginName = matcher.group(1);
        return plugin(pluginName);
    }

    private NamedPlugin plugin(String pluginName) {
        NamedPlugin plugin = plugins.get(pluginName);

        if (plugin == null) {
            throw new PluginNotFoundException("No such plugin");
        }
        return plugin;
    }

    private Matcher urlMatcher(HttpRequest request) {
        Matcher matcher = URL_PATTERN.matcher(request.path());

        if (!matcher.matches()) {
            throw new BadPluginToggleRequestException("Invalid URL");
        }
        return matcher;
    }

    private static StyxObservable<PluginEnabledState> requestedNewState(HttpRequest request) {
        return request.toFullRequest(MAX_CONTENT_SIZE)
                .map(fullRequest -> fullRequest.bodyAs(UTF_8))
                .map(PluginToggleHandler::parseToBoolean)
                .map(PluginEnabledState::fromBoolean);
    }

    private static HttpResponse responseWith(HttpResponseStatus status, String message) {
        return FullHttpResponse.response(status)
                .body(message + "\n", UTF_8)
                .addHeader(CONTENT_TYPE, PLAIN_TEXT_UTF_8.toString())
                .disableCaching()
                .build()
                .toStreamingResponse();
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

    private static StyxObservable<HttpResponse> handleErrors(Throwable e, HttpInterceptor.Context context) {
        if (e instanceof PluginNotFoundException) {
            return StyxObservable.of(responseWith(NOT_FOUND, e.getMessage()));
        }

        if (e instanceof BadPluginToggleRequestException) {
            return StyxObservable.of(responseWith(BAD_REQUEST, e.getMessage()));
        }

        LOGGER.error("Plugin toggle error", e);
        return StyxObservable.of(responseWith(INTERNAL_SERVER_ERROR, ""));
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
