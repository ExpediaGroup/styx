/*
  Copyright (C) 2013-2022 Expedia Inc.

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
package com.hotels.styx.proxy

import com.hotels.styx.Environment
import com.hotels.styx.api.configuration.Configuration
import com.hotels.styx.client.OriginsInventory
import com.hotels.styx.client.OriginStatsFactory
import com.hotels.styx.client.BackendServiceClient
import com.hotels.styx.api.extension.retrypolicy.spi.RetryPolicy
import com.hotels.styx.serviceproviders.ServiceProvision
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer
import com.hotels.styx.api.extension.service.BackendService
import com.hotels.styx.client.loadbalancing.strategies.BusyConnectionsStrategy
import com.hotels.styx.client.StyxBackendServiceClient
import com.hotels.styx.client.stickysession.StickySessionLoadBalancingStrategy
import com.hotels.styx.client.OriginRestrictionLoadBalancingStrategy
import com.hotels.styx.client.retry.RetryNTimes
import org.slf4j.LoggerFactory

/**
 * Creates HTTP clients for connecting to backend services.
 */
class StyxBackendServiceClientFactory(    // Todo: This can be package private if/when backend service router is created in a separate builder in styx.proxy package.
    private val environment: Environment
) : BackendServiceClientFactory {

    override fun createClient(
        backendService: BackendService,
        originsInventory: OriginsInventory,
        originStatsFactory: OriginStatsFactory
    ): BackendServiceClient {
        val styxConfig: Configuration = environment.configuration()
        val originRestrictionCookie = styxConfig["originRestrictionCookie"].orElse(null)
        val stickySessionEnabled = backendService.stickySessionConfig().stickySessionEnabled()
        val retryPolicy = ServiceProvision.loadRetryPolicy(
            styxConfig, environment, "retrypolicy.policy.factory", RetryPolicy::class.java
        ).orElseGet { defaultRetryPolicy() }
        val configuredLbStrategy = ServiceProvision.loadLoadBalancer(
            styxConfig, environment, "loadBalancing.strategy.factory", LoadBalancer::class.java, originsInventory
        ).orElseGet { BusyConnectionsStrategy(originsInventory) }

        // TODO: Ensure that listeners are also unregistered:
        // We are going to revamp how we handle origins, https://github.com/HotelsDotCom/styx/issues/197
        originsInventory.addOriginsChangeListener(configuredLbStrategy)
        val loadBalancingStrategy = decorateLoadBalancer(
            configuredLbStrategy,
            stickySessionEnabled,
            originsInventory,
            originRestrictionCookie
        )
        return StyxBackendServiceClient(
            id = backendService.id(),
            rewriteRules = backendService.rewrites(),
            originStatsFactory = originStatsFactory,
            originsRestrictionCookieName = originRestrictionCookie,
            stickySessionConfig = backendService.stickySessionConfig(),
            originIdHeader = environment.configuration().styxHeaderConfig().originIdHeaderName(),
            loadBalancer = loadBalancingStrategy,
            retryPolicy = retryPolicy,
            metrics = environment.centralisedMetrics(),
            overrideHostHeader = backendService.isOverrideHostHeader()
        )
    }

    private fun decorateLoadBalancer(
        configuredLbStrategy: LoadBalancer,
        stickySessionEnabled: Boolean,
        originsInventory: OriginsInventory,
        originRestrictionCookie: String?
    ): LoadBalancer =
        if (stickySessionEnabled) {
            StickySessionLoadBalancingStrategy(originsInventory, configuredLbStrategy)
        } else if (originRestrictionCookie == null) {
            LOGGER.info("originRestrictionCookie not specified - origin restriction disabled")
            configuredLbStrategy
        } else {
            LOGGER.info(
                "originRestrictionCookie specified as {} - origin restriction will apply when this cookie is sent",
                originRestrictionCookie
            )
            OriginRestrictionLoadBalancingStrategy(originsInventory, configuredLbStrategy)
        }


    companion object {
        private val LOGGER = LoggerFactory.getLogger(BackendServiceClientFactory::class.java)
        private fun defaultRetryPolicy(): RetryPolicy {
            val retryOnce = RetryNTimes(1)
            LOGGER.warn("No configured retry policy found, using {}", retryOnce)
            return retryOnce
        }
    }
}
