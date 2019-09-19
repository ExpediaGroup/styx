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
import com.fasterxml.jackson.databind.JsonNode
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
import com.hotels.styx.routing.config.Builtins.INTERCEPTOR_PIPELINE
import com.hotels.styx.routing.config.Builtins.LOAD_BALANCING_GROUP
import com.hotels.styx.routing.config.Builtins.PATH_PREFIX_ROUTER
import com.hotels.styx.routing.config.Builtins.REWRITE
import com.hotels.styx.routing.config.RoutingObjectFactory
import com.hotels.styx.routing.config.StyxObjectDefinition
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.handlers.HostProxy.HostProxyConfiguration
import com.hotels.styx.routing.handlers.LoadBalancingGroup
import com.hotels.styx.routing.handlers.ProviderObjectRecord
import org.slf4j.LoggerFactory


internal class OriginsConfigConverter(
        val serviceDb: StyxObjectStore<ProviderObjectRecord>,
        val context: RoutingObjectFactory.Context,
        val originRestrictionCookie: String?) {

    internal fun routingObjects(apps: List<BackendService>) =
            routingObjectConfigs(apps)
                    .map { styxObjectDef ->
                        Pair(styxObjectDef.name(), RoutingObjectRecord.create(
                                styxObjectDef.type(),
                                styxObjectDef.tags().toSet(),
                                styxObjectDef.config(),
                                Builtins.build(listOf(styxObjectDef.name()), context, styxObjectDef)
                        ))
                    }

    internal fun routingObjectConfigs(apps: List<BackendService>): List<StyxObjectDefinition> =
            apps.flatMap { toBackendServiceObjects(it, originRestrictionCookie) } + pathPrefixRouter(apps)

    internal fun pathPrefixRouter(apps: List<BackendService>): StyxObjectDefinition {
        val configuration = """
            ---
            type: $PATH_PREFIX_ROUTER
            name: $ROOT_OBJECT_NAME
            tags: 
              - $OBJECT_CREATOR_TAG
            config:
              routes:
            """.trimIndent()
                .plus(apps
                        .map { "   - { prefix: ${it.path()}, destination: ${it.id()} }" }
                        .joinToString(prefix = "\n", separator = "\n"))

        return ConfigurationParser.Builder<YamlConfiguration>()
                .format(YAML)
                .build()
                .parse(configSource(configuration)).`as`(StyxObjectDefinition::class.java)
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

    companion object {
        val LOGGER = LoggerFactory.getLogger(this::class.java)
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

        private fun toBackendServiceObjects(app: BackendService, originsRestrictionCookie: String? = null) = app
                .origins()
                .sortedBy { it.id().toString() }
                .map { hostProxy(app, it) }
                .plus(loadBalancingGroup(app, originsRestrictionCookie))

        internal fun loadBalancingGroup(app: BackendService, originsRestrictionCookie: String? = null) = if (app.rewrites().isEmpty()) {
            StyxObjectDefinition(
                    "${app.id()}",
                    LOAD_BALANCING_GROUP,
                    listOf(OBJECT_CREATOR_TAG),
                    loadBalancingGroupConfig(app.id().toString(), originsRestrictionCookie, app.stickySessionConfig()))
        } else {
            interceptorPipelineConfig(app, originsRestrictionCookie)
        }

        internal fun hostProxy(app: BackendService, origin: Origin) = StyxObjectDefinition(
                "${app.id()}.${origin.id()}",
                HOST_PROXY,
                listOf(OBJECT_CREATOR_TAG, app.id().toString()),
                hostProxyConfig(
                        app.connectionPoolConfig(),
                        app.tlsSettings().orElse(null),
                        app.responseTimeoutMillis(),
                        origin))

        private fun loadBalancingGroupConfig(origins: String,
                                             originsRestrictionCookie: String?,
                                             stickySession: StickySessionConfig?): JsonNode = MAPPER.valueToTree(
                LoadBalancingGroup.Config(origins, originsRestrictionCookie, stickySession))

        internal fun interceptorPipelineConfig(app: BackendService, originsRestrictionCookie: String?): StyxObjectDefinition {
            val rewrites = app.rewrites()
                    .map { """- { urlPattern: "${it.urlPattern()}", replacement: "${it.replacement()}" }""" }
                    .joinToString(separator = "\n")
                    .prependIndent("  ")

            val rewriteInterceptor = """
                - type: $REWRITE
                  config:
                __rewrites__
                """.trimIndent()
                    .replace("__rewrites__", rewrites)

            val lbConfig = MAPPER.writeValueAsString(loadBalancingGroupConfig(app.id().toString(), originsRestrictionCookie, app.stickySessionConfig()))
                    .dropWhile { it == '-' || it == '\n' }
                    .prependIndent("  ")

            val handler = """
                type: $LOAD_BALANCING_GROUP
                name: "${app.id()}-lb"
                tags: 
                  - $OBJECT_CREATOR_TAG
                config:
                __config__
            """.trimIndent()
                    .replace("__config__", lbConfig)

            val yamlConfig = """
                ---
                type: $INTERCEPTOR_PIPELINE
                name: ${app.id()}
                tags: 
                  - $OBJECT_CREATOR_TAG
                config:
                  pipeline:
                __rewriteInterceptor__
                  handler:
                __handlers__
                """.trimIndent()
                    .replace("__rewriteInterceptor__", rewriteInterceptor.prependIndent("    "))
                    .replace("__handlers__", handler.prependIndent("    "))

            return ConfigurationParser.Builder<YamlConfiguration>()
                    .format(YAML)
                    .build()
                    .parse(configSource(yamlConfig)).`as`(StyxObjectDefinition::class.java)
        }

        private fun hostProxyConfig(poolSettings: ConnectionPoolSettings,
                                    tlsSettings: TlsSettings?,
                                    responseTimeout: Int,
                                    origin: Origin): JsonNode = MAPPER.valueToTree(
                HostProxyConfiguration(
                        "${origin.host()}:${origin.port()}",
                        poolSettings,
                        tlsSettings,
                        responseTimeout,
                        "origins.${origin.id()}.${origin.id()}"))
    }
}
