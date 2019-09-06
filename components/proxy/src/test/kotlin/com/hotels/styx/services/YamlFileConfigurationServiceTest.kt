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

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.hotels.styx.api.extension.service.ConnectionPoolSettings
import com.hotels.styx.api.extension.service.StickySessionConfig
import com.hotels.styx.api.extension.service.spi.AbstractStyxService
import com.hotels.styx.api.extension.service.spi.StyxServiceStatus.RUNNING
import com.hotels.styx.api.extension.service.spi.StyxServiceStatus.STOPPED
import com.hotels.styx.infrastructure.configuration.json.ObjectMappers
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig
import com.hotels.styx.routing.RoutingObjectFactoryContext
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.handlers.PathPrefixRouter
import com.hotels.styx.routing.handlers.ProviderObjectRecord
import com.hotels.styx.services.OriginsConfigConverter.Companion.OBJECT_CREATOR_TAG
import io.kotlintest.Spec
import io.kotlintest.eventually
import io.kotlintest.matchers.collections.shouldContainAll
import io.kotlintest.seconds
import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Optional

class YamlFileConfigurationServiceTest : FunSpec() {
    val tempDir = createTempDir(suffix = "-${this.javaClass.simpleName}")
    val originsConfig = File("${tempDir.absolutePath}/config.yml")
    val LOGGER = LoggerFactory.getLogger(YamlFileConfigurationServiceTest::class.java)

    override fun beforeSpec(spec: Spec) {
        LOGGER.info("Temp directory: " + tempDir.absolutePath)
        LOGGER.info("Origins file: " + originsConfig.absolutePath)
    }

    override fun afterSpec(spec: Spec) {
        tempDir.deleteRecursively()
    }

    private fun with(service: YamlFileConfigurationService, action: (YamlFileConfigurationService) -> Unit) {
        try {
            action(service)
        } finally {
            service.stop().join()
        }
    }

    init {
        context("When the service starts") {
            test("It populates object store from the YAML configuration") {
                val routeDb = StyxObjectStore<RoutingObjectRecord>()
                val serviceDb = StyxObjectStore<ProviderObjectRecord>()

                writeOrigins("""
                    ---
                    - id: "app"
                      path: "/"
                      origins:
                      - { id: "app-01", host: "localhost:9090" }
                      - { id: "app-02", host: "localhost:9091" }
                    """.trimIndent())

                with(YamlFileConfigurationService(
                        routeDb,
                        OriginsConfigConverter(serviceDb, RoutingObjectFactoryContext(objectStore = routeDb).get(), "origins-cookie"),
                        YamlFileConfigurationServiceConfig(originsConfig.absolutePath),
                        serviceDb)) {
                    it.start().join()

                    eventually(2.seconds, AssertionError::class.java) {
                        routeDb.entrySet().size shouldBe 4
                    }
                }
            }

            // TODO: Can't be tested as FileChangeMonitor fails to start if the origins file doesn't exist.
            //       Enable this later when the restriction is lifted.
            test("!If file doesn't exist, it waits for it to become available") {
                val routeDb = StyxObjectStore<RoutingObjectRecord>()
                val serviceDb = StyxObjectStore<ProviderObjectRecord>()

                with(YamlFileConfigurationService(
                        routeDb,
                        OriginsConfigConverter(serviceDb, RoutingObjectFactoryContext(objectStore = routeDb).get(), "origins-cookie"),
                        YamlFileConfigurationServiceConfig("/a/b/c"),
                        serviceDb)) {
                    it.start().join()
                    eventually(2.seconds, AssertionError::class.java) {
                        routeDb.entrySet().size shouldBe 0
                    }
                }
            }

            test("It recovers from syntax errors") {
                val routeDb = StyxObjectStore<RoutingObjectRecord>()
                val serviceDb = StyxObjectStore<ProviderObjectRecord>()
                val service = serviceWithInitialConfig(routeDb, serviceDb,
                        initialObjectCount = 0,
                        config = """
                            ---
                            - something's wrong
                            """.trimIndent())

                with(service) {
                    writeOrigins("""
                        ---
                        - id: "app"
                          path: "/"
                          origins:
                          - { id: "app-01", host: "localhost:9090" }
                          - { id: "app-02", host: "localhost:9091" }
                         """.trimIndent())

                    eventually(2.seconds, AssertionError::class.java) {
                        routeDb.entrySet().size shouldBe 4
                    }
                }
            }
        }

        context("Service detects configuration changes") {
            val objectStore = StyxObjectStore<RoutingObjectRecord>()
            val serviceDb = StyxObjectStore<ProviderObjectRecord>()
            val service = serviceWithInitialConfig(objectStore, serviceDb)

            test("add origins") {
                writeOrigins("""
                        ---
                        - id: "app"
                          path: "/"
                          origins:
                          - { id: "app-01", host: "localhost:9090" }
                          - { id: "app-02", host: "localhost:9091" }
                         """.trimIndent())

                eventually(2.seconds, AssertionError::class.java) {
                    objectStore.entrySet().size shouldBe 4
                    val metadata = objectStore.entrySet().map { (key, record) -> Triple(key, record.type, record.tags) }

                    metadata.shouldContainAll(
                            Triple("app.app-01", "HostProxy", setOf("app", OBJECT_CREATOR_TAG)),
                            Triple("app.app-02", "HostProxy", setOf("app", OBJECT_CREATOR_TAG)),
                            Triple("app", "LoadBalancingGroup", setOf(OBJECT_CREATOR_TAG)),
                            Triple("pathPrefixRouter", "PathPrefixRouter", setOf(OBJECT_CREATOR_TAG)))
                }
            }

            test("remove origins") {
                writeOrigins("""
                        ---
                        - id: "app"
                          path: "/"
                          origins:
                          - { id: "app-01", host: "localhost:9090" }
                         """.trimIndent())

                eventually(2.seconds, AssertionError::class.java) {
                    objectStore.entrySet().size shouldBe 3

                    objectStore.entrySet()
                            .map { (key, record) -> Triple(key, record.type, record.tags) }
                            .shouldContainAll(
                                    Triple("app.app-01", "HostProxy", setOf("app", OBJECT_CREATOR_TAG)),
                                    Triple("app", "LoadBalancingGroup", setOf(OBJECT_CREATOR_TAG)),
                                    Triple("pathPrefixRouter", "PathPrefixRouter", setOf(OBJECT_CREATOR_TAG)))
                }
            }

            test("modify origins") {
                writeOrigins("""
                        ---
                        - id: "app"
                          path: "/"
                          origins:
                          - { id: "app-01", host: "localhost:9999" }
                         """.trimIndent())

                eventually(2.seconds, AssertionError::class.java) {
                    objectStore.entrySet().size shouldBe 3

                    objectStore.entrySet()
                            .map { (key, record) -> Triple(key, record.type, record.tags) }
                            .shouldContainAll(
                                    Triple("app.app-01", "HostProxy", setOf("app", OBJECT_CREATOR_TAG)),
                                    Triple("app", "LoadBalancingGroup", setOf(OBJECT_CREATOR_TAG)),
                                    Triple("pathPrefixRouter", "PathPrefixRouter", setOf(OBJECT_CREATOR_TAG)))

                    JsonNodeConfig(objectStore["app.app-01"].get().config).get("host") shouldBe Optional.of("localhost:9999")
                }
            }

            test("add applications") {
                writeOrigins("""
                        ---
                        - id: "app"
                          path: "/"
                          origins:
                          - { id: "app-01", host: "localhost:9090" }
                        - id: "appB"
                          path: "/b"
                          origins:
                          - { id: "appB-01", host: "localhost:8081" }
                         """.trimIndent())

                eventually(2.seconds, AssertionError::class.java) {
                    objectStore.entrySet().size shouldBe 5

                    objectStore.entrySet()
                            .map { (key, record) -> Triple(key, record.type, record.tags) }
                            .shouldContainAll(
                                    Triple("app.app-01", "HostProxy", setOf("app", OBJECT_CREATOR_TAG)),
                                    Triple("app", "LoadBalancingGroup", setOf(OBJECT_CREATOR_TAG)),
                                    Triple("appB.appB-01", "HostProxy", setOf("appB", OBJECT_CREATOR_TAG)),
                                    Triple("appB", "LoadBalancingGroup", setOf(OBJECT_CREATOR_TAG)),
                                    Triple("pathPrefixRouter", "PathPrefixRouter", setOf(OBJECT_CREATOR_TAG)))
                }
            }

            test("remove applications") {
                writeOrigins("""
                        ---
                        - id: "appB"
                          path: "/b"
                          origins:
                          - { id: "appB-01", host: "localhost:8081" }
                         """.trimIndent())

                eventually(2.seconds, AssertionError::class.java) {
                    objectStore.entrySet().size shouldBe 3

                    objectStore.entrySet()
                            .map { (key, record) -> Triple(key, record.type, record.tags) }
                            .shouldContainAll(
                                    Triple("appB.appB-01", "HostProxy", setOf("appB", OBJECT_CREATOR_TAG)),
                                    Triple("appB", "LoadBalancingGroup", setOf(OBJECT_CREATOR_TAG)),
                                    Triple("pathPrefixRouter", "PathPrefixRouter", setOf(OBJECT_CREATOR_TAG)))
                }

            }

            test("modify applications") {
                writeOrigins("""
                        ---
                        - id: "appB"
                          path: "/b"
                          origins:
                          - { id: "appB-01", host: "localhost:8081" }
                         """.trimIndent())

                eventually(2.seconds, AssertionError::class.java) {
                    objectStore.entrySet().size shouldBe 3

                    objectStore.entrySet()
                            .map { (key, record) -> Triple(key, record.type, record.tags) }
                            .shouldContainAll(
                                    Triple("appB.appB-01", "HostProxy", setOf("appB", OBJECT_CREATOR_TAG)),
                                    Triple("appB", "LoadBalancingGroup", setOf(OBJECT_CREATOR_TAG)),
                                    Triple("pathPrefixRouter", "PathPrefixRouter", setOf(OBJECT_CREATOR_TAG)))
                }
            }

            LOGGER.info("configuration changes - Stopping service [$service]")
            service.stop()
        }

        context("Load balancing group changes") {
            val objectStore = StyxObjectStore<RoutingObjectRecord>()
            val serviceDb = StyxObjectStore<ProviderObjectRecord>()
            val service = serviceWithInitialConfig(objectStore, serviceDb)

            test("Sticky session config changes") {
                writeOrigins("""
                        ---
                        - id: "app"
                          path: "/"
                          stickySession:
                            enabled: true
                            timeoutSeconds: 14321
                          origins:
                          - { id: "app-01", host: "localhost:9090" }
                         """.trimIndent())

                eventually(2.seconds, AssertionError::class.java) {
                    objectStore.get("app").isPresent shouldBe true
                    objectStore.get("app").get().let {
                        it.config.get("stickySession", StickySessionConfig::class.java)
                                .let {
                                    it.stickySessionEnabled() shouldBe true
                                    it.stickySessionTimeoutSeconds() shouldBe 14321
                                }
                    }
                }
            }

            test("!Rewrites changes") {
                // Rewrite rules are not supported. Need to investigate if they can be
                // supported as routing objects instead of bespoke feature.
            }

            test("Health checking is enabled") {
                writeOrigins("""
                        ---
                        - id: "app"
                          path: "/"
                          healthCheck:
                            uri: "http://www/check/me"
                          origins:
                          - { id: "app-01", host: "localhost:9090" }
                         """.trimIndent(), debug = true)

                eventually(2.seconds, AssertionError::class.java) {
                    serviceDb.get("app").isPresent shouldBe true
                    serviceDb.get("app").get().let {
                        it.config.get("objects", String::class.java) shouldBe "app"
                        it.config.get("path", String::class.java) shouldBe "http://www/check/me"
                        (it.styxService as AbstractStyxService).status() shouldBe RUNNING
                    }
                }
            }

            test("Health check configuration is modified") {
                // From previous test:
                val oldMonitor = serviceDb.get("app").get().styxService as AbstractStyxService

                writeOrigins("""
                        ---
                        - id: "app"
                          path: "/"
                          healthCheck:
                            uri: "http://new/url"
                          origins:
                          - { id: "app-01", host: "localhost:9090" }
                         """.trimIndent(), debug = true)

                eventually(2.seconds, AssertionError::class.java) {
                    serviceDb.get("app").isPresent shouldBe true
                    serviceDb.get("app").get().let {
                        it.config.get("objects", String::class.java) shouldBe "app"
                        it.config.get("path", String::class.java) shouldBe "http://new/url"
                        (it.styxService as AbstractStyxService).status() shouldBe RUNNING
                    }
                }

                eventually(2.seconds, AssertionError::class.java) {
                    oldMonitor.status() shouldBe STOPPED
                }
            }

            test("Health check configuration is removed") {
                // From previous test:
                val oldMonitor = serviceDb.get("app").get().styxService as AbstractStyxService

                writeOrigins("""
                        # test: removing health check configuration
                        --- 
                        - id: "app"
                          path: "/"
                          origins:
                          - { id: "app-01", host: "localhost:9090" }
                         """.trimIndent(), debug = true)

                eventually(2.seconds, AssertionError::class.java) {
                    serviceDb.get("app").isPresent shouldBe false
                    oldMonitor.status() shouldBe STOPPED
                }
            }

            service.stop()
        }

        context("Host proxy changes") {
            val objectStore = StyxObjectStore<RoutingObjectRecord>()
            val serviceDb = StyxObjectStore<ProviderObjectRecord>()
            val service = serviceWithInitialConfig(objectStore, serviceDb)

            test("Connection pool settings changes") {
                writeOrigins("""
                    ---
                    - id: "app"
                      path: "/"
                      connectionPool:
                        maxConnectionsPerHost: 111
                      origins:
                      - { id: "app2", host: "localhost:9091" }
                    """.trimIndent())

                eventually(2.seconds, AssertionError::class.java) {
                    objectStore.get("app.app2").isPresent shouldBe true
                    objectStore.get("app.app2").get().let {
                        it.config.get("connectionPool", ConnectionPoolSettings::class.java)
                                .let {
                                    it.maxConnectionsPerHost() shouldBe 111
                                }
                    }
                }
            }

            test("Response timeout changes") {
                writeOrigins("""
                    ---
                    - id: "app"
                      path: "/"
                      responseTimeoutMillis: 1982821
                      origins:
                      - { id: "app2", host: "localhost:9091" }
                    """.trimIndent())

                eventually(2.seconds, AssertionError::class.java) {
                    objectStore.get("app.app2").isPresent shouldBe true
                    objectStore.get("app.app2").get().let {
                        it.config.get("responseTimeoutMillis", Int::class.java)
                                .let {
                                    it shouldBe 1982821
                                }
                    }
                }
            }

            test("Hostname changes") {
                writeOrigins("""
                    ---
                    - id: "app"
                      path: "/"
                      origins:
                      - { id: "app2", host: "abc:9091" }
                    """.trimIndent())

                eventually(2.seconds, AssertionError::class.java) {
                    objectStore.get("app.app2").isPresent shouldBe true
                    objectStore.get("app.app2").get().let {
                        it.config.get("host", String::class.java) shouldBe "abc:9091"
                    }
                }
            }

            test("Port number changes") {
                writeOrigins("""
                    ---
                    - id: "app"
                      path: "/"
                      origins:
                      - { id: "app2", host: "abc:8080" }
                    """.trimIndent())

                eventually(2.seconds, AssertionError::class.java) {
                    objectStore.get("app.app2").isPresent shouldBe true
                    objectStore.get("app.app2").get().let {
                        it.config.get("host", String::class.java) shouldBe "abc:8080"
                    }
                }
            }

            service.stop()
        }

        context("Path mapping changes") {
            val objectStore = StyxObjectStore<RoutingObjectRecord>()
            val serviceDb = StyxObjectStore<ProviderObjectRecord>()
            val service = serviceWithInitialConfig(objectStore, serviceDb,
                    initialObjectCount = 5,
                    config = """
                        ---
                        - id: "appA"
                          path: "/appA/"
                          origins:
                          - { id: "appA-01", host: "localhost:9090" }
                        - id: "appB"
                          path: "/appB/"
                          origins:
                          - { id: "appB-01", host: "localhost:9190" }
                         """.trimIndent())

            test("Updates path prefix router when path mapping changes") {
                writeOrigins("""
                    # Apply new path prefix mapping
                    ---
                    - id: "appA"
                      path: "/new-path-appA/"
                      origins:
                      - { id: "appA-01", host: "localhost:9090" }
                    - id: "appB"
                      path: "/new-path-appB/"
                      origins:
                      - { id: "appB-01", host: "localhost:9190" }
                    """.trimIndent())

                eventually(2.seconds, AssertionError::class.java) {
                    objectStore.get("pathPrefixRouter").get().let {
                        it.config.get("routes", object : TypeReference<List<PathPrefixRouter.PathPrefixConfig>>() {})
                                .let {
                                    it[0].prefix() shouldBe "/new-path-appA/"
                                    it[1].prefix() shouldBe "/new-path-appB/"
                                }
                    }
                }
            }

            service.stop()
        }

        context("Error handling") {
            val objectStore = StyxObjectStore<RoutingObjectRecord>()
            val serviceDb = StyxObjectStore<ProviderObjectRecord>()
            val path = originsConfig.absolutePath
            val service = serviceWithInitialConfig(objectStore, serviceDb)

            test("Keeps the original configuration when a syntax error occurs") {
                writeOrigins("""
                    ---
                    - something's wrong
                    """.trimIndent())

                delay(2.seconds.toMillis())
                objectStore.entrySet().size shouldBe 3

                objectStore.entrySet()
                        .map { (key, record) -> Triple(key, record.type, record.tags) }
                        .shouldContainAll(
                                Triple("app.app-01", "HostProxy", setOf("app", OBJECT_CREATOR_TAG)),
                                Triple("app", "LoadBalancingGroup", setOf(OBJECT_CREATOR_TAG)),
                                Triple("pathPrefixRouter", "PathPrefixRouter", setOf(OBJECT_CREATOR_TAG)))
            }

            test("Keeps the original configuration when origins file is removed") {
                originsConfig.delete()
                originsConfig.exists() shouldBe false

                eventually(2.seconds, AssertionError::class.java) {
                    objectStore.entrySet().size shouldBe 3

                    objectStore.entrySet()
                            .map { (key, record) -> Triple(key, record.type, record.tags) }
                            .shouldContainAll(
                                    Triple("app.app-01", "HostProxy", setOf("app", OBJECT_CREATOR_TAG)),
                                    Triple("app", "LoadBalancingGroup", setOf(OBJECT_CREATOR_TAG)),
                                    Triple("pathPrefixRouter", "PathPrefixRouter", setOf(OBJECT_CREATOR_TAG)))
                }
            }

            test("Recovers when the file becomes availabe again") {

                writeOrigins("""
                    ---
                    - id: "appB"
                      path: "/"
                      origins:
                        - { id: "appB-01", host: "localhost:9999" }
                    """.trimIndent())

                eventually(2.seconds, AssertionError::class.java) {
                    objectStore.entrySet().size shouldBe 3

                    objectStore.entrySet()
                            .map { (key, record) -> Triple(key, record.type, record.tags) }
                            .shouldContainAll(
                                    Triple("appB.appB-01", "HostProxy", setOf("appB", OBJECT_CREATOR_TAG)),
                                    Triple("appB", "LoadBalancingGroup", setOf(OBJECT_CREATOR_TAG)),
                                    Triple("pathPrefixRouter", "PathPrefixRouter", setOf(OBJECT_CREATOR_TAG)))
                }
            }

            service.stop()
        }
    }

    internal fun writeOrigins(text: String, debug: Boolean = false) {
        originsConfig.writeText(text)
        if (debug) {
            LOGGER.info("new origins file: \n${originsConfig.readText()}")
        }
    }

    private val initialConfig = """
            ---
            - id: "app"
              path: "/"
              origins:
              - { id: "app-01", host: "localhost:9090" }
             """.trimIndent()

    internal fun serviceWithInitialConfig(
            routeDb: StyxObjectStore<RoutingObjectRecord>,
            serviceDb: StyxObjectStore<ProviderObjectRecord>,
            debug: Boolean = false,
            initialObjectCount: Int = 3,
            config: String = initialConfig): YamlFileConfigurationService {

        writeOrigins(config, debug)

        val service = YamlFileConfigurationService(
                routeDb,
                OriginsConfigConverter(serviceDb, RoutingObjectFactoryContext(objectStore = routeDb).get(), "origins-cookie"),
                YamlFileConfigurationServiceConfig(originsConfig.absolutePath),
                serviceDb)

        service.start().join()
        eventually(2.seconds, AssertionError::class.java) {
            routeDb.entrySet().size shouldBe initialObjectCount
        }

        return service
    }
}


internal fun <T> JsonNode.get(child: String, targetType: Class<T>): T = MAPPER.readValue(this.get(child).traverse(), targetType)

internal fun <T> JsonNode.get(child: String, targetType: TypeReference<T>): T = MAPPER.readValue(this.get(child).traverse(), targetType)

private val MAPPER = ObjectMappers.addStyxMixins(ObjectMapper(YAMLFactory()))
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, true)
