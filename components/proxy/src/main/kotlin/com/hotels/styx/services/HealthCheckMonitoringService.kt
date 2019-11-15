/*
  Copyright (C) 2013-2019 Expedia Inc.

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
import com.hotels.styx.*
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.extension.service.spi.AbstractStyxService
import com.hotels.styx.api.extension.service.spi.StyxService
import com.hotels.styx.config.schema.SchemaDsl
import com.hotels.styx.config.schema.SchemaDsl.field
import com.hotels.styx.config.schema.SchemaDsl.integer
import com.hotels.styx.config.schema.SchemaDsl.optional
import com.hotels.styx.config.schema.SchemaDsl.string
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig
import com.hotels.styx.routing.RoutingObject
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.config.RoutingObjectFactory
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.handlers.ProviderObjectRecord
import com.hotels.styx.serviceproviders.ServiceProviderFactory
import com.hotels.styx.services.HealthCheckMonitoringService.Companion.EXECUTOR
import com.sun.org.apache.regexp.internal.RE
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.toMono
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
        private val executor: ScheduledExecutorService) : AbstractStyxService("HealthCheckMonitoringService") {

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

        private val LOGGER = LoggerFactory.getLogger(HealthCheckMonitoringService::class.java)
    }

    private val probe = urlProbe(HttpRequest.get(urlPath).build(), Duration.ofMillis(1000))
    private val determineObjectState = healthCheckFunction(activeThreshold, inactiveThreshold)
    private val futureRef: AtomicReference<ScheduledFuture<*>> = AtomicReference()

    override fun startService() = CompletableFuture.runAsync {
        LOGGER.info("started - {} - {}", period.toMillis(), period.toMillis())
        futureRef.set(executor.scheduleAtFixedRate(
                { runChecks(application, objectStore) },
                period.toMillis(),
                period.toMillis(),
                MILLISECONDS))
    }

    override fun stopService() = CompletableFuture.runAsync {
        LOGGER.info("stopped")

        objectStore.entrySet()
                .filter(::containsRelevantStateTag)
                .forEach { (name, _) ->
                    markObject(objectStore, name, ObjectActive(0))
                }

        futureRef.get().cancel(false)
    }

    internal fun runChecks(application: String, objectStore: StyxObjectStore<RoutingObjectRecord>) {
        val monitoredObjects = discoverMonitoredObjects(application, objectStore)
                .map {
                    val tags = it.second.tags
                    val objectHealth = objectHealthFrom(stateTagValue(tags), healthTagValue(tags))
                    Triple(it.first, it.second, objectHealth)
                }
                .filter { (_, _, objectHealth) -> objectHealth != null }

        val pendingHealthChecks = monitoredObjects
                .map { (name, record, objectHealth) ->
                    healthCheck(probe, record.routingObject, objectHealth!!)
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
                    LOGGER.info("Health Check Completed ..")
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
                EXECUTOR)
    }
}

internal fun objectHealthFrom(state: String?, health: String?) =
        when {
            state == STATE_ACTIVE && (health?.matches("$HEALTH_FAIL:[0-9]+".toRegex()) ?: false) -> {
                val count = health!!.removePrefix("$HEALTH_FAIL:").toInt()
                ObjectActive(count)
            }

            state == STATE_UNREACHABLE && (health?.matches("$HEALTH_SUCCESS:[0-9]+".toRegex()) ?: false) -> {
                val count = health!!.removePrefix("$HEALTH_SUCCESS:").toInt()
                ObjectUnreachable(count)
            }

            state == STATE_ACTIVE -> ObjectActive(0)
            state == STATE_UNREACHABLE -> ObjectUnreachable(0)
            state == null -> ObjectUnreachable(0)

            else -> ObjectOther(state)
        }

internal fun discoverMonitoredObjects(application: String, objectStore: StyxObjectStore<RoutingObjectRecord>) =
        objectStore.entrySet()
                .filter { it.value.tags.contains(lbGroupTag(application)) }
                .map { Pair(it.key, it.value) }

private fun markObject(db: StyxObjectStore<RoutingObjectRecord>, name: String, newStatus: ObjectHealth) {
    // The ifPresent is not ideal, but compute() does not allow the computation to return null. So we can't preserve
    // a state where the object does not exist using compute alone. But even with ifPresent, as we are open to
    // the object disappearing between the ifPresent and the compute, which would again lead to the compute creating
    // a new object when we don't want it to. But at least this will happen much less frequently.
    db.get(name).ifPresent {
        db.compute(name) { previous ->
            val prevTags = previous!!.tags // TODO: Handle the !! failure
            val newTags = reTag(prevTags, newStatus)
            if (prevTags != newTags)
                it.copy(tags = newTags)
            else
                previous
        }
    }
}

internal fun reTag(tags: Set<String>, newStatus: ObjectHealth) =
    tags.asSequence()
            .filterNot { isStateTag(it) || isHealthTag(it) }
            .plus(stateTag(newStatus.state()))
            .plus(healthTag(newStatus.health()))
            .filterNotNull()
            .toSet()

val RELEVANT_STATES = setOf(STATE_ACTIVE, STATE_UNREACHABLE)
private fun containsRelevantStateTag(entry: Map.Entry<String, RoutingObjectRecord>) =
        stateTagValue(entry.value.tags) in RELEVANT_STATES

