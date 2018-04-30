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

import com.hotels.styx.Environment;
import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.HttpHandler2;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.client.Connection;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.metrics.MetricRegistry;
import com.hotels.styx.client.ConnectionSettings;
import com.hotels.styx.client.OriginStatsFactory;
import com.hotels.styx.client.OriginsInventory;
import com.hotels.styx.client.SimpleNettyHttpClient;
import com.hotels.styx.client.StyxHeaderConfig;
import com.hotels.styx.client.StyxHostHttpClient;
import com.hotels.styx.api.service.BackendService;
import com.hotels.styx.client.connectionpool.CloseAfterUseConnectionDestination;
import com.hotels.styx.client.connectionpool.ExpiringConnectionFactory;
import com.hotels.styx.client.connectionpool.ConnectionPoolFactory;
import com.hotels.styx.api.service.HealthCheckConfig;
import com.hotels.styx.client.healthcheck.OriginHealthCheckFunction;
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor;
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitorFactory;
import com.hotels.styx.client.healthcheck.UrlRequestHealthCheck;
import com.hotels.styx.client.netty.connectionpool.NettyConnectionFactory;
import com.hotels.styx.api.service.TlsSettings;
import com.hotels.styx.api.service.spi.Registry;
import com.hotels.styx.server.HttpRouter;
import org.slf4j.Logger;
import rx.Observable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.concat;
import static com.hotels.styx.client.HttpRequestOperationFactory.Builder.httpRequestOperationFactoryBuilder;
import static java.util.Comparator.comparingInt;
import static java.util.Comparator.naturalOrder;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A {@link HttpHandler2} implementation.
 */
public class BackendServicesRouter implements HttpRouter, Registry.ChangeListener<BackendService> {
    private static final Logger LOG = getLogger(BackendServicesRouter.class);

    private final BackendServiceClientFactory clientFactory;
    private final Environment environment;
    private final ConcurrentMap<String, ProxyToClientPipeline> routes;
    private final int clientWorkerThreadsCount;

    public BackendServicesRouter(BackendServiceClientFactory clientFactory, Environment environment) {
        this.clientFactory = checkNotNull(clientFactory);
        this.environment = environment;
        this.routes = new ConcurrentSkipListMap<>(
                comparingInt(String::length).reversed()
                        .thenComparing(naturalOrder()));
        this.clientWorkerThreadsCount = environment.styxConfig().proxyServerConfig().clientWorkerThreadsCount();
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
    public void onChange(Registry.Changes<BackendService> changes) {
        changes.removed().forEach(backendService -> routes.remove(backendService.path()).close());

        concat(changes.added(), changes.updated()).forEach(backendService -> {

            ProxyToClientPipeline pipeline = routes.get(backendService.path());
            if (pipeline != null) {
                pipeline.close();
            }

            boolean requestLoggingEnabled = environment.styxConfig().get("request-logging.outbound.enabled", Boolean.class)
                    .orElse(false);

            boolean longFormat = environment.styxConfig().get("request-logging.outbound.longFormat", Boolean.class)
                    .orElse(false);

            OriginStatsFactory originStatsFactory = new OriginStatsFactory(environment.metricRegistry());
            ConnectionPool.Settings poolSettings = backendService.connectionPoolConfig();

            Connection.Factory connectionFactory = connectionFactory(
                    backendService,
                    requestLoggingEnabled,
                    longFormat,
                    originStatsFactory,
                    poolSettings.connectionExpirationSeconds());

            ConnectionPool.Factory connectionPoolFactory = new ConnectionPoolFactory.Builder()
                    .connectionFactory(connectionFactory)
                    .connectionPoolSettings(backendService.connectionPoolConfig())
                    .metricRegistry(environment.metricRegistry())
                    .build();

            OriginHealthStatusMonitor healthStatusMonitor = new OriginHealthStatusMonitorFactory()
                    .create(backendService.id(),
                            backendService.healthCheckConfig(),
                            () -> originHealthCheckFunction(
                                    backendService.id(),
                                    environment.metricRegistry(),
                                    backendService.tlsSettings(),
                                    backendService.connectionPoolConfig(),
                                    backendService.healthCheckConfig(),
                                    environment.buildInfo().releaseVersion()
                            ));

            StyxHostHttpClient.Factory hostClientFactory = (ConnectionPool connectionPool) -> {
                StyxHeaderConfig headerConfig = environment.styxConfig().styxHeaderConfig();
                return StyxHostHttpClient.create(backendService.id(), connectionPool.getOrigin().id(), headerConfig.originIdHeaderName(), connectionPool);
            };

            //TODO: origins inventory builder assumes that appId/originId tuple is unique and it will fail on metrics registration.
            OriginsInventory inventory = new OriginsInventory.Builder(backendService.id())
                    .eventBus(environment.eventBus())
                    .metricsRegistry(environment.metricRegistry())
                    .connectionPoolFactory(connectionPoolFactory)
                    .originHealthMonitor(healthStatusMonitor)
                    .initialOrigins(backendService.origins())
                    .hostClientFactory(hostClientFactory)
                    .build();

            pipeline = new ProxyToClientPipeline(newClientHandler(backendService, inventory, originStatsFactory), inventory);

            routes.put(backendService.path(), pipeline);
            LOG.info("added path={} current routes={}", backendService.path(), routes.keySet());

        });
    }

    private Connection.Factory connectionFactory(
            BackendService backendService,
            boolean requestLoggingEnabled,
            boolean longFormat,
            OriginStatsFactory originStatsFactory,
            long connectionExpiration) {
        Connection.Factory factory = new NettyConnectionFactory.Builder()
                .name("Styx")
                .httpRequestOperationFactory(
                        httpRequestOperationFactoryBuilder()
                                .flowControlEnabled(true)
                                .originStatsFactory(originStatsFactory)
                                .responseTimeoutMillis(backendService.responseTimeoutMillis())
                                .requestLoggingEnabled(requestLoggingEnabled)
                                .longFormat(longFormat)
                                .build()
                )
                .clientWorkerThreadsCount(clientWorkerThreadsCount)
                .tlsSettings(backendService.tlsSettings().orElse(null))
                .build();

        if (connectionExpiration > 0) {
            return new ExpiringConnectionFactory(connectionExpiration, factory);
        } else {
            return factory;
        }
    }

    private HttpClient newClientHandler(BackendService backendService, OriginsInventory originsInventory, OriginStatsFactory originStatsFactory) {
        return clientFactory.createClient(backendService, originsInventory, originStatsFactory);
    }

    private static OriginHealthCheckFunction originHealthCheckFunction(
            Id appId,
            MetricRegistry metricRegistry,
            Optional<TlsSettings> tlsSettings,
            ConnectionPool.Settings connectionPoolSettings,
            HealthCheckConfig healthCheckConfig,
            String styxVersion) {
        NettyConnectionFactory connectionFactory = new NettyConnectionFactory.Builder()
                .name("Health-Check-Monitor-" + appId)
                .tlsSettings(tlsSettings.orElse(null))
                .build();

        ConnectionSettings connectionSettings = new ConnectionSettings(
                connectionPoolSettings.connectTimeoutMillis());

        HttpClient client = new SimpleNettyHttpClient.Builder()
                .userAgent("Styx/" + styxVersion)
                .connectionDestinationFactory(
                        new CloseAfterUseConnectionDestination.Factory()
                                .connectionSettings(connectionSettings)
                                .connectionFactory(connectionFactory))
                .build();

        String healthCheckUri = healthCheckConfig
                .uri()
                .orElseThrow(() -> new IllegalArgumentException("Health check URI missing for " + appId));

        return new UrlRequestHealthCheck(healthCheckUri, client, metricRegistry);
    }

    private static class ProxyToClientPipeline implements HttpHandler2 {
        private final HttpClient client;
        private final OriginsInventory originsInventory;

        ProxyToClientPipeline(HttpClient httpClient, OriginsInventory originsInventory) {
            this.client = checkNotNull(httpClient);
            this.originsInventory = originsInventory;
        }

        @Override
        public Observable<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
            return client.sendRequest(request)
                    .doOnError(throwable -> handleError(request, throwable));
        }

        public void close() {
            originsInventory.close();
        }

        private static void handleError(HttpRequest request, Throwable throwable) {
            LOG.error("Error proxying request={} exceptionClass={} exceptionMessage=\"{}\"", new Object[]{request, throwable.getClass().getName(), throwable.getMessage()});
        }

    }
}
