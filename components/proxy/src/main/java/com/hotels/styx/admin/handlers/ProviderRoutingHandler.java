/*
  Copyright (C) 2013-2020 Expedia Inc.

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

import com.hotels.styx.StyxObjectRecord;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.WebServiceHandler;
import com.hotels.styx.api.configuration.ObjectStore;
import com.hotels.styx.api.extension.service.spi.StyxService;
import com.hotels.styx.common.http.handler.HttpStreamer;
import com.hotels.styx.routing.db.StyxObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * Routes admin requests to the admin endpoints of each {@link com.hotels.styx.api.extension.service.spi.StyxService}
 * in the Provider {@link ObjectStore}, and to the index page that organizes and lists these endpoints.
 * This handler registers as a watcher on the store, and will keep the endpoint list up to date as the store data changes.
 */
public class ProviderRoutingHandler implements WebServiceHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProviderRoutingHandler.class);
    private static final int MEGABYTE = 1024 * 1024;

    private final String pathPrefix;
    private volatile UrlPatternRouter router;

    /**
     * Create a new handler for the given provider object store, with provider admin URLs mounted
     * under the path "pathPrefix/providerName".
     * @param pathPrefix the path prefix added to each provider admin URL
     * @param providerDb the provider object store
     */
    public ProviderRoutingHandler(String pathPrefix, StyxObjectStore<StyxObjectRecord<StyxService>> providerDb) {
        this.pathPrefix = pathPrefix;
        Flux.from(providerDb.watch()).subscribe(
                this::refreshRoutes,
                error -> LOG.error("Error in providerDB subscription", error));
    }

    @Override
    public Eventual<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        return router.handle(request, context);
    }

    private void refreshRoutes(ObjectStore<StyxObjectRecord<StyxService>> db) {
        LOG.info("Refreshing provider admin endpoint routes");
        router = buildRouter(db);
    }

    private UrlPatternRouter buildRouter(ObjectStore<StyxObjectRecord<StyxService>> db) {
        UrlPatternRouter.Builder routeBuilder = new UrlPatternRouter.Builder(pathPrefix)
                .get("", new ProviderListHandler(db));
        db.entrySet().forEach(entry -> {
                String providerName = entry.getKey();
                entry.getValue().getStyxService().adminInterfaceHandlers(pathPrefix + "/" + providerName)
                        .forEach((relPath, handler) ->
                            routeBuilder.get(providerName + "/" + relPath, new HttpStreamer(MEGABYTE, handler))
                        );
        });

        return routeBuilder.build();
    }
}
