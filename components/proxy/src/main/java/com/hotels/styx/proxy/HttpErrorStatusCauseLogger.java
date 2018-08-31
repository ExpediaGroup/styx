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
package com.hotels.styx.proxy;

import com.hotels.styx.server.HttpErrorStatusListener;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.HttpResponseStatus;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Wrapper for {@link HttpErrorStatusListener} that also logs {@link Throwable}s.
 */
public class HttpErrorStatusCauseLogger implements HttpErrorStatusListener {
    private static final Logger LOG = getLogger(HttpErrorStatusCauseLogger.class);

    @Override
    public void proxyErrorOccurred(HttpResponseStatus status, Throwable cause) {
        if (status.code() > 500) {
            // we remove the stack trace so that logs are not flooded with high volumes of data when origins are unreachable/timing out.
            LOG.error("Failure status=\"{}\", exception=\"{}\"", status, withoutStackTrace(cause));
        } else {
            LOG.error("Failure status=\"{}\"", status, cause);
        }
    }

    @Override
    public void proxyErrorOccurred(HttpRequest request, HttpResponseStatus status, Throwable cause) {
        if (status.code() == 500) {
            LOG.error("Failure status=\"{}\" during request={}", new Object[]{status, request, cause});
        } else {
            proxyErrorOccurred(status, cause);
        }
    }

    @Override
    public void proxyErrorOccurred(Throwable cause) {
        LOG.error("Error occurred during proxying", cause);
    }

    @Override
    public void proxyWriteFailure(HttpRequest request, HttpResponse response, Throwable cause) {
        LOG.error("Error writing response. request={}, response={}, cause={}", new Object[]{request, response, cause});
    }

    @Override
    public void proxyingFailure(HttpRequest request, HttpResponse response, Throwable cause) {
        LOG.error("Error proxying request. request={} response={} cause={}", new Object[]{request, response, cause});
    }

    private static String withoutStackTrace(Throwable cause) {
        StringBuilder builder = new StringBuilder(cause.toString());

        Throwable head = cause;
        while ((head = head.getCause()) != null) {
            builder.append(", cause=").append('"').append(head).append('"');
        }

        return builder.toString();
    }
}
