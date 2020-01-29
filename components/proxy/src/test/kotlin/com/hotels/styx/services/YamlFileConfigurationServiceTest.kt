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

import com.fasterxml.jackson.core.type.TypeReference
import com.hotels.styx.api.extension.service.ConnectionPoolSettings
import com.hotels.styx.api.extension.service.StickySessionConfig
import com.hotels.styx.api.extension.service.spi.AbstractStyxService
import com.hotels.styx.api.extension.service.spi.StyxService
import com.hotels.styx.api.extension.service.spi.StyxServiceStatus.RUNNING
import com.hotels.styx.api.extension.service.spi.StyxServiceStatus.STOPPED
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig
import com.hotels.styx.lbGroupTag
import com.hotels.styx.RoutingObjectFactoryContext
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.handlers.PathPrefixRouter
import com.hotels.styx.ProviderObjectRecord
import io.kotlintest.Matcher
import io.kotlintest.MatcherResult
import io.kotlintest.Spec
import io.kotlintest.eventually
import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.seconds
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.util.Optional

private val LOGGER = LoggerFactory.getLogger(YamlFileConfigurationServiceTest::class.java)

class YamlFileConfigurationServiceTest : FunSpec() {
    val tempDir = createTempDir(suffix = "-${this.javaClass.simpleName}")
    val originsConfig = File("${tempDir.absolutePath}/config.yml")
    val pollInterval = Duration.ofMillis(100).toString()

    override fun beforeSpec(spec: Spec) {
        LOGGER.info("Temp directory: " + tempDir.absolutePath)
        LOGGER.info("Origins file: " + originsConfig.absolutePath)
        LOGGER.info("Duration: '{}'", Duration.ofMillis(100).toString())
    }

    override fun afterSpec(spec: Spec) {
        tempDir.deleteRecursively()
    }

    private fun withService(service: StyxService, action: (StyxService) -> Unit) {
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

                withService(YamlFileConfigurationService(
                        "dc-us-west",
                        routeDb,
                        OriginsConfigConverter(serviceDb, RoutingObjectFactoryContext(objectStore = routeDb).get(), "origins-cookie"),
                        YamlFileConfigurationServiceConfig(originsConfig.absolutePath, pollInterval = pollInterval),
                        serviceDb)) {
                    it.start().join()

                    eventually(2.seconds, AssertionError::class.java) {
                        routeDb.entrySet().size shouldBe 4
                    }
                }
            }

            test("It recovers from syntax errors") {
                val routeDb = StyxObjectStore<RoutingObjectRecord>()
                val serviceDb = StyxObjectStore<ProviderObjectRecord>()
                val service = OriginsServiceConfiguration(routeDb, serviceDb, originsConfig,
                        config = """
                            ---
                            - something's wrong
                            """.trimIndent())
                        .createService()
                        .start(wait = false)
                        .service

                withService(service) {
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
            val service = OriginsServiceConfiguration(objectStore, serviceDb, originsConfig)
                    .createService(name = "zone1")
                    .start()
                    .waitForObjects(count = 3)

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
                    val objects = objectStore.toMap()
                    objects.size shouldBe 4

                    objects["app.app-01"].should(beRoutingObject("HostProxy", setOf(lbGroupTag("app"), "source=zone1")))
                    objects["app.app-02"].should(beRoutingObject("HostProxy", setOf(lbGroupTag("app"), "source=zone1")))
                    objects["app"].should(beRoutingObject("LoadBalancingGroup", setOf("source=zone1")))
                    objects["zone1-router"].should(beRoutingObject("PathPrefixRouter", setOf("source=zone1")))
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
                    val objects = objectStore.toMap()

                    objects.size shouldBe 3

                    objects["app.app-01"].should(beRoutingObject("HostProxy", setOf(lbGroupTag("app"), "source=zone1")))
                    objects["app"].should(beRoutingObject("LoadBalancingGroup", setOf("source=zone1")))
                    objects["zone1-router"].should(beRoutingObject("PathPrefixRouter", setOf("source=zone1")))
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
                    val objects = objectStore.toMap()

                    objects.size shouldBe 3

                    objects["app.app-01"].should(beRoutingObject("HostProxy", setOf(lbGroupTag("app"), "source=zone1")))
                    objects["app"].should(beRoutingObject("LoadBalancingGroup", setOf("source=zone1")))
                    objects["zone1-router"].should(beRoutingObject("PathPrefixRouter", setOf("source=zone1")))

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
                    val objects = objectStore.toMap()

                    objects.size shouldBe 5

                    objects["app.app-01"].should(beRoutingObject("HostProxy", setOf(lbGroupTag("app"), "source=zone1")))
                    objects["app"].should(beRoutingObject("LoadBalancingGroup", setOf("source=zone1")))
                    objects["appB.appB-01"].should(beRoutingObject("HostProxy", setOf(lbGroupTag("appB"), "source=zone1")))
                    objects["app"].should(beRoutingObject("LoadBalancingGroup", setOf("source=zone1")))
                    objects["zone1-router"].should(beRoutingObject("PathPrefixRouter", setOf("source=zone1")))
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
                    val objects = objectStore.toMap()

                    objects.size shouldBe 3

                    objects["appB.appB-01"].should(beRoutingObject("HostProxy", setOf(lbGroupTag("appB"), "source=zone1")))
                    objects["appB"].should(beRoutingObject("LoadBalancingGroup", setOf("source=zone1")))
                    objects["zone1-router"].should(beRoutingObject("PathPrefixRouter", setOf("source=zone1")))
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
                    val objects = objectStore.toMap()

                    objects.size shouldBe 3
                    objects["appB.appB-01"].should(beRoutingObject("HostProxy", setOf(lbGroupTag("appB"), "source=zone1")))
                    objects["appB"].should(beRoutingObject("LoadBalancingGroup", setOf("source=zone1")))
                    objects["zone1-router"].should(beRoutingObject("PathPrefixRouter", setOf("source=zone1")))
                }
            }

            test("Does not re-create unchanged objects") {
                val creationTimes = objectStore.entrySet()
                        .map { (key, record) -> Pair(key, record.creationTime()) }
                        .toMap()

                writeOrigins("""
                        ---
                        - id: "appB"
                          path: "/b"
                          origins:
                          - { id: "appB-01", host: "localhost:8081" }
                          - { id: "appB-02", host: "localhost:8082" }
                         """.trimIndent())

                eventually(2.seconds, AssertionError::class.java) {
                    objectStore.entrySet().size shouldBe 4
                }

                val objects = objectStore.toMap()

                objects["appB.appB-01"].should(beRoutingObject("HostProxy",
                        setOf(creationTimes["appB.appB-01"]!!, lbGroupTag("appB"), "source=zone1")))

                objects["appB"].should(beRoutingObject("LoadBalancingGroup",
                        setOf(creationTimes["appB"]!!, "source=zone1")))

                objects["zone1-router"].should(beRoutingObject("PathPrefixRouter",
                        setOf(creationTimes["zone1-router"]!!, "source=zone1")))
            }

            LOGGER.info("configuration changes - Stopping service [$service]")
            service.stop()
        }

        context("Load balancing group changes") {
            val objectStore = StyxObjectStore<RoutingObjectRecord>()
            val serviceDb = StyxObjectStore<ProviderObjectRecord>()
            val service = OriginsServiceConfiguration(objectStore, serviceDb, originsConfig)
                    .createService(name = "zone1")
                    .start()
                    .waitForObjects(count = 3)

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
                    serviceDb.get("app-monitor").isPresent shouldBe true
                    serviceDb.get("app-monitor").get().let {
                        it.config.get("objects", String::class.java) shouldBe "app"
                        it.config.get("path", String::class.java) shouldBe "http://www/check/me"
                        (it.styxService as AbstractStyxService).status() shouldBe RUNNING
                    }
                }
            }

            test("Health check configuration is modified") {
                // From previous test:
                val oldMonitor = serviceDb.get("app-monitor").get().styxService as AbstractStyxService

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
                    serviceDb.get("app-monitor").isPresent shouldBe true
                    serviceDb.get("app-monitor").get().let {
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
                val oldMonitor = serviceDb.get("app-monitor").get().styxService as AbstractStyxService

                writeOrigins("""
                        # test: removing health check configuration
                        --- 
                        - id: "app"
                          path: "/"
                          origins:
                          - { id: "app-01", host: "localhost:9090" }
                         """.trimIndent(), debug = true)

                eventually(2.seconds, AssertionError::class.java) {
                    serviceDb.get("app-monitor").isPresent shouldBe false
                    oldMonitor.status() shouldBe STOPPED
                }
            }

            service.stop()
        }

        context("Host proxy changes") {
            val objectStore = StyxObjectStore<RoutingObjectRecord>()
            val serviceDb = StyxObjectStore<ProviderObjectRecord>()
            val service = OriginsServiceConfiguration(objectStore, serviceDb, originsConfig)
                    .createService(name = "zone1")
                    .start()
                    .waitForObjects(count = 3)

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
            val service = OriginsServiceConfiguration(objectStore, serviceDb, originsConfig,
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
                    .createService(name = "cloud")
                    .start()
                    .waitForObjects(count = 5)

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
                    objectStore.get("cloud-router").get().let {
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

        context("Path Prefix Router") {
            val objectStore = StyxObjectStore<RoutingObjectRecord>()
            val serviceDb = StyxObjectStore<ProviderObjectRecord>()

            test("name is derived from the provider name when the ingressObject is unspecified") {
                val service = ServiceConfiguration(objectStore, serviceDb, "cloud-origins-provider",
                        YamlFileConfigurationServiceConfig(originsConfig.absolutePath, pollInterval = pollInterval))
                        .createService()
                        .start()

                writeOrigins("""
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

                service.waitForObjects(count = 5)

                val objects = objectStore.toMap()

                objects.containsKey("cloud-origins-provider-router")
                objects["cloud-origins-provider-router"]!!.should(beRoutingObject("PathPrefixRouter", setOf("source=cloud-origins-provider")))

                service.service.stop()
            }

               test("name is set by ingressObject attribute") {
                val service = ServiceConfiguration(objectStore, serviceDb, "cloud-origins-provider",
                        YamlFileConfigurationServiceConfig(
                                originsConfig.absolutePath,
                                ingressObject = "myCloudZone",
                                pollInterval = pollInterval))
                        .createService()
                        .start()

                writeOrigins("""
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

                service.waitForObjects(count = 5)

                val objects = objectStore.toMap()

                objects.containsKey("myCloudZone").shouldBeTrue()
                objects["myCloudZone"]!!.should(beRoutingObject("PathPrefixRouter", setOf("source=cloud-origins-provider")))

                service.service.stop()
            }
        }

        context("Error handling") {
            val objectStore = StyxObjectStore<RoutingObjectRecord>()
            val serviceDb = StyxObjectStore<ProviderObjectRecord>()
            val path = originsConfig.absolutePath
            val service = OriginsServiceConfiguration(objectStore, serviceDb, originsConfig)
                    .createService()
                    .start()
                    .waitForObjects(count = 3)

            test("Keeps the original configuration when a syntax error occurs") {
                writeOrigins("""
                    ---
                    - something's wrong
                    """.trimIndent())

                delay(2.seconds.toMillis())

                val objects = objectStore.toMap()

                objects.size shouldBe 3
                objects["app.app-01"].should(beRoutingObject("HostProxy", setOf(lbGroupTag("app"), "source=origins-provider")))
                objects["app"].should(beRoutingObject("LoadBalancingGroup", setOf("source=origins-provider")))
                objects["origins-provider-router"].should(beRoutingObject("PathPrefixRouter", setOf("source=origins-provider")))
            }

            test("Keeps the original configuration when origins file is removed") {
                originsConfig.delete()
                originsConfig.exists() shouldBe false

                eventually(2.seconds, AssertionError::class.java) {
                    val objects = objectStore.toMap()

                    objects.size shouldBe 3
                    objects["app.app-01"].should(beRoutingObject("HostProxy", setOf(lbGroupTag("app"), "source=origins-provider")))
                    objects["app"].should(beRoutingObject("LoadBalancingGroup", setOf("source=origins-provider")))
                    objects["origins-provider-router"].should(beRoutingObject("PathPrefixRouter", setOf("source=origins-provider")))
                }
            }

            test("Recovers when the file becomes availabe again") {

                LOGGER.info("Writing origins file now")
                writeOrigins("""
                    ---
                    - id: "appC"
                      path: "/"
                      origins:
                        - { id: "appC-01", host: "localhost:9001" }
                        - { id: "appC-02", host: "localhost:9002" }
                    """.trimIndent())

                eventually(2.seconds, AssertionError::class.java) {
                    val objects = objectStore.toMap()

                    objects.size shouldBe 4

                    objects.entries.forEach {
                        LOGGER.info("entry key: ${it.key}")
                    }

                    objects["appC.appC-01"].should(beRoutingObject("HostProxy", setOf(lbGroupTag("appC"), "source=origins-provider")))
                    objects["appC"].should(beRoutingObject("LoadBalancingGroup", setOf("source=origins-provider")))
                    objects["origins-provider-router"].should(beRoutingObject("PathPrefixRouter", setOf("source=origins-provider")))
                }
            }

            service.stop()
        }
    }

    internal fun StyxObjectStore<RoutingObjectRecord>.toMap() = this.entrySet()
            .map { (k, v) -> Pair(k, v) }
            .toMap()

    internal fun beRoutingObject(type: String, mandatoryTags: Collection<String>) = object : Matcher<RoutingObjectRecord?> {
        override fun test(value: RoutingObjectRecord?): MatcherResult {
            val message = "Object mismatch"

            if (value == null) {
                return MatcherResult(false, "{$message}.\nExcpected ${type} but was null",
                        "${message}.\nObject is null")
            }

            if (value.type != type) {
                return MatcherResult(false,
                        "{$message}.\nExcpected ${type} but was ${value.type}",
                        "${message}.\nObject type should not be ${type}")
            }
            if (!value.tags.containsAll(mandatoryTags)) {
                return MatcherResult(false,
                        "${message}.\nShould contain all tags ${mandatoryTags}, but contains ${value.tags}",
                        "${message}.\nShould not contain tags ${value.tags}")
            }

            return MatcherResult(true, "test passed", "test passed")
        }
    }

    internal fun writeOrigins(text: String, debug: Boolean = false) = writeOrigins(originsConfig, text, debug)

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
            config: String = initialConfig,
            name: String = "originsProvider"): YamlFileConfigurationService {

        writeOrigins(config, debug)

        val service = YamlFileConfigurationService(
                name,
                routeDb,
                OriginsConfigConverter(serviceDb, RoutingObjectFactoryContext(objectStore = routeDb).get(), "origins-cookie"),
                YamlFileConfigurationServiceConfig(originsConfig.absolutePath, pollInterval = pollInterval),
                serviceDb)

        return service
    }

    internal data class OriginsServiceConfiguration(
            val routeDb: StyxObjectStore<RoutingObjectRecord>,
            val serviceDb: StyxObjectStore<ProviderObjectRecord>,
            val originsFile: File,
            val pollInterval: String = Duration.ofMillis(100).toString(),
            val config: String = """
                ---
                - id: "app"
                  path: "/"
                  origins:
                  - { id: "app-01", host: "localhost:9090" }
                 """.trimIndent()) {

        fun createService(name: String = "origins-provider", debug: Boolean = true): CreatedService {
            writeOrigins(originsFile, config, debug)

            return ServiceConfiguration(
                    routeDb,
                    serviceDb,
                    name,
                    YamlFileConfigurationServiceConfig(originsFile.absolutePath, pollInterval = pollInterval))
                    .createService()
        }
    }

    internal data class ServiceConfiguration(
            val routeDb: StyxObjectStore<RoutingObjectRecord>,
            val serviceDb: StyxObjectStore<ProviderObjectRecord>,
            val providerName: String,
            val serviceConfig: YamlFileConfigurationServiceConfig) {

        fun createService(): CreatedService {
            val service = YamlFileConfigurationService(
                    this.providerName,
                    this.routeDb,
                    OriginsConfigConverter(this.serviceDb, RoutingObjectFactoryContext(objectStore = this.routeDb).get(), "origins-cookie"),
                    serviceConfig,
                    this.serviceDb)

            return CreatedService(ServiceConfiguration(routeDb, serviceDb, providerName, serviceConfig), service)
        }
    }
}


internal fun writeOrigins(originsConfig: File, text: String, debug: Boolean = false) {
    originsConfig.writeText(text)
    if (debug) {
        LOGGER.info("new origins file: \n${originsConfig.readText()}")
    }
}

internal sealed class ServiceContext

internal data class CreatedService(val config: YamlFileConfigurationServiceTest.ServiceConfiguration, val service: StyxService) : ServiceContext() {
    internal fun start(wait: Boolean = true): CreatedService {
        val startFuture = this.service.start()

        if (wait) {
            startFuture.join()
        }

        return this
    }

    internal fun waitForObjects(count: Int): CreatedService {
        eventually(2.seconds, AssertionError::class.java) {
            this.config.routeDb.entrySet().size shouldBe count
        }

        return this
    }

    fun stop() {
        service.stop().join()
    }
}

