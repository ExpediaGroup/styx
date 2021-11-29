/*
  Copyright (C) 2013-2022 Expedia Inc.

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

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.Resource;
import com.hotels.styx.api.WebServiceHandler;
import org.slf4j.Logger;

import java.io.IOException;

<<<<<<< HEAD
import static com.google.common.base.Suppliers.memoizeWithExpiration;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
=======
import static com.google.common.net.HttpHeaders.CONTENT_LENGTH;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
>>>>>>> Remove Guava Suppliers
import static com.hotels.styx.api.HttpHeaderValues.PLAIN_TEXT;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.lang.System.currentTimeMillis;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Displays contents of logging configuration file.
 */
public class LoggingConfigurationHandler implements WebServiceHandler {
    private static final Logger LOG = getLogger(LoggingConfigurationHandler.class);

    private final Resource logConfigLocation;
    private volatile long lastLoad = 0L;
    private volatile Content content;

    public LoggingConfigurationHandler(Resource logConfigLocation) {
        this.logConfigLocation = requireNonNull(logConfigLocation);
    }

    @Override
    public Eventual<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        return Eventual.of(generateResponse());
    }

    private HttpResponse generateResponse() {
        synchronized (this) {
            long time = currentTimeMillis();
            if (content == null || time - lastLoad > 1000) {
                lastLoad = time;
                content = loadContent();
            }
        }

        return response(OK)
                .header(CONTENT_TYPE, content.type)
                .header(CONTENT_LENGTH, content.length)
                .body(content.content, UTF_8)
                .build();
    }

    private Content loadContent() {
        try {
            String fileContents = Resources.load(logConfigLocation);

            return new Content("text/xml; charset=utf-8", fileContents);
        } catch (IOException e) {
            logException(e);

            return new Content(PLAIN_TEXT, "Could not load resource='" + logConfigLocation + "'");
        }
    }

    private void logException(IOException e) {
        LOG.error("Could not load resource=" + logConfigLocation, e);
    }

    private static class Content {
        private final String content;
        private final String type;
        private final int length;

        Content(CharSequence type, String content) {
            this.content = content;
            this.type = type.toString();
            this.length = content.getBytes(UTF_8).length;
        }
    }
}
