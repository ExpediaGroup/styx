/**
 * Copyright (C) 2013-2018 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.proxy;

import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.HttpHandler2;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.infrastructure.Registry;
import com.hotels.styx.proxy.backends.CommonBackendServiceRegistry.StyxBackendService;
import com.hotels.styx.server.HttpRouter;
import org.slf4j.Logger;
import rx.Observable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.concat;
import static java.lang.String.format;
import static java.util.Comparator.comparingInt;
import static java.util.Comparator.naturalOrder;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A {@link HttpHandler2} implementation.
 */
public class BackendServicesRouter implements HttpRouter, Registry.ChangeListener<StyxBackendService> {
    private static final Logger LOG = getLogger(BackendServicesRouter.class);

    private final ConcurrentMap<String, ProxyToClientPipeline> routes;

    public BackendServicesRouter() {
        this.routes = new ConcurrentSkipListMap<>(
                comparingInt(String::length).reversed()
                        .thenComparing(naturalOrder()));
    }

    ConcurrentMap<String, ProxyToClientPipeline> routes() {
        return routes;
    }

    @Override
    public Optional<HttpHandler2> route(HttpRequest request) {
        String path = request.path();

        return routes.entrySet().stream()
                .filter(entry -> path.startsWith(entry.getKey()))
                .findFirst()
                .map(Map.Entry::getValue);
    }

    @Override
    public void onChange(Registry.Changes<StyxBackendService> changes) {
        changes.removed().forEach(
                backendService -> {
                    routes.remove(backendService.configuration().path());
                    LOG.info("removed path={} current routes={}", backendRoute(backendService), routes.keySet());
                }
        );

        concat(changes.added(), changes.updated()).forEach(backendService -> {
            ProxyToClientPipeline handler = new ProxyToClientPipeline(backendService.httpClient());

            routes.put(backendService.configuration().path(), handler);
            LOG.info("added {} current routes={}", backendRoute(backendService), routes.keySet());

        });
    }

    private String backendRoute(StyxBackendService backendService) {
        return format("'%s' -> '%s'", backendService.configuration().path(), backendService.id());
    }

    @Override
    public void onError(Throwable ex) {
        LOG.warn("Error from registry", ex);
    }

    private static class ProxyToClientPipeline implements HttpHandler2 {
        private final HttpClient client;

        ProxyToClientPipeline(HttpClient httpClient) {
            this.client = checkNotNull(httpClient);
        }

        @Override
        public Observable<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
            Observable<HttpResponse> httpResponseObservable = client.sendRequest(request);
            return httpResponseObservable
                    .doOnError(throwable -> handleError(request, throwable));
        }

        private static void handleError(HttpRequest request, Throwable throwable) {
            LOG.error("Error proxying request={} exceptionClass={} exceptionMessage=\"{}\"", new Object[]{request, throwable.getClass().getName(), throwable.getMessage()});
        }

    }
}
