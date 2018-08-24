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

import com.google.common.net.MediaType;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.Resource;
import com.hotels.styx.api.StyxObservable;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.function.Supplier;

import static com.google.common.base.Suppliers.memoizeWithExpiration;
import static com.google.common.net.HttpHeaders.CONTENT_LENGTH;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.google.common.net.MediaType.XML_UTF_8;
import static com.hotels.styx.api.FullHttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.slf4j.LoggerFactory.getLogger;
import com.hotels.styx.api.HttpRequest;

/**
 * Displays contents of logging configuration file.
 */
public class LoggingConfigurationHandler implements HttpHandler {
    private static final Logger LOG = getLogger(LoggingConfigurationHandler.class);

    private final Resource logConfigLocation;
    private final Supplier<Content> contentSupplier;

    public LoggingConfigurationHandler(Resource logConfigLocation) {
        this.logConfigLocation = requireNonNull(logConfigLocation);
        this.contentSupplier = memoizeWithExpiration(this::loadContent, 1, SECONDS)::get;
    }

    @Override
    public StyxObservable<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        return StyxObservable.of(generateResponse());
    }

    private HttpResponse generateResponse() {
        Content content = contentSupplier.get();

        return response(OK)
                .header(CONTENT_TYPE, content.type)
                .header(CONTENT_LENGTH, content.length)
                .body(content.content, UTF_8)
                .build()
                .toStreamingResponse();
    }

    private Content loadContent() {
        try {
            String fileContents = Resources.load(logConfigLocation);

            return new Content(XML_UTF_8, fileContents);
        } catch (IOException e) {
            logException(e);

            return new Content(PLAIN_TEXT_UTF_8, "Could not load resource='" + logConfigLocation + "'");
        }
    }

    private void logException(IOException e) {
        LOG.error("Could not load resource=" + logConfigLocation, e);
    }

    private static class Content {
        private final String content;
        private final String type;
        private final int length;

        Content(MediaType type, String content) {
            this.content = content;
            this.type = type.toString();
            this.length = content.getBytes(type.charset().get()).length;
        }
    }
}
