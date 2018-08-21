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

import java.util.ArrayList;
import java.util.List;

class CompositeHttpErrorStatusListener implements HttpErrorStatusListener {
    private final List<HttpErrorStatusListener> listeners;

    public CompositeHttpErrorStatusListener(List<HttpErrorStatusListener> listeners) {
        this.listeners = new ArrayList<>(listeners);
    }

    @Override
    public void proxyErrorOccurred(Throwable cause) {
        listeners.forEach(listener -> listener.proxyErrorOccurred(cause));
    }

    @Override
    public void proxyWriteFailure(HttpRequest request, HttpResponse response, Throwable cause) {
        listeners.forEach(listener -> listener.proxyWriteFailure(request, response, cause));
    }

    @Override
    public void proxyingFailure(HttpRequest request, HttpResponse response, Throwable cause) {
        listeners.forEach(listener -> listener.proxyingFailure(request, response, cause));
    }

    @Override
    public void proxyErrorOccurred(HttpResponseStatus status, Throwable cause) {
        listeners.forEach(listener -> listener.proxyErrorOccurred(status, cause));
    }

    @Override
    public void proxyErrorOccurred(HttpRequest request, HttpResponseStatus status, Throwable cause) {
        listeners.forEach(listener -> listener.proxyErrorOccurred(request, status, cause));
    }
}
