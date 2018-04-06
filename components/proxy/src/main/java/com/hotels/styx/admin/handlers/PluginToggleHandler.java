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
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import rx.Observable;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.google.common.base.Throwables.propagate;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.hotels.styx.api.HttpResponse.Builder.response;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.PUT;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.naturalOrder;
import static org.slf4j.LoggerFactory.getLogger;
import static rx.Observable.just;

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
    public Observable<HttpResponse> handle(HttpRequest request) {
        return getCurrentOrPutNewState(request)
                .onErrorResumeNext(this::handleErrors);
    }

    private Observable<HttpResponse> getCurrentOrPutNewState(HttpRequest request) {
        if (GET.equals(request.method())) {
            return getCurrentState(request);
        } else if (PUT.equals(request.method())) {
            return putNewState(request);
        } else {
            return just(response(METHOD_NOT_ALLOWED).build());
        }
    }

    private Observable<HttpResponse> getCurrentState(HttpRequest request) {
        return just(request)
                .map(this::plugin)
                .map(this::currentState)
                .map(state -> responseWith(OK, state.toString()));
    }

    private PluginEnabledState currentState(NamedPlugin plugin) {
        return plugin.enabled() ? PluginEnabledState.ENABLED : PluginEnabledState.DISABLED;
    }

    private Observable<HttpResponse> putNewState(HttpRequest request) {
        return just(request)
                .flatMap(this::requestedUpdate)
                .map(this::applyUpdate);
    }

    private Observable<RequestedUpdate> requestedUpdate(HttpRequest request) {
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

    private Observable<PluginEnabledState> requestedNewState(HttpRequest request) {
        return request.decode(byteBuf -> byteBuf.toString(UTF_8), MAX_CONTENT_SIZE)
                .map(HttpRequest.DecodedRequest::body)
                .map(PluginToggleHandler::parseToBoolean)
                .map(PluginEnabledState::fromBoolean);
    }

    private HttpResponse responseWith(HttpResponseStatus status, String message) {
        return response(status)
                .body(message + "\n")
                .contentType(PLAIN_TEXT_UTF_8)
                .disableCaching()
                .build();
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

    private Observable<? extends HttpResponse> handleErrors(Throwable e) {
        if (e instanceof PluginNotFoundException) {
            return just(responseWith(NOT_FOUND, e.getMessage()));
        }

        if (e instanceof BadPluginToggleRequestException) {
            return just(responseWith(BAD_REQUEST, e.getMessage()));
        }

        LOGGER.error("Plugin toggle error", e);
        return just(responseWith(INTERNAL_SERVER_ERROR, ""));
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
