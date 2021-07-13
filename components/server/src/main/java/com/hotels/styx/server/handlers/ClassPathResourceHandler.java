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
package com.hotels.styx.server.handlers;

import com.google.common.io.ByteStreams;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.HttpResponseStatus;
import com.hotels.styx.common.http.handler.BaseHttpHandler;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.api.HttpResponseStatus.FORBIDDEN;
import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.server.handlers.MediaTypes.mediaTypeOf;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.regex.Matcher.quoteReplacement;

/**
 * Handler for class path resources.
 */
public class ClassPathResourceHandler extends BaseHttpHandler {
    private static final Pattern DOUBLE_SEPARATOR = Pattern.compile("//", Pattern.LITERAL);
    private final String requestPrefix;
    private final String resourceRoot;

    public ClassPathResourceHandler(String resourceRoot) {
        this(resourceRoot, resourceRoot);
    }

    public ClassPathResourceHandler(String requestPrefix, String resourceRoot) {
        this.requestPrefix = checkPrefix(requestPrefix);
        this.resourceRoot = ensureHasTrailingSlash(resourceRoot);
    }

    private static String checkPrefix(String requestPrefix) {
        requireNonNull(requestPrefix);

        if (requestPrefix.isEmpty()) {
            throw new IllegalArgumentException("Request prefix is empty.");
        }

        if (!requestPrefix.startsWith("/")) {
            throw new IllegalArgumentException("Request prefix must start with '/'");
        }

        return ensureHasTrailingSlash(requestPrefix);
    }

    @Override
    protected HttpResponse doHandle(HttpRequest request, HttpInterceptor.Context context) {
        try {
            String path = request.path();

            if (!path.startsWith(requestPrefix) || path.contains("..")) {
                return error(FORBIDDEN);
            }

            String resourcePath = DOUBLE_SEPARATOR.matcher(resourceRoot + ensureHasPreceedingSlash(request.path().replace(requestPrefix, "")))
                    .replaceAll(quoteReplacement("/"));

            return new HttpResponse.Builder(OK)
                    .body(resourceBody(resourcePath), true)
                    .header(CONTENT_TYPE, mediaTypeOf(path))
                    .build();
        } catch (FileNotFoundException e) {
            return error(NOT_FOUND);
        } catch (IOException e) {
            return error(INTERNAL_SERVER_ERROR);
        }
    }

    private static String ensureHasTrailingSlash(String path) {
        return path.endsWith("/") ? path : path + "/";
    }

    private static String ensureHasPreceedingSlash(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    private static byte[] resourceBody(String path) throws IOException {
        try (InputStream stream = classPathResourceAsStream(path)) {
            return readStream(stream);
        }
    }

    private static HttpResponse error(HttpResponseStatus status) {
        return new HttpResponse.Builder(status)
                .body(status.description(), UTF_8)
                .build();
    }

    private static InputStream classPathResourceAsStream(String resource) throws FileNotFoundException {
        InputStream stream = ClassPathResourceHandler.class.getResourceAsStream(resource);

        if (stream == null) {
            throw new FileNotFoundException(resource);
        }

        return stream;
    }

    private static byte[] readStream(InputStream stream) throws IOException {
        return ByteStreams.toByteArray(stream);
    }
}
