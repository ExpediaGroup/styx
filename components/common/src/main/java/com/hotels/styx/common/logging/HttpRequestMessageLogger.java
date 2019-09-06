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
package com.hotels.styx.common.logging;

import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.common.format.HttpMessageFormatter;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Logs client side requests and responses when enabled. Disabled by default.
 */
public class HttpRequestMessageLogger {

    private final Logger logger;
    private final boolean longFormatEnabled;
    private final HttpMessageFormatter httpMessageFormatter;

    public HttpRequestMessageLogger(String name, boolean longFormatEnabled, HttpMessageFormatter httpMessageFormatter) {
        this.longFormatEnabled = longFormatEnabled;
        this.httpMessageFormatter = httpMessageFormatter;
        logger = getLogger(name);
    }

    public void logRequest(LiveHttpRequest request, Origin origin) {
        if (request == null) {
            logger.warn("requestId=N/A, request=null, origin={}", origin);
        } else {
            logger.info("requestId={}, request={}, origin={}", new Object[] {request.id(), requestAsString(request), origin});
        }
    }

    public void logRequest(LiveHttpRequest request, Origin origin, boolean secure) {
        if (request == null) {
            logger.warn("requestId=N/A, request=null, origin={}", origin);
        } else {
            logger.info("requestId={}, request={}, secure={}, origin={}", new Object[] {request.id(), requestAsString(request), secure, origin});
        }
    }

    public void logResponse(LiveHttpRequest request, LiveHttpResponse response) {
        if (response == null) {
            logger.warn("requestId={}, response=null", id(request));
        } else {
            logger.info("requestId={}, response={}", id(request), responseAsString(response));
        }
    }

    public void logResponse(LiveHttpRequest request, LiveHttpResponse response, boolean secure) {
        if (response == null) {
            logger.warn("requestId={}, response=null", id(request));
        } else {
            logger.info("requestId={}, response={}, secure={}", new Object[] {id(request), responseAsString(response), secure});
        }
    }

    private String requestAsString(LiveHttpRequest request) {
        return longFormatEnabled ? httpMessageFormatter.formatRequest(request) : request.toString();
    }

    private String responseAsString(LiveHttpResponse response) {
        return longFormatEnabled ? httpMessageFormatter.formatResponse(response) : response.toString();
    }

    private static Object id(LiveHttpRequest request) {
        return request != null ? request.id() : null;
    }

}
