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
package com.hotels.styx.admin.handlers;

import static com.hotels.styx.admin.AdminServerBuilder.adminPath;
import static com.hotels.styx.admin.AdminServerBuilder.adminEndpointPath;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.WebServiceHandler;
import com.hotels.styx.api.configuration.ObjectStore;
import com.hotels.styx.common.http.handler.HttpStreamer;
import com.hotels.styx.routing.db.StyxObjectStore;
import com.hotels.styx.routing.handlers.ProviderObjectRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Routes admin requests to the admin endpoints of each {@link com.hotels.styx.api.extension.service.spi.StyxService}
 * in the Provider {@link ObjectStore}, and to the index page that organizes and lists these endpoints.
 * This handler registers as a watcher on the store, and will keep the endpoint list up to date as the store data changes.
 */
public class ProviderRoutingHandler implements WebServiceHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProviderRoutingHandler.class);
    private static final int MEGABYTE = 1024 * 1024;
    private static final String ADMIN_ROOT = "providers";

    private final ReadWriteLock routerLock = new ReentrantReadWriteLock();
    private UrlPatternRouter router;

    /**
     * Create a new handler for the given provider ojbect store.
     * @param providerDb the provider object store
     */
    public ProviderRoutingHandler(StyxObjectStore<ProviderObjectRecord> providerDb) {
        Flux.from(providerDb.watch()).subscribe(
                this::refreshRoutes,
                error -> LOG.error("Error in providerDB subscription", error));
    }

    @Override
    public Eventual<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        routerLock.readLock().lock();
        try {
            return router.handle(request, context);
        } finally {
            routerLock.readLock().unlock();
        }
    }

    private void refreshRoutes(ObjectStore<ProviderObjectRecord> db) {
        LOG.info("Refreshing provider admin endpoint routes");
        UrlPatternRouter newRouter = buildRouter(db);
        routerLock.writeLock().lock();
        try {
            router = newRouter;
        } finally {
            routerLock.writeLock().unlock();
        }
    }

    private static UrlPatternRouter buildRouter(ObjectStore<ProviderObjectRecord> db) {
        UrlPatternRouter.Builder routeBuilder = new UrlPatternRouter.Builder()
                .get("/admin/" + ADMIN_ROOT, new ProviderListHandler(db));
        db.entrySet().forEach(entry -> {
                String providerName = entry.getKey();
                entry.getValue().getStyxService().adminInterfaceHandlers(providerPath(providerName))
                        .forEach((relPath, handler) ->
                            routeBuilder.get(endpointPath(providerName, relPath), new HttpStreamer(MEGABYTE, handler))
                        );
        });


        return routeBuilder.build();
    }

    private static String providerPath(String providerName) {
        return adminPath(ADMIN_ROOT, providerName);
    }

    private static String endpointPath(String providerName, String endpointRelativePath) {
        return adminEndpointPath(ADMIN_ROOT, providerName, endpointRelativePath);
    }
}
