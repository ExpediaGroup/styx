/*
  Copyright (C) 2013-2025 Expedia Inc.

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
package com.hotels.styx.services

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.hotels.styx.HEALTHCHECK_FAILING
import com.hotels.styx.HEALTHCHECK_PASSING
import com.hotels.styx.NettyExecutor
import com.hotels.styx.ProviderObjectRecord
import com.hotels.styx.STATE_ACTIVE
import com.hotels.styx.STATE_UNREACHABLE
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.extension.service.spi.AbstractStyxService
import com.hotels.styx.api.extension.service.spi.StyxService
import com.hotels.styx.config.schema.SchemaDsl
import com.hotels.styx.config.schema.SchemaDsl.field
import com.hotels.styx.config.schema.SchemaDsl.integer
import com.hotels.styx.config.schema.SchemaDsl.optional
import com.hotels.styx.config.schema.SchemaDsl.string
import com.hotels.styx.healthCheckTag
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig
import com.hotels.styx.lbGroupTag
import com.hotels.styx.routing.RoutingObject
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.config.RoutingObjectFactory
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.server.HttpInterceptorContext
import com.hotels.styx.serviceproviders.ServiceProviderFactory
import com.hotels.styx.services.HealthCheckMonitoringService.Companion.EXECUTOR
import com.hotels.styx.stateTag
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.kotlin.core.publisher.toMono
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicReference

internal class HealthCheckMonitoringService(
        private val objectStore: StyxObjectStore<RoutingObjectRecord>,
        private val application: String,
        urlPath: String,
        private val period: Duration,
        activeThreshold: Int,
        inactiveThreshold: Int,
        private val executor: ScheduledExecutorService,
        workerExecutor: NettyExecutor = healthCheckExecutor) : AbstractStyxService("HealthCheckMonitoringService-$application") {

    companion object {
        @JvmField
        val SCHEMA = SchemaDsl.`object`(
                field("objects", string()),
                optional("path", string()),
                optional("timeoutMillis", integer()),
                optional("intervalMillis", integer()),
                optional("healthyThreshold", integer()),
                optional("unhealthyThreshold", integer())
        )

        internal val EXECUTOR = ScheduledThreadPoolExecutor(2)

        internal val LOGGER = LoggerFactory.getLogger(HealthCheckMonitoringService::class.java)
    }

    private val probe = urlProbe(
            HttpRequest.get(urlPath).build(),
            Duration.ofMillis(1000),
            HttpInterceptorContext(false, null, workerExecutor.eventLoopGroup))

    private val determineObjectState = healthCheckFunction(activeThreshold, inactiveThreshold)
    private val futureRef: AtomicReference<ScheduledFuture<*>> = AtomicReference()

    override fun startService() = CompletableFuture.runAsync {
        LOGGER.info("started service for {} - {} - {}", application, period.toMillis(), period.toMillis())
        futureRef.set(executor.scheduleAtFixedRate(
                { runChecks(application, objectStore) },
                period.toMillis(),
                period.toMillis(),
                MILLISECONDS))
    }

    override fun stopService() = CompletableFuture.runAsync {
        LOGGER.info("stopped service for {}", application)

        objectStore.entrySet()
                .filter(::containsRelevantStateTag)
                .forEach { (name, record) ->
                    objectStore.compute(name) { previous ->
                        if (previous == null) return@compute null

                        val newTags = previous.tags
                                .let { healthCheckTag.remove(it) }
                                .let { stateTag.remove(it) }
                                .plus(stateTag(STATE_ACTIVE))

                        if (previous.tags != newTags)
                            previous.copy(tags = newTags)
                        else
                            previous
                    }
                }
        futureRef.get().cancel(false)
    }

    fun isRunning() = futureRef.get()?.let { !it.isCancelled && !it.isDone } == true

    internal fun runChecks(application: String, objectStore: StyxObjectStore<RoutingObjectRecord>) {
        val monitoredObjects = objectStore.entrySet()
                .map { Pair(it.key, it.value) }
                .filter { (_, record) -> record.tags.contains(lbGroupTag(application)) }
                .map { (name, record) ->
                    val tags = record.tags
                    val objectHealth = objectHealthFrom(stateTag.find(tags), healthCheckTag.find(tags))
                    Triple(name, record, objectHealth)
                }

        val pendingHealthChecks = monitoredObjects
                .map { (name, record, objectHealth) ->
                    healthCheck(probe, record.routingObject, objectHealth)
                            .map { newHealth -> Triple(name, objectHealth, newHealth) }
                            .doOnNext { (name, currentHealth, newHealth) ->
                                if (currentHealth != newHealth) {
                                    markObject(objectStore, name, newHealth)
                                }
                            }
                }

        Flux.fromIterable(pendingHealthChecks)
                .flatMap { it }
                .collectList()
                .subscribe {
                    val details = it.joinToString(", ") { (name, _, health2) ->
                        "{ app: $application, host: $name, result: $health2 }"
                    }

                    LOGGER.debug("Health Check Completed: ${details}")
                }
    }

    private fun healthCheck(probe: Probe, routingObject: RoutingObject, previous: ObjectHealth) =
            probe(routingObject)
                    .toMono()
                    .map { reachable -> determineObjectState(previous, reachable) }
}

internal data class HealthCheckConfiguration(
        @JsonProperty val objects: String,
        @JsonProperty val path: String,
        @JsonProperty val timeoutMillis: Long,
        @JsonProperty val intervalMillis: Long,
        @JsonProperty val healthyThreshod: Int,
        @JsonProperty val unhealthyThreshold: Int)

private val healthCheckExecutor = NettyExecutor.create("HealthCheckMonitoringService-global", 1)

internal class HealthCheckMonitoringServiceFactory : ServiceProviderFactory {
    override fun create(name: String, context: RoutingObjectFactory.Context, configuration: JsonNode, serviceDb: StyxObjectStore<ProviderObjectRecord>): StyxService {
        val config = JsonNodeConfig(configuration).`as`(HealthCheckConfiguration::class.java)

        return HealthCheckMonitoringService(
                context.routeDb(),
                config.objects,
                config.path,
                Duration.ofMillis(config.intervalMillis),
                config.healthyThreshod,
                config.unhealthyThreshold,
                EXECUTOR,
                healthCheckExecutor)
    }
}

internal fun objectHealthFrom(state: String?, health: Pair<String, Int>?) =
        when {
            state == STATE_ACTIVE && (health?.first == HEALTHCHECK_FAILING && health.second >= 0) -> {
                ObjectActive(health.second)
            }

            state == STATE_UNREACHABLE && (health?.first == HEALTHCHECK_PASSING && health.second >= 0) -> {
                ObjectUnreachable(health.second)
            }

            state == STATE_ACTIVE -> ObjectActive(0, healthTagPresent = (health != null))
            state == STATE_UNREACHABLE -> ObjectUnreachable(0, healthTagPresent = (health != null))
            state == null -> ObjectUnreachable(0, healthTagPresent = (health != null))

            else -> ObjectOther(state)
        }


private fun markObject(db: StyxObjectStore<RoutingObjectRecord>, name: String, newStatus: ObjectHealth) {
    db.compute(name) { previous ->
        if (previous == null) {
            return@compute null
        }
        val prevTags = previous.tags;
        val newTags = reTag(prevTags, newStatus)
        if (prevTags != newTags)
            previous.copy(tags = newTags)
        else
            previous
    }
}

internal fun reTag(tags: Set<String>, newStatus: ObjectHealth) =
        tags.asSequence()
                .filterNot { stateTag.match(it) || healthCheckTag.match(it) }
                .plus(stateTag(newStatus.state()))
                .plus(healthCheckTag(newStatus.health()!!))
                .filterNotNull()
                .toSet()

private val RELEVANT_STATES = setOf(STATE_ACTIVE, STATE_UNREACHABLE)
private fun containsRelevantStateTag(entry: Map.Entry<String, RoutingObjectRecord>) =
        stateTag.find(entry.value.tags) in RELEVANT_STATES

