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
package com.hotels.styx.server.handlers;

import com.google.common.io.ByteStreams;
import com.hotels.styx.api.http.handlers.BaseHttpHandler;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import static com.hotels.styx.api.MediaTypes.mediaTypeOf;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;

/**
 * Handler for class path resources.
 */
public class ClassPathResourceHandler extends BaseHttpHandler {
    private final String root;

    public ClassPathResourceHandler(String root) {
        this.root = ensureRootEndsInSlash(root);
    }

    private static String ensureRootEndsInSlash(String root) {
        return root.endsWith("/") ? root : root + "/";
    }

    @Override
    protected HttpResponse doHandle(HttpRequest request) {
        try {
            String path = request.path();

            if (!path.startsWith(root) || path.contains("..")) {
                return error(FORBIDDEN);
            }

            return new HttpResponse.Builder(OK)
                    .body(resourceBody(path))
                    .header(CONTENT_TYPE, mediaTypeOf(path))
                    .build();
        } catch (FileNotFoundException e) {
            return error(NOT_FOUND);
        } catch (IOException e) {
            return error(INTERNAL_SERVER_ERROR);
        }
    }

    private static byte[] resourceBody(String path) throws IOException {
        try (InputStream stream = classPathResourceAsStream(path)) {
            return readStream(stream);
        }
    }

    private static HttpResponse error(HttpResponseStatus status) {
        return new HttpResponse.Builder(status)
                .body(status.reasonPhrase())
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
