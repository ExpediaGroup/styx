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
package com.hotels.styx.proxy;

import com.hotels.styx.Environment;
import com.hotels.styx.NettyExecutor;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.ConnectionPoolSettings;
import com.hotels.styx.api.extension.service.HealthCheckConfig;
import com.hotels.styx.api.extension.service.spi.Registry;
import com.hotels.styx.client.BackendServiceClient;
import com.hotels.styx.client.Connection;
import com.hotels.styx.client.OriginStatsFactory;
import com.hotels.styx.client.OriginStatsFactory.CachingOriginStatsFactory;
import com.hotels.styx.client.OriginsInventory;
import com.hotels.styx.client.StyxHostHttpClient;
import com.hotels.styx.client.StyxHttpClient;
import com.hotels.styx.client.connectionpool.ConnectionPool;
import com.hotels.styx.client.connectionpool.ExpiringConnectionFactory;
import com.hotels.styx.client.connectionpool.SimpleConnectionPoolFactory;
import com.hotels.styx.client.healthcheck.OriginHealthCheckFunction;
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor;
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitorFactory;
import com.hotels.styx.client.healthcheck.UrlRequestHealthCheck;
import com.hotels.styx.client.netty.connectionpool.NettyConnectionFactory;
import com.hotels.styx.server.HttpRouter;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;

import static com.google.common.collect.Iterables.concat;
import static com.hotels.styx.client.HttpConfig.newHttpConfigBuilder;
import static com.hotels.styx.client.HttpRequestOperationFactory.Builder.httpRequestOperationFactoryBuilder;
import static java.util.Comparator.comparingInt;
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A {@link HttpHandler} implementation.
 */
public class BackendServicesRouter implements HttpRouter, Registry.ChangeListener<BackendService> {
    private static final Logger LOG = getLogger(BackendServicesRouter.class);

    private final BackendServiceClientFactory clientFactory;
    private final Environment environment;
    private final NettyExecutor executor;
    private final ConcurrentMap<String, ProxyToClientPipeline> routes;

    public BackendServicesRouter(BackendServiceClientFactory clientFactory,
                                 Environment environment,
                                 NettyExecutor executor) {
        this.clientFactory = requireNonNull(clientFactory);
        this.environment = requireNonNull(environment);
        this.executor = requireNonNull(executor);

        this.routes = new ConcurrentSkipListMap<>(
                comparingInt(String::length).reversed()
                        .thenComparing(naturalOrder()));
    }

    ConcurrentMap<String, ProxyToClientPipeline> routes() {
        return routes;
    }

    @Override
    public Optional<HttpHandler> route(LiveHttpRequest request, HttpInterceptor.Context ignore) {
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

            MetricRegistry originsMetrics = environment.metricRegistry().scope("origins");
            OriginStatsFactory originStatsFactory = new CachingOriginStatsFactory(originsMetrics);
            ConnectionPoolSettings poolSettings = backendService.connectionPoolConfig();

            Connection.Factory connectionFactory = connectionFactory(
                    backendService,
                    requestLoggingEnabled,
                    longFormat,
                    originStatsFactory,
                    poolSettings.connectionExpirationSeconds());

            ConnectionPool.Factory connectionPoolFactory = new SimpleConnectionPoolFactory.Builder()
                    .connectionFactory(connectionFactory)
                    .connectionPoolSettings(backendService.connectionPoolConfig())
                    .metricRegistry(originsMetrics)
                    .build();

            OriginHealthStatusMonitor healthStatusMonitor = healthStatusMonitor(backendService);

            OriginsInventory inventory = new OriginsInventory.Builder(backendService.id())
                    .eventBus(environment.eventBus())
                    .metricsRegistry(originsMetrics)
                    .connectionPoolFactory(connectionPoolFactory)
                    .originHealthMonitor(healthStatusMonitor)
                    .initialOrigins(backendService.origins())
                    .hostClientFactory(StyxHostHttpClient::create)
                    .build();

            pipeline = new ProxyToClientPipeline(newClientHandler(backendService, inventory, originStatsFactory), () -> {
                inventory.close();
                healthStatusMonitor.stop();
            });

            routes.put(backendService.path(), pipeline);
            LOG.info("added path={} current routes={}", backendService.path(), routes.keySet());
        });
    }

    private OriginHealthStatusMonitor healthStatusMonitor(BackendService backendService) {
        return new OriginHealthStatusMonitorFactory()
                        .create(backendService.id(),
                                backendService.healthCheckConfig(),
                                () -> originHealthCheckFunction(
                                        backendService.id(),
                                        environment.metricRegistry(),
                                        backendService.healthCheckConfig()),
                                healthCheckClient(backendService));
    }

    private StyxHttpClient healthCheckClient(BackendService backendService) {
        StyxHttpClient.Builder builder = new StyxHttpClient.Builder()
                .connectTimeout(backendService.connectionPoolConfig().connectTimeoutMillis(), MILLISECONDS)
                .userAgent("Styx/" + environment.buildInfo().releaseVersion());

        backendService.tlsSettings().ifPresent(builder::tlsSettings);

        return builder.build();
    }

    private Connection.Factory connectionFactory(
            BackendService backendService,
            boolean requestLoggingEnabled,
            boolean longFormat,
            OriginStatsFactory originStatsFactory,
            long connectionExpiration) {

        Connection.Factory factory = new NettyConnectionFactory.Builder()
                .executor(executor)
                .httpRequestOperationFactory(
                        httpRequestOperationFactoryBuilder()
                                .flowControlEnabled(true)
                                .originStatsFactory(originStatsFactory)
                                .responseTimeoutMillis(backendService.responseTimeoutMillis())
                                .requestLoggingEnabled(requestLoggingEnabled)
                                .longFormat(longFormat)
                                .httpMessageFormatter(environment.httpMessageFormatter())
                                .build()
                )
                .tlsSettings(backendService.tlsSettings().orElse(null))
                .httpConfig(newHttpConfigBuilder().setMaxHeadersSize(backendService.maxHeaderSize()).build())
                .build();

        if (connectionExpiration > 0) {
            return new ExpiringConnectionFactory(connectionExpiration, factory);
        } else {
            return factory;
        }
    }

    private HttpHandler newClientHandler(BackendService backendService, OriginsInventory originsInventory, OriginStatsFactory originStatsFactory) {
        BackendServiceClient client = clientFactory.createClient(backendService, originsInventory, originStatsFactory);
        return (request, context) -> new Eventual<>(client.sendRequest(request, context));
    }

    private static OriginHealthCheckFunction originHealthCheckFunction(
            Id appId,
            MetricRegistry metricRegistry,
            HealthCheckConfig healthCheckConfig) {

        String healthCheckUri = healthCheckConfig
                .uri()
                .orElseThrow(() -> new IllegalArgumentException("Health check URI missing for " + appId));

        return new UrlRequestHealthCheck(healthCheckUri, metricRegistry);
    }

    private static class ProxyToClientPipeline implements HttpHandler {
        private final HttpHandler client;
        private final Runnable onClose;

        ProxyToClientPipeline(HttpHandler httpClient, Runnable onClose) {
            this.client = requireNonNull(httpClient);
            this.onClose = requireNonNull(onClose);
        }

        @Override
        public Eventual<LiveHttpResponse> handle(LiveHttpRequest request, HttpInterceptor.Context context) {
            return client.handle(request, context);
        }

        public void close() {
            onClose.run();
        }
    }
}
