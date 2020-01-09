/*
  Copyright (C) 2013-2020 Expedia Inc.

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

import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpMethod;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.WebServiceHandler;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.hotels.styx.api.HttpMethod.DELETE;
import static com.hotels.styx.api.HttpMethod.GET;
import static com.hotels.styx.api.HttpMethod.POST;
import static com.hotels.styx.api.HttpMethod.PUT;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static java.util.stream.Collectors.toMap;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A configurable router.
 */
public class UrlPatternRouter implements WebServiceHandler {
    private static final Logger LOGGER = getLogger(UrlPatternRouter.class);
    private static final String PLACEHOLDERS_KEY = "UrlRouter.placeholders";
    private final List<RouteDescriptor> alternatives;

    private UrlPatternRouter(List<RouteDescriptor> alternatives) {
        this.alternatives = ImmutableList.copyOf(alternatives);
    }

    public static Map<String, String> placeholders(HttpInterceptor.Context context) {
        return context.getIfAvailable(PLACEHOLDERS_KEY, Map.class).get();
    }

    @Override
    public Eventual<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        for (RouteDescriptor route : alternatives) {
            if (request.method().equals(route.method())) {
                Matcher match = route.uriPattern().matcher(request.path());

                LOGGER.debug("Request path '{}' matching against route pattern '{}' matches: {}", new Object[] {
                        request.path(), route.uriPattern(), match.matches()});

                if (match.matches()) {
                    Map<String, String> placeholders = route.placeholderNames().stream()
                            .collect(toMap(name -> name, match::group));

                    context.add(PLACEHOLDERS_KEY, placeholders);

                    try {
                        return route.handler().handle(request, context);
                    } catch (Exception cause) {
                        LOGGER.error("ERROR: {} {}", new Object[] {request.method(), request.path(), cause});
                        return Eventual.of(response(INTERNAL_SERVER_ERROR).build());
                    }
                }
            }
        }

        return Eventual.of(response(NOT_FOUND).build());
    }

    /**
     * A builder class.
     */
    public static class Builder {
        private final List<RouteDescriptor> alternatives = new LinkedList<>();
        private final String pathPrefix;

        public Builder() {
            this("");
        }

        /**
         * The pathPrefix will be prepended to the URI patterns provided in the get/post/...
         * methods.
         * @param pathPrefix path prefix.
         */
        public Builder(String pathPrefix) {
            this.pathPrefix = pathPrefix.endsWith("/")
                    ? pathPrefix.substring(pathPrefix.length() - 1)
                    : pathPrefix;
        }

        public Builder get(String uriPattern, WebServiceHandler handler) {
            alternatives.add(new RouteDescriptor(GET, addPrefix(uriPattern), handler));
            return this;
        }

        public Builder post(String uriPattern, WebServiceHandler handler) {
            alternatives.add(new RouteDescriptor(POST, addPrefix(uriPattern), handler));
            return this;
        }

        public Builder put(String uriPattern, WebServiceHandler handler) {
            alternatives.add(new RouteDescriptor(PUT, addPrefix(uriPattern), handler));
            return this;
        }

        public Builder delete(String uriPattern, WebServiceHandler handler) {
            alternatives.add(new RouteDescriptor(DELETE, addPrefix(uriPattern), handler));
            return this;
        }

        private String addPrefix(String uriPattern) {
            StringBuilder path = new StringBuilder(pathPrefix);
            if (uriPattern.length() > 0 && !uriPattern.startsWith("/")) {
                path.append("/");
            }
            path.append(uriPattern);
            return path.toString();
        }

        public UrlPatternRouter build() {
            return new UrlPatternRouter(alternatives);
        }
    }

    private static class RouteDescriptor {
        private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(":([a-zA-Z0-9-_]+)");

        private final HttpMethod method;
        private final Pattern uriPattern;
        private final WebServiceHandler handler;
        private final List<String> placeholderNames;

        public RouteDescriptor(HttpMethod method, String uriPattern, WebServiceHandler handler) {
            this.method = method;
            this.handler = handler;
            this.placeholderNames = placeholders(uriPattern);
            this.uriPattern = compilePattern(uriPattern);
        }

        public HttpMethod method() {
            return method;
        }

        public Pattern uriPattern() {
            return uriPattern;
        }

        public WebServiceHandler handler() {
            return handler;
        }

        public List<String> placeholderNames() {
            return placeholderNames;
        }

        private static Pattern compilePattern(String pattern) {
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(pattern);

            return Pattern.compile(matcher.replaceAll("(?<$1>[a-zA-Z0-9-_]+)"));
        }

        private static List<String> placeholders(String pattern) {
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(pattern);

            List<String> outcome = new ArrayList<>();

            while (matcher.find()) {
                outcome.add(matcher.group(1));
            }

            return outcome;
        }

    }
}
