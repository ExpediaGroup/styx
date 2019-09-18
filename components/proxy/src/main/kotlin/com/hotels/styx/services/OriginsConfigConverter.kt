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

import com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.api.extension.service.BackendService
import com.hotels.styx.api.extension.service.ConnectionPoolSettings
import com.hotels.styx.api.extension.service.HealthCheckConfig
import com.hotels.styx.api.extension.service.StickySessionConfig
import com.hotels.styx.api.extension.service.TlsSettings
import com.hotels.styx.infrastructure.configuration.ConfigurationParser
import com.hotels.styx.infrastructure.configuration.ConfigurationSource.configSource
import com.hotels.styx.infrastructure.configuration.json.ObjectMappers
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfiguration
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfigurationFormat.YAML
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.config.Builtins
import com.hotels.styx.routing.config.Builtins.HEALTH_CHECK_MONITOR
import com.hotels.styx.routing.config.Builtins.HOST_PROXY
import com.hotels.styx.routing.config.Builtins.LOAD_BALANCING_GROUP
import com.hotels.styx.routing.config.Builtins.PATH_PREFIX_ROUTER
import com.hotels.styx.routing.config.RoutingObjectFactory
import com.hotels.styx.routing.config.StyxObjectDefinition
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.handlers.HostProxy
import com.hotels.styx.routing.handlers.LoadBalancingGroup
import com.hotels.styx.routing.handlers.ProviderObjectRecord


internal class OriginsConfigConverter(
        val serviceDb: StyxObjectStore<ProviderObjectRecord>,
        val context: RoutingObjectFactory.Context,
        val originRestrictionCookie: String?) {

    companion object {
        val OBJECT_CREATOR_TAG = "source=OriginsFileConverter"
        val ROOT_OBJECT_NAME = "pathPrefixRouter"

        internal fun deserialiseOrigins(text: String): List<BackendService> {
            val rootNode = MAPPER.readTree(text)
            return MAPPER.readValue<List<BackendService>>(rootNode.traverse(), TYPE)
        }

        private val MAPPER = ObjectMappers.addStyxMixins(ObjectMapper(YAMLFactory()))
                .disable(FAIL_ON_UNKNOWN_PROPERTIES)
                .configure(AUTO_CLOSE_SOURCE, true)

        private val TYPE = object : TypeReference<List<BackendService>>() {
        }
    }

    internal fun routingObjects(apps: List<BackendService>) =
            apps.flatMap { toBackendServiceObjects(it, originRestrictionCookie) } +
                    Pair(ROOT_OBJECT_NAME, pathPrefixRouter(apps))

    internal fun pathPrefixRouter(apps: List<BackendService>): RoutingObjectRecord {
        val prefix = """
            ---
            type: $PATH_PREFIX_ROUTER
            config:
              routes:
            """.trimIndent()

        val origins = apps
                .map { "   - { prefix: ${it.path()}, destination: ${it.id()} }" }
                .joinToString(separator = "\n")

        val styxObjectDefinition = ConfigurationParser.Builder<YamlConfiguration>()
                .format(YAML)
                .build()
                .parse(configSource(prefix + "\n" + origins)).`as`(StyxObjectDefinition::class.java)

        return RoutingObjectRecord.create(
                PATH_PREFIX_ROUTER,
                setOf(OBJECT_CREATOR_TAG),
                styxObjectDefinition.config(),
                Builtins.build(listOf(ROOT_OBJECT_NAME), context, styxObjectDefinition))
    }

    internal fun healthCheckServices(apps: List<BackendService>) = apps
            .filterNot { it.healthCheckConfig() == null }
            .filter { it.healthCheckConfig().uri().isPresent }
            .filter { it.healthCheckConfig().isEnabled }
            .map {
                val appId = it.id().toString()
                val healthCheckConfig = it.healthCheckConfig()

                Pair(appId, healthCheckService(appId, healthCheckConfig))
            }

    internal fun healthCheckService(appId: String, healthCheckConfig: HealthCheckConfig): ProviderObjectRecord {
        assert(healthCheckConfig.isEnabled)
        assert(healthCheckConfig.uri().isPresent)

        val str = MAPPER.writeValueAsString(HealthCheckConfiguration(
                appId,
                healthCheckConfig.uri().get(),
                healthCheckConfig.timeoutMillis(),
                healthCheckConfig.intervalMillis(),
                healthCheckConfig.healthyThreshold(),
                healthCheckConfig.unhealthyThreshold()))

        val serviceConfig = MAPPER.readTree(str)

        val providerObject = Builtins.build(StyxObjectDefinition(appId, HEALTH_CHECK_MONITOR, serviceConfig),
                serviceDb, Builtins.BUILTIN_SERVICE_PROVIDER_FACTORIES, context)

        return ProviderObjectRecord(HEALTH_CHECK_MONITOR,
                setOf(OBJECT_CREATOR_TAG, "target=$appId"),
                serviceConfig,
                providerObject)
    }

    private fun toBackendServiceObjects(app: BackendService, originsRestrictionCookie: String? = null): List<Pair<String, RoutingObjectRecord>> {
        val hostProxies = app.origins()
                .sortedBy { it.id().toString() }
                .map { hostProxy(app, it) }

        return hostProxies + loadBalancingGroup(app, originsRestrictionCookie)
    }

    internal fun loadBalancingGroup(app: BackendService, originsRestrictionCookie: String? = null): Pair<String, RoutingObjectRecord> {
        val config = loadBalancingGroupConfig(app.id().toString(), originsRestrictionCookie, app.stickySessionConfig())
        val lbGroupName = "${app.id()}"

        val lbGroupRecord = RoutingObjectRecord.create(LOAD_BALANCING_GROUP,
                setOf(OBJECT_CREATOR_TAG),
                config,
                Builtins.build(
                        listOf(lbGroupName),
                        context,
                        StyxObjectDefinition("${app.id()}", LOAD_BALANCING_GROUP, config)))

        return Pair(lbGroupName, lbGroupRecord)
    }

    private fun loadBalancingGroupConfig(origins: String,
                                         originsRestrictionCookie: String?,
                                         stickySession: StickySessionConfig?) =
            MAPPER.readTree(MAPPER.writeValueAsString(
                    LoadBalancingGroup.Config(origins, null, originsRestrictionCookie, stickySession)))


    internal fun hostProxy(app: BackendService, origin: Origin): Pair<String, RoutingObjectRecord> {
        val name = "${app.id()}.${origin.id()}"

        val config = hostProxyConfig(
                app.connectionPoolConfig(),
                app.tlsSettings().orElse(null),
                app.responseTimeoutMillis(),
                origin)

        val record = RoutingObjectRecord.create(HOST_PROXY,
                setOf(OBJECT_CREATOR_TAG, app.id().toString()),
                config,
                Builtins.build(
                        listOf(name),
                        context,
                        StyxObjectDefinition(name, HOST_PROXY, config)
                ))

        return Pair(name, record)
    }

    private fun hostProxyConfig(poolSettings: ConnectionPoolSettings,
                                tlsSettings: TlsSettings?,
                                responseTimeout: Int,
                                origin: Origin) =
            MAPPER.readTree(MAPPER.writeValueAsString(
                    HostProxy.HostProxyConfiguration(
                            "${origin.host()}:${origin.port()}",
                            poolSettings,
                            tlsSettings,
                            responseTimeout,
                            "origins.${origin.id()}.${origin.id()}")))
}
