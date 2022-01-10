/*
  Copyright (C) 2013-2021 Expedia Inc.

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
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer;
import com.hotels.styx.api.extension.retrypolicy.spi.RetryPolicy;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.client.BackendServiceClient;
import com.hotels.styx.client.OriginRestrictionLoadBalancingStrategy;
import com.hotels.styx.client.OriginStatsFactory;
import com.hotels.styx.client.OriginsInventory;
import com.hotels.styx.client.StyxBackendServiceClient;
import com.hotels.styx.client.loadbalancing.strategies.BusyConnectionsStrategy;
import com.hotels.styx.client.retry.RetryNTimes;
import com.hotels.styx.client.stickysession.StickySessionLoadBalancingStrategy;
import org.slf4j.Logger;

import static com.hotels.styx.serviceproviders.ServiceProvision.loadLoadBalancer;
import static com.hotels.styx.serviceproviders.ServiceProvision.loadRetryPolicy;
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
    public BackendServiceClient createClient(BackendService backendService, OriginsInventory originsInventory, OriginStatsFactory originStatsFactory) {
        Configuration styxConfig = environment.configuration();

        String originRestrictionCookie = styxConfig.get("originRestrictionCookie").orElse(null);
        boolean stickySessionEnabled = backendService.stickySessionConfig().stickySessionEnabled();

        RetryPolicy retryPolicy = loadRetryPolicy(styxConfig, environment, "retrypolicy.policy.factory", RetryPolicy.class)
                .orElseGet(() -> defaultRetryPolicy(environment));

        LoadBalancer configuredLbStrategy = loadLoadBalancer(
                styxConfig, environment, "loadBalancing.strategy.factory", LoadBalancer.class, originsInventory)
                .orElseGet(() -> new BusyConnectionsStrategy(originsInventory));

        // TODO: Ensure that listeners are also unregistered:
        // We are going to revamp how we handle origins, https://github.com/HotelsDotCom/styx/issues/197
        originsInventory.addOriginsChangeListener(configuredLbStrategy);

        LoadBalancer loadBalancingStrategy = decorateLoadBalancer(
                configuredLbStrategy,
                stickySessionEnabled,
                originsInventory,
                originRestrictionCookie
        );

        return new StyxBackendServiceClient.Builder(backendService.id())
                .loadBalancer(loadBalancingStrategy)
                .stickySessionConfig(backendService.stickySessionConfig())
                .metrics(environment.centralisedMetrics())
                .retryPolicy(retryPolicy)
                .rewriteRules(backendService.rewrites())
                .originStatsFactory(originStatsFactory)
                .originsRestrictionCookieName(originRestrictionCookie)
                .originIdHeader(environment.styxConfig().styxHeaderConfig().originIdHeaderName())
                .overrideHostHeader(backendService.isOverrideHostHeader())
                .build();
    }

    private LoadBalancer decorateLoadBalancer(LoadBalancer configuredLbStrategy, boolean stickySessionEnabled, OriginsInventory originsInventory, String originRestrictionCookie) {
        if (stickySessionEnabled) {
            return new StickySessionLoadBalancingStrategy(originsInventory, configuredLbStrategy);
        } else if (originRestrictionCookie == null) {
            LOGGER.info("originRestrictionCookie not specified - origin restriction disabled");
            return configuredLbStrategy;
        } else {
            LOGGER.info("originRestrictionCookie specified as {} - origin restriction will apply when this cookie is sent", originRestrictionCookie);
            return new OriginRestrictionLoadBalancingStrategy(originsInventory, configuredLbStrategy);
        }
    }

    private static RetryPolicy defaultRetryPolicy(com.hotels.styx.api.Environment environment) {
        RetryNTimes retryOnce = new RetryNTimes(1);
        LOGGER.warn("No configured retry policy found in {}. Using {}", environment.configuration(), retryOnce);
        return retryOnce;
    }
}
