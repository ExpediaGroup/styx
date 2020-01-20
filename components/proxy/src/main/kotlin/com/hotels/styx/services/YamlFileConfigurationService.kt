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
package com.hotels.styx.services

import com.fasterxml.jackson.databind.JsonNode
import com.google.common.net.MediaType.HTML_UTF_8
import com.google.common.net.MediaType.PLAIN_TEXT_UTF_8
import com.hotels.styx.common.http.handler.HttpContentHandler
import com.hotels.styx.api.extension.service.spi.StyxService
import com.hotels.styx.common.http.handler.HttpAggregator
import com.hotels.styx.config.schema.SchemaDsl
import com.hotels.styx.config.schema.SchemaDsl.bool
import com.hotels.styx.config.schema.SchemaDsl.field
import com.hotels.styx.config.schema.SchemaDsl.optional
import com.hotels.styx.config.schema.SchemaDsl.string
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.config.RoutingObjectFactory
import com.hotels.styx.routing.config.StyxObjectDefinition
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.ProviderObjectRecord
import com.hotels.styx.server.handlers.ClassPathResourceHandler
import com.hotels.styx.serviceproviders.ServiceProviderFactory
import com.hotels.styx.services.OriginsConfigConverter.Companion.deserialiseOrigins
import com.hotels.styx.sourceTag
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.RuntimeException
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

internal class YamlFileConfigurationService(
        private val name: String,
        private val routeDb: StyxObjectStore<RoutingObjectRecord>,
        private val converter: OriginsConfigConverter,
        private val config: YamlFileConfigurationServiceConfig,
        private val serviceDb: StyxObjectStore<ProviderObjectRecord>) : StyxService {

    private val pollInterval = if (config.pollInterval.isBlank()) {
        Duration.ofSeconds(1)
    } else {
        Duration.parse(config.pollInterval)
    }

    private val initialised = CountDownLatch(1)

    private val ingressObjectName = if (config.ingressObject.isNotBlank()) config.ingressObject else "$name-router"

    private val objectSourceTag = sourceTag(name)

    private val fileMonitoringService = FileMonitoringService("YamlFileCoinfigurationService", config.originsFile, pollInterval) {
        reloadAction(it)
    }

    private val healthMonitors = AtomicReference<List<Pair<String, ProviderObjectRecord>>>(listOf())

    @Volatile
    private var originsConfig = ""

    companion object {
        @JvmField
        val SCHEMA = SchemaDsl.`object`(
                field("originsFile", string()),
                optional("monitor", bool()),
                optional("ingressObject", string()),
                optional("pollInterval", string()))

        private val LOGGER = LoggerFactory.getLogger(YamlFileConfigurationService::class.java)
    }

    override fun start() = fileMonitoringService.start()
            .thenAccept {
                LOGGER.info("service starting - {}", config.originsFile)
                initialised.await()
                LOGGER.info("service started - {}", config.originsFile)
            }

    override fun stop() = fileMonitoringService.stop()
            .thenAccept {
                LOGGER.info("service stopped")
            }

    override fun adminInterfaceHandlers(namespace: String) = mapOf(
            "assets/" to HttpAggregator(ClassPathResourceHandler("$namespace/assets/", "/admin/assets/YamlConfigurationService")),
            "configuration" to HttpContentHandler(PLAIN_TEXT_UTF_8.toString(), UTF_8) { originsConfig },
            "origins" to HttpContentHandler(HTML_UTF_8.toString(), UTF_8) {
                OriginsPageRenderer("$namespace/assets", name, routeDb).render()
            },
            "/" to OriginsAdminHandler(namespace, name, routeDb, serviceDb))

    fun reloadAction(content: String): Unit {
        LOGGER.info("New origins configuration: \n$content")

        kotlin.runCatching {
            val deserialised = deserialiseOrigins(content)

            val routingObjectDefs = (
                    converter.routingObjects(deserialised) +
                            converter.pathPrefixRouter(ingressObjectName, deserialised))
                    .map { StyxObjectDefinition(it.name(), it.type(), it.tags() + objectSourceTag, it.config()) }

            routingObjectDefs.forEach { objectDef ->
                routeDb.get(objectDef.name()).ifPresent {
                    if (sourceTag.find(it.tags) != name) {
                        throw DuplicateObjectException("Object name='${objectDef.name()}' already exists. Provider='${name}', file='${config.originsFile}'.")
                    }
                }
            }

            val healthMonitors = converter.healthCheckServices(deserialised)
                    .map { (name, record) -> Pair(name, record.copy(tags = record.tags + objectSourceTag)) }

            healthMonitors.forEach { (objectName, _) ->
                serviceDb.get(objectName).ifPresent {
                    if (sourceTag.find(it.tags) != name) {
                        throw DuplicateObjectException("Health Monitor name='${objectName}' already exists. Provider='${name}', file='${config.originsFile}'.")
                    }
                }
            }

            Pair(healthMonitors, routingObjectDefs)
        }.mapCatching { (healthMonitors, routingObjectDefs) ->
            updateRoutingObjects(routingObjectDefs)
            updateHealthCheckServices(serviceDb, healthMonitors)
        }.onSuccess {
            originsConfig = content
            initialised.countDown()
        }.onFailure {
            LOGGER.error("Failed to reload new configuration. cause='{}'", it.message, it)
        }
    }

    private fun changed(one: JsonNode, another: JsonNode) = !one.equals(another)

    internal fun updateRoutingObjects(objectDefs: List<StyxObjectDefinition>) {
        val previousObjectNames = routeDb.entrySet()
                .filter { it.value.tags.contains(objectSourceTag) }
                .map { it.key }

        val newObjectNames = objectDefs.map { it.name() }
        val removedObjects = previousObjectNames.minus(newObjectNames)

        objectDefs.forEach { objectDef ->
            routeDb.compute(objectDef.name()) { previous ->
                if (previous == null || changed(objectDef.config(), previous.config)) {
                    previous?.routingObject?.stop()
                    converter.routingObjectRecord(objectDef)
                } else {
                    previous
                }
            }
        }

        removedObjects.forEach {
            routeDb.remove(it).ifPresent {
                it.routingObject.stop()
            }
        }
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
                            ?.whenComplete { _, throwable ->
                                if (throwable != null) {
                                    val stack = StringWriter().let {
                                        throwable.printStackTrace(PrintWriter(it))
                                        it.toString()
                                    }
                                    LOGGER.warn("Service failed to terminate cleanly. cause=$throwable stack=$stack")
                                }
                            }
                    new
                } else {
                    // No need to shout down the new one. It has yet been started.
                    previous
                }
            }
        }

        removedObjects.forEach {
            objectDb.remove(it).ifPresent {
                it.styxService.stop()
            }
        }
    }

    private class DuplicateObjectException(message: String): RuntimeException(message)
}

internal data class YamlFileConfigurationServiceConfig(val originsFile: String, val ingressObject: String = "", val monitor: Boolean = true, val pollInterval: String = "")

internal class YamlFileConfigurationServiceFactory : ServiceProviderFactory {
    override fun create(name: String, context: RoutingObjectFactory.Context, jsonConfig: JsonNode, serviceDb: StyxObjectStore<ProviderObjectRecord>): StyxService {
        val serviceConfig = JsonNodeConfig(jsonConfig).`as`(YamlFileConfigurationServiceConfig::class.java)!!
        val originRestrictionCookie = context.environment().configuration().get("originRestrictionCookie").orElse(null)

        return YamlFileConfigurationService(name, context.routeDb(), OriginsConfigConverter(serviceDb, context, originRestrictionCookie), serviceConfig, serviceDb)
    }
}
