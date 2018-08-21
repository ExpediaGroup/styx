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
package com.hotels.styx.server;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.HttpResponseStatus;

import static java.util.Arrays.asList;

/**
 * Listens to errors during requests that result in a 4xx or 5xx status code, so that metrics can be recorded.
 */
public interface HttpErrorStatusListener {
    HttpErrorStatusListener IGNORE_ERROR_STATUS = new HttpErrorStatusListener() {
        @Override
        public void proxyErrorOccurred(Throwable cause) { }

        @Override
        public void proxyWriteFailure(HttpRequest request, HttpResponse response, Throwable cause) { }

        @Override
        public void proxyingFailure(HttpRequest request, HttpResponse response, Throwable cause) { }

        @Override
        public void proxyErrorOccurred(HttpResponseStatus status, Throwable cause) { }

        @Override
        public void proxyErrorOccurred(HttpRequest request, HttpResponseStatus status, Throwable cause) { }
    };

    static HttpErrorStatusListener compose(HttpErrorStatusListener... listeners) {
        return new CompositeHttpErrorStatusListener(asList(listeners));
    }

    /**
     * To be called when an exception was thrown in styx while proxying.
     *
     * @param cause the throwable class associated with this error
     */
    void proxyErrorOccurred(Throwable cause);

    /**
     * To be called when an exception was thrown in styx while writing response.
     *
     * @param cause the throwable class associated with this error
     */
    void proxyWriteFailure(HttpRequest request, HttpResponse response, Throwable cause);

    /**
     * To be called when an exception was thrown in styx while proxying.
     * If the status is not an error code, the listener should ignore this method call.
     *
     * @param cause the throwable class associated with this error
     */
    void proxyingFailure(HttpRequest request, HttpResponse response, Throwable cause);

    /**
     * To be called when an exception was thrown in styx while proxying.
     * If the status is not an error code, the listener should ignore this method call.
     *
     * @param status HTTP response status
     * @param cause  the throwable class associated with this error
     */
    void proxyErrorOccurred(HttpResponseStatus status, Throwable cause);

    /**
     * To be called when an exception was thrown in styx while proxying.
     * If the status is not an error code, the listener should ignore this method call.
     *
     * @param request Proxied request
     * @param status  HTTP response status
     * @param cause   the throwable class associated with this error
     */
    void proxyErrorOccurred(HttpRequest request, HttpResponseStatus status, Throwable cause);
}
