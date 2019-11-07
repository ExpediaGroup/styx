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
import com.hotels.styx.services.HealthCheckMonitoringService.Companion.ACTIVE_TAG
import com.hotels.styx.services.HealthCheckMonitoringService.Companion.EXECUTOR
import com.hotels.styx.services.HealthCheckMonitoringService.Companion.INACTIVE_TAG
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.toMono
import java.time.Duration
import java.util.Optional
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

        val ACTIVE_TAG = "state:active"
        val INACTIVE_TAG = "state:inactive"

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
                .filter(::containsInactiveTag)
                .forEach {
                    (name, _) -> markObject(objectStore, name, ObjectActive(0))
                }

        futureRef.get().cancel(false)
    }

    internal fun runChecks(application: String, objectStore: StyxObjectStore<RoutingObjectRecord>) {
        val monitoredObjects = discoverMonitoredObjects(application, objectStore)
                .map {
                    val tag = healthStatusTag(it.second.tags).orElse(INACTIVE_TAG)
                    val health = objectHealthFrom(tag).orElse(null)
                    Triple(it.first, it.second, health)
                }
                .filter { (_, _, objectHealth) -> objectHealth != null }

        val pendingHealthChecks = monitoredObjects
                .map { (name, record, objectHealth) ->
                    healthCheck(probe, record.routingObject, objectHealth)
                            .map { newHealth -> Triple(name, objectHealth, newHealth) }
                            .doOnNext { (name, currentHealth, newHealth) ->
                                if (currentHealth != newHealth || tagIsIncomplete(record.tags)) {
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

internal fun objectHealthFrom(string: String) = Optional.ofNullable(
        when {
            string.equals(ACTIVE_TAG) -> ObjectActive(0)
            string.equals(INACTIVE_TAG) -> ObjectInactive(0)
            string.matches("$ACTIVE_TAG:[0-9]+".toRegex()) -> {
                val count = string.removePrefix("$ACTIVE_TAG:").toInt()
                ObjectActive(count)
            }
            string.matches("$INACTIVE_TAG:[0-9]+".toRegex()) -> {
                val count = string.removePrefix("$INACTIVE_TAG:").toInt()
                ObjectInactive(count)
            }
            else -> null
        })

private fun healthStatusTag(tags: Set<String>) = Optional.ofNullable(
        tags.firstOrNull {
            it.startsWith(ACTIVE_TAG) || it.startsWith(INACTIVE_TAG)
        }
)

internal fun tagIsIncomplete(tag: Set<String>) = !healthStatusTag(tag)
        .filter { it.matches(".+:([0-9]+)$".toRegex()) }
        .isPresent

internal fun discoverMonitoredObjects(application: String, objectStore: StyxObjectStore<RoutingObjectRecord>) =
        objectStore.entrySet()
                .filter { it.value.tags.contains(application) }
                .map { Pair(it.key, it.value) }

private fun removeTags(db: StyxObjectStore<RoutingObjectRecord>, name: String) {
    db.get(name).ifPresent {
        db.insert(name, it.copy(tags = it.tags
                .filterNot { tag -> tag.matches("($ACTIVE_TAG|$INACTIVE_TAG).*".toRegex()) }
                .toSet()))
    }
}

private fun markObject(db: StyxObjectStore<RoutingObjectRecord>, name: String, newStatus: ObjectHealth) {
    db.get(name).ifPresent { db.insert(name, it.copy(tags = reTag(it.tags, newStatus))) }
}

internal fun reTag(tags: Set<String>, newStatus: ObjectHealth) = tags
        .filterNot { it.matches("($ACTIVE_TAG|$INACTIVE_TAG).*".toRegex()) }
        .plus(statusTag(newStatus))
        .toSet()

private fun statusTag(status: ObjectHealth) = when (status) {
    is ObjectActive -> "$ACTIVE_TAG:${status.failedProbes}"
    is ObjectInactive -> "$INACTIVE_TAG:${status.successfulProbes}"
}

private fun containsInactiveTag(entry: Map.Entry<String, RoutingObjectRecord>) =
        entry.value.tags.any { it.matches("($INACTIVE_TAG).*".toRegex()) }
