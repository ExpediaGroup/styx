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

import com.hotels.styx.Environment;
import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import com.hotels.styx.api.client.retrypolicy.spi.RetryPolicy;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.client.OriginRestrictionLoadBalancingStrategy;
import com.hotels.styx.client.OriginStatsFactory;
import com.hotels.styx.client.OriginsInventory;
import com.hotels.styx.client.StyxHttpClient;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.client.loadbalancing.strategies.RoundRobinStrategy;
import com.hotels.styx.client.retry.RetryNTimes;
import com.hotels.styx.client.stickysession.StickySessionLoadBalancingStrategy;
import org.slf4j.Logger;

import static com.hotels.styx.serviceproviders.ServiceProvision.loadService;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Creates HTTP clients for connecting to backend services.
 */
public class StyxBackendServiceClientFactory implements BackendServiceClientFactory {
    private static final Logger LOGGER = getLogger(BackendServiceClientFactory.class);

    private final Environment environment;

    // Todo: This can be package private if/when backend service router is created in a separate builder in styx.proxy package.
    public StyxBackendServiceClientFactory(Environment environment) {
        this.environment = environment;
    }

    @Override
    public HttpClient createClient(BackendService backendService, OriginsInventory originsInventory, OriginStatsFactory originStatsFactory) {
        Configuration styxConfig = environment.configuration();
        String originRestrictionCookie = styxConfig.get("originRestrictionCookie").orElse(null);
        boolean stickySessionEnabled = backendService.stickySessionConfig().stickySessionEnabled();

        RetryPolicy retryPolicy = loadService(styxConfig, environment, "retrypolicy.policy.factory", RetryPolicy.class)
                .orElseGet(() -> defaultRetryPolicy(environment));

        LoadBalancingStrategy configuredLbStrategy = loadService(
                styxConfig, environment, "loadBalancing.strategy.factory", LoadBalancingStrategy.class, originsInventory)
                .orElseGet(() -> new RoundRobinStrategy(originsInventory));

        originsInventory.addInventoryStateChangeListener(configuredLbStrategy);

        LoadBalancingStrategy loadBalancingStrategy = decorateLoadBalancer(
                configuredLbStrategy,
                stickySessionEnabled,
                originsInventory,
                originRestrictionCookie
        );

        return new StyxHttpClient.Builder(backendService)
                .loadBalancingStrategy(loadBalancingStrategy)
                .metricsRegistry(environment.metricRegistry())
                .retryPolicy(retryPolicy)
                .enableContentValidation()
                .rewriteRules(backendService.rewrites())
                .originStatsFactory(originStatsFactory)
                .build();
    }

    private LoadBalancingStrategy decorateLoadBalancer(LoadBalancingStrategy configuredLbStrategy, boolean stickySessionEnabled, OriginsInventory originsInventory, String originRestrictionCookie) {
        if (stickySessionEnabled) {
            return new StickySessionLoadBalancingStrategy(originsInventory, configuredLbStrategy);
        } else if (originRestrictionCookie == null) {
            LOGGER.info("originRestrictionCookie not specified - origin restriction disabled");
            return configuredLbStrategy;
        } else {
            LOGGER.info("originRestrictionCookie specified as {} - origin restriction will apply when this cookie is sent", originRestrictionCookie);
            return new OriginRestrictionLoadBalancingStrategy(originsInventory, configuredLbStrategy, originRestrictionCookie);
        }
    }

    private static RetryPolicy defaultRetryPolicy(com.hotels.styx.api.Environment environment) {
        RetryNTimes retryOnce = new RetryNTimes(1);
        LOGGER.warn("No configured retry policy found in {}. Using {}", environment.configuration(), retryOnce);
        return retryOnce;
    }
}
