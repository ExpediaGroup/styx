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

import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.ProviderObjectRecord
import com.hotels.styx.services.YamlFileConfigurationServiceTest.OriginsServiceConfiguration
import com.hotels.styx.support.matchers.LoggingTestSupport
import io.kotlintest.Spec
import io.kotlintest.matchers.string.shouldMatch
import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec
import io.mockk.mockk
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration

private val LOGGER = LoggerFactory.getLogger(YamlFileConfigurationServiceDuplicateIdentifiersTest::class.java)

class YamlFileConfigurationServiceDuplicateIdentifiersTest : FunSpec() {
    val logger = LoggingTestSupport(YamlFileConfigurationService::class.java);

    private val tempDir = createTempDir(suffix = "-${this.javaClass.simpleName}")
    private val originsFile = File("${tempDir.absolutePath}/config.yml")

    private val objectStore = StyxObjectStore<RoutingObjectRecord>()
    private val serviceDb = StyxObjectStore<ProviderObjectRecord>()
    private val service = OriginsServiceConfiguration(objectStore, serviceDb, originsFile,
            config = """
                        ---
                        - id: "app"
                          path: "/"
                          origins:
                          - { id: "app-01", host: "localhost:9090" }
                    """.trimIndent())
            .createService(name = "zone1")
            .start()
            .waitForObjects(count = 3)

    override fun beforeSpec(spec: Spec) {
        LOGGER.info("Temp directory: " + tempDir.absolutePath)
        LOGGER.info("Origins file: " + originsFile.absolutePath)
        LOGGER.info("Duration: '{}'", Duration.ofMillis(100).toString())
    }

    override fun afterSpec(spec: Spec) {
        tempDir.deleteRecursively()
        service.stop()
    }

    init {
        context("Duplicate object detection") {
            test("Rejects origin file if it would introduce duplicate routing object keys") {
                objectStore.insert("app.app-02", RoutingObjectRecord("HostProxy", setOf("abc"), mockk(), mockk()))
                objectStore.entrySet().size.shouldBe(4)

                writeOrigins(originsFile, """
                        ---
                        - id: "app"
                          path: "/"
                          origins:
                          - { id: "app-01", host: "localhost:9091" } 
                          - { id: "app-02", host: "localhost:9092" } 
                          - { id: "app-03", host: "localhost:9093" } 
                """.trimIndent())

                Thread.sleep(500)

                objectStore.entrySet().forEach {
                    LOGGER.debug("entry: ${it.key} -> ${it.value.type} - ${it.value.tags}")
                }

                objectStore.entrySet().size.shouldBe(4)
            }

            test("Logs an error message for duplicate routing object key") {

                logger.lastMessage()
                        .getFormattedMessage()
                        .shouldMatch(".*Failed to reload new configuration. cause='Object name='app.app-02' already exists. Provider='zone1', file='.*config.yml'.*")
            }

            test("Rejects origin file if it would introduce duplicate health check monitor names") {
                serviceDb.insert("app-monitor", ProviderObjectRecord("HealthCheckMonitor", setOf("abc"), mockk(), mockk()))

                objectStore.entrySet().size.shouldBe(4)
                serviceDb.entrySet().size.shouldBe(1)

                writeOrigins(originsFile, """
                        ---
                        - id: "app"
                          path: "/"
                          healthCheck:
                             uri: /endpoint.txt
                          origins:
                          - { id: "app-01", host: "localhost:9091" } 
                          - { id: "app-03", host: "localhost:9093" } 
                """.trimIndent())

                Thread.sleep(500)

                objectStore.entrySet().forEach {
                    LOGGER.debug("routing entry: ${it.key} -> ${it.value.type} - ${it.value.tags}")
                }
                serviceDb.entrySet().forEach {
                    LOGGER.debug("service entry: ${it.key} -> ${it.value.type} - ${it.value.tags}")
                }

                serviceDb.entrySet().size.shouldBe(1)

                // The above configuration would introduce 5th object unless it is rejected:
                objectStore.entrySet().size.shouldBe(4)
            }


            test("Logs an error message for duplicate health check monitor name") {
                logger.lastMessage()
                        .getFormattedMessage()
                        .shouldMatch(".*Failed to reload new configuration. cause='Health Monitor name='app-monitor' already exists. Provider='zone1', file='.*config.yml'.*")
            }
        }
    }
}


