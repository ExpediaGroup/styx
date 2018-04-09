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
package com.hotels.styx.api.plugins.spi;

import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.StyxLifecycleListener;

import java.util.Map;

import static java.util.Collections.emptyMap;

/**
 * Styx Plugins are used to intercept the HttpRequest/HttpResponse proxy call.
 *
 * They can be used to log/record information and to modify requests and/or responses.
 */
public interface Plugin extends HttpInterceptor, StyxLifecycleListener {
    Plugin PASS_THROUGH = (request, chain) -> chain.proceed(request);

    /**
     * Returns a map of (path, handler) pairs, where *path* is an URL path that gets
     * inserted under "/admin/plugins/" URL path prefix, and *handler* is a HttpHandler
     * instance that serves the admin request.
     *
     * Default implementation returns an empty map.
     *
     * @return Admin interface handlers supported by the plugin
     */
    default Map<String, HttpHandler> adminInterfaceHandlers() {
        return emptyMap();
    }
}
