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
package com.hotels.styx.routing.handlers

import com.fasterxml.jackson.annotation.JsonProperty
import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.Id
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.configuration.ObjectStore
import com.hotels.styx.api.extension.ActiveOrigins
import com.hotels.styx.api.extension.Origin.newOriginBuilder
import com.hotels.styx.api.extension.RemoteHost
import com.hotels.styx.api.extension.RemoteHost.remoteHost
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancingMetricSupplier
import com.hotels.styx.api.extension.service.StickySessionConfig
import com.hotels.styx.client.OriginRestrictionLoadBalancingStrategy
import com.hotels.styx.client.StyxBackendServiceClient
import com.hotels.styx.client.loadbalancing.strategies.PowerOfTwoStrategy
import com.hotels.styx.client.stickysession.StickySessionLoadBalancingStrategy
import com.hotels.styx.config.schema.SchemaDsl.`object`
import com.hotels.styx.config.schema.SchemaDsl.bool
import com.hotels.styx.config.schema.SchemaDsl.field
import com.hotels.styx.config.schema.SchemaDsl.integer
import com.hotels.styx.config.schema.SchemaDsl.optional
import com.hotels.styx.config.schema.SchemaDsl.string
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig
import com.hotels.styx.lbGroupTag
import com.hotels.styx.routing.RoutingObject
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.config.RoutingObjectFactory
import com.hotels.styx.routing.config.StyxObjectDefinition
import com.hotels.styx.stateTag
import org.slf4j.LoggerFactory
import reactor.core.Disposable
import reactor.core.publisher.toFlux
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.atomic.AtomicReference

/**
 * Balances load between other routing objects.
 * It has been designed to work with {@link PathPrefixRouter} and {@link HostProxy}
 * objects to implement application routing capability. In reality it will work
 * with any routing object.
 */
internal class LoadBalancingGroup(val client: StyxBackendServiceClient, val changeWatcher: Disposable) : RoutingObject {

    override fun handle(request: LiveHttpRequest, context: HttpInterceptor.Context) = Eventual(client.sendRequest(request, context))

    override fun stop(): CompletableFuture<Void> {
        changeWatcher.dispose()
        return completedFuture(null)
    }

    companion object {
        val SCHEMA = `object`(
                field("origins", string()),
                optional("originRestrictionCookie", string()),
                optional("stickySession", `object`(
                        field("enabled", bool()),
                        field("timeoutSeconds", integer())
                ))
        )

        private val LOGGER = LoggerFactory.getLogger(LoadBalancingGroup::class.java)
    }

    class Factory : RoutingObjectFactory {
        override fun build(fullName: List<String>, context: RoutingObjectFactory.Context, configBlock: StyxObjectDefinition): RoutingObject {

            val appId = fullName.last()
            val config = JsonNodeConfig(configBlock.config()).`as`(Config::class.java)

            val routeDb = context.routeDb()
            val remoteHosts = AtomicReference<Set<RemoteHost>>(setOf())

            val watch = routeDb.watch()
                    .toFlux()
                    .subscribe(
                            { routeDatabaseChanged(config.origins, it, remoteHosts) },
                            { watchFailed(appId, it) },
                            { watchCompleted(appId) }
                    )


            val client = StyxBackendServiceClient.Builder(Id.id(appId))
                    .loadBalancer(loadBalancer(config, ActiveOrigins { remoteHosts.get() }))
                    .metricsRegistry(context.environment().metricRegistry())
                    .originIdHeader(context.environment().configuration().styxHeaderConfig().originIdHeaderName())
                    .stickySessionConfig(config.stickySession ?: StickySessionConfig.stickySessionDisabled())
                    .originsRestrictionCookieName(config.originRestrictionCookie)
                    .build()

            return LoadBalancingGroup(client, watch)
        }

        private fun loadBalancer(config: Config, activeOrigins: ActiveOrigins): LoadBalancer {
            val loadBalancer = PowerOfTwoStrategy(activeOrigins)
            return if (config.stickySessionConfig.stickySessionEnabled()) {
                StickySessionLoadBalancingStrategy(activeOrigins, loadBalancer)
            } else if (config.originRestrictionCookie == null) {
                loadBalancer
            } else {
                OriginRestrictionLoadBalancingStrategy(activeOrigins, loadBalancer)
            }
        }

        private fun routeDatabaseChanged(appId: String, snapshot: ObjectStore<RoutingObjectRecord>, remoteHosts: AtomicReference<Set<RemoteHost>>) {
            val newSet = snapshot.entrySet()
                    .filter { it.value.tags.contains(lbGroupTag(appId)) }
                    .filter { stateTag.find(it.value.tags)
                            .let { it == null || it == "active" }
                    }
                    .map { toRemoteHost(appId, it) }
                    .toSet()

            remoteHosts.set(newSet)
        }

        private fun toRemoteHost(appId: String, record: Map.Entry<String, RoutingObjectRecord>): RemoteHost {
            val routingObject = record.value.routingObject
            val originName = record.key

            return remoteHost(
                    // The origin is used to determine remote host hostname or port
                    // therefore we'll just pass NA:0
                    newOriginBuilder("NA", 0)
                            .applicationId(appId)
                            .id(originName)
                            .build(),
                    routingObject,
                    LoadBalancingMetricSupplier { routingObject.metric() })
        }

        private fun watchFailed(name: String, cause: Throwable) {
            LOGGER.error("{}: Illegal state: watch error. Cause={}", name, cause)
        }

        private fun watchCompleted(name: String) {
            LOGGER.error("{}: Illegal state: watch completed", name)
        }
    }

    data class Config(
            @JsonProperty val origins: String,
            @JsonProperty val originRestrictionCookie: String?,
            @JsonProperty val stickySession: StickySessionConfig?
    ) {
        val stickySessionConfig: StickySessionConfig
            get() = stickySession ?: StickySessionConfig.stickySessionDisabled()
    }

}
