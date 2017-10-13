/**
 * Copyright (C) 2013-2017 Expedia Inc.
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

import com.hotels.styx.Environment;
import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import com.hotels.styx.api.client.retrypolicy.spi.RetryPolicy;
import com.hotels.styx.client.StyxHttpClient;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.client.loadbalancing.strategies.RoundRobinStrategy;
import com.hotels.styx.client.netty.connectionpool.NettyConnectionFactory;
import com.hotels.styx.client.retry.RetryNTimes;
import org.slf4j.Logger;

import static com.hotels.styx.serviceproviders.ServiceProvision.loadService;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Creates HTTP clients for connecting to backend services.
 */
public class StyxBackendServiceClientFactory implements BackendServiceClientFactory {
    private static final Logger LOG = getLogger(BackendServiceClientFactory.class);

    private final Environment environment;
    private final int clientWorkerThreadsCount;

    // Todo: This can be package private if/when backend service router is created in a separate builder in styx.proxy package.
    public StyxBackendServiceClientFactory(Environment environment, int clientWorkerThreadsCount) {
        this.environment = environment;
        this.clientWorkerThreadsCount = clientWorkerThreadsCount;
    }

    @Override
    public HttpClient createClient(BackendService backendService) {
        RetryPolicy retryPolicy = loadService(environment.configuration(), environment, "retrypolicy.policy.factory", RetryPolicy.class)
                .orElseGet(() -> defaultRetryPolicy(environment));

        LoadBalancingStrategy loadBalancingStrategy = loadService(environment.configuration(), environment, "loadBalancing.strategy.factory", LoadBalancingStrategy.class)
                .orElseGet(RoundRobinStrategy::new);

        boolean requestLoggingEnabled = environment.styxConfig().get("request-logging.outbound.enabled", Boolean.class)
                .orElse(false);

        boolean longFormat = environment.styxConfig().get("request-logging.outbound.longFormat", Boolean.class)
                .orElse(false);

        return new StyxHttpClient.Builder(backendService)
                .styxHeaderNames(environment.styxConfig().styxHeaderConfig())
                .version(environment.buildInfo().releaseVersion())
                .eventBus(environment.eventBus())
                .loadBalancingStrategy(loadBalancingStrategy)
                .originRestrictionCookie(environment.configuration().get("originRestrictionCookie").orElse(null))
                .metricsRegistry(environment.metricRegistry())
                .connectionFactory(new NettyConnectionFactory.Builder()
                        .name("Styx")
                        .clientWorkerThreadsCount(clientWorkerThreadsCount)
                        .tlsSettings(backendService.tlsSettings().orElse(null)).build())
                .retryPolicy(retryPolicy)
                .flowControlEnabled(true)
                .enableContentValidation()
                .rewriteRules(backendService.rewrites())
                .requestLoggingEnabled(requestLoggingEnabled)
                .longFormat(longFormat)
                .build();
    }

    private static RetryPolicy defaultRetryPolicy(com.hotels.styx.api.Environment environment) {
        RetryNTimes retryOnce = new RetryNTimes(1);
        LOG.warn("No configured retry policy found in {}. Using {}", environment.configuration(), retryOnce);
        return retryOnce;
    }
}
