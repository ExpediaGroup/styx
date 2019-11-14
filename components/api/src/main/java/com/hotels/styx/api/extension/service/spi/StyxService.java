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
package com.hotels.styx.api.extension.service.spi;

import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.StyxLifecycleListener;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import static java.util.Collections.emptyMap;

/**
 * A StyxService is a background task supporting Styx proxy by performing ongoing tasks,
 * such as service discovery or monitoring origin availability.
 */
public interface StyxService extends StyxLifecycleListener {

    /**
     * Invoked when Styx core starts the service.
     *
     * An implementation of start() should:
     *
     * - Asynchronously initialise all resources that are necessary for running
     *   the service. Especially resources that involve IO, such as opening files
     *   or establishing network connections, etc.
     *
     *  @return StyxFuture associated to the asynchronous initialisation task.
     *
     * - The returned StyxFuture must be completed with a *null* value upon
     *   successful initialisation.
     *
     * - The returned StyxFuture must be completed exceptionally with a failure
     *   cause when the initialisation fails.
     */
    CompletableFuture<Void> start();

    /**
     * Invoked when Styx core stops the service.
     *
     * An implementation of stop() should:
     *
     * - Create an asynchronous task to initialise the service. The stop() method
     *   should tear down any resources that are associated with the service.
     *
     * @return StyxFuture associated to the asynchronous teardown task.
     *
     * - The returned StyxFuture must be completed with a *null* value when
     *   successfully released all resources.
     *
     * - The returned StyxFuture must be completed exceptionally with a failure
     *   cause when the resource release fails.
     */
    CompletableFuture<Void> stop();

    /**
     * An admin interface hook.
     *
     * @deprecated use {@link #adminInterfaceHandlers(String)} instead.
     *
     * Styx Core calls this method to publish Service admin interface extensions
     * on its admin HTTP server. This method should return a map of endpoint
     * paths with corresponding HTTP handlers.
     * <br>
     * Styx Core maintains a namespace for admin interface extensions. Therefore, a
     * prefix e.g. {@code /admin/providers/<PROVIDER-NAME>/} is applied to the returned
     * endpoint paths.
     * <br>
     * Suppose this method returns a map with single entry:
     * <br>
     *    {@code "/abc" -> HttpHandler(...)}
     * <br>
     * Then the actual admin endpoint will be {@code /admin/providers/<PROVIDER-NAME>/abc}.
     * <br>
     *
     * @return Returns a list of named admin interface handlers.
     */
    @Deprecated
    default Map<String, HttpHandler> adminInterfaceHandlers() {
        return emptyMap();
    }


    /**
     * An admin interface hook.
     *
     * Styx Core calls this method to publish Service admin interface extensions
     * on its admin HTTP server. This method should return a map of endpoint
     * paths with corresponding HTTP handlers.
     * <br>
     * Styx Core maintains a namespace for admin interface extensions. Therefore, a
     * {@code /admin/providers/<PROVIDER-NAME>/} prefix is applied to the returned
     * endpoint paths.
     * <br>
     * Suppose this method returns a map with single entry:
     * <br>
     *    {@code "/abc" -> HttpHandler(...)}
     * <br>
     * Then the actual admin endpoint will be {@code /admin/providers/<PROVIDER-NAME>/abc}.
     * <br>
     * A {@code namespace} parameter can be used to infer an endpoint URL that
     * will be visible to the end-users. This can be used for example to generate an
     * HTTP hyperlink to an admin interface extension. In the previous example, the
     * namespace would be {@code /admin/providers/<PROVIDER-NAME>}
     *
     * @param namespace A base path for the admin interface handlers.
     *
     * @return Returns a list of named admin interface handlers.
     */
    default Map<String, HttpHandler> adminInterfaceHandlers(String namespace) {
        return adminInterfaceHandlers();
    }
}
