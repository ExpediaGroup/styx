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

import com.fasterxml.jackson.databind.JsonNode
import com.hotels.styx.api.extension.service.spi.StyxService
import com.hotels.styx.config.schema.SchemaDsl
import com.hotels.styx.config.schema.SchemaDsl.bool
import com.hotels.styx.config.schema.SchemaDsl.field
import com.hotels.styx.config.schema.SchemaDsl.optional
import com.hotels.styx.config.schema.SchemaDsl.string
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.config.RoutingObjectFactory
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.handlers.ProviderObjectRecord
import com.hotels.styx.serviceproviders.ServiceProviderFactory
import com.hotels.styx.services.OriginsConfigConverter.Companion.deserialiseOrigins
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

internal class YamlFileConfigurationService(
        private val routeDb: StyxObjectStore<RoutingObjectRecord>,
        private val converter: OriginsConfigConverter,
        private val config: YamlFileConfigurationServiceConfig,
        private val serviceDb: StyxObjectStore<ProviderObjectRecord>) : StyxService {

    val pollInterval = if (config.pollInterval.isNullOrBlank()) {
        Duration.ofSeconds(1)
    } else {
        Duration.parse(config.pollInterval)
    }

    val fileMonitoringService = FileMonitoringService("YamlFileCoinfigurationService", config.originsFile, pollInterval) {
        reloadAction(it)
    }

    val routingObjects = AtomicReference<List<Pair<String, RoutingObjectRecord>>>(listOf())
    val healthMonitors = AtomicReference<List<Pair<String, ProviderObjectRecord>>>(listOf())

    companion object {
        @JvmField
        val SCHEMA = SchemaDsl.`object`(
                field("originsFile", string()),
                optional("monitor", bool()),
                optional("pollInterval", string()))

        private val LOGGER = LoggerFactory.getLogger(YamlFileConfigurationService::class.java)
    }

    override fun start() = fileMonitoringService.start()
            .thenAccept {
                LOGGER.info("service started - {} - {}", config.originsFile)
            }

    override fun stop() = fileMonitoringService.stop()
            .thenAccept {
                LOGGER.info("service stopped")
            }

    fun reloadAction(content: String): Unit {
        LOGGER.info("New origins configuration: \n$content")

        kotlin.runCatching {
            val deserialised = deserialiseOrigins(content)

            val routingObjects = converter.routingObjects(deserialised)
            val healthMonitors = converter.healthCheckServices(deserialised)

            Pair(healthMonitors, routingObjects)
        }.mapCatching { (healthMonitors, routingObjects) ->
            updateRoutingObjects(routingObjects)
            updateHealthCheckServices(serviceDb, healthMonitors)
        }.onFailure {
            LOGGER.error("Failed to reload new configuration. cause='{}'", it.message, it)
        }
    }

    private fun changed(one: JsonNode, another: JsonNode) = !one.equals(another)

    internal fun updateRoutingObjects(objects: List<Pair<String, RoutingObjectRecord>>) {
        val oldObjectNames = routingObjects.get().map { it.first }
        routingObjects.set(objects)

        val newObjectNames = objects.map { it.first }
        val removedObjects = oldObjectNames.minus(newObjectNames)

        objects.forEach { (name, new) ->
            routeDb.compute(name) { previous ->
                if (previous == null || changed(new.config, previous.config)) {
                    previous?.routingObject?.stop()
                    new
                } else {
                    new.routingObject.stop()
                    previous
                }
            }
        }

        removedObjects.forEach { routeDb.remove(it).ifPresent {
            it.routingObject.stop()
        } }
    }

    private fun updateHealthCheckServices(objectDb: StyxObjectStore<ProviderObjectRecord>, objects: List<Pair<String, ProviderObjectRecord>>): Unit {
        val oldObjectNames = healthMonitors.get().map { it.first }
        healthMonitors.set(objects)

        val newObjectNames = objects.map { it.first }
        val removedObjects = oldObjectNames.minus(newObjectNames)

        objects.forEach { (name, new) ->
            objectDb.compute(name) { previous ->
                if (previous == null || changed(new.config, previous.config)) {
                    new.styxService.start()
                    previous?.styxService?.stop()
                    new
                } else {
                    // No need to shout down the new one. It has yet been started.
                    previous
                }
            }
        }

        removedObjects.forEach { objectDb.remove(it).ifPresent {
            it.styxService.stop()
        } }
    }
}

internal data class YamlFileConfigurationServiceConfig(val originsFile: String, val monitor: Boolean = true, val pollInterval: String = "")

internal class YamlFileConfigurationServiceFactory : ServiceProviderFactory {
    override fun create(context: RoutingObjectFactory.Context, jsonConfig: JsonNode, serviceDb: StyxObjectStore<ProviderObjectRecord>): StyxService {
        val serviceConfig = JsonNodeConfig(jsonConfig).`as`(YamlFileConfigurationServiceConfig::class.java)!!
        val originRestrictionCookie = context.environment().configuration().get("originRestrictionCookie").orElse(null)

        return YamlFileConfigurationService(context.routeDb(), OriginsConfigConverter(serviceDb, context, originRestrictionCookie), serviceConfig, serviceDb)
    }
}
