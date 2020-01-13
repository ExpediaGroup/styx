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
package com.hotels.styx.resiliency

import com.github.tomakehurst.wiremock.client.WireMock
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.server.HttpConnectorConfig
import com.hotels.styx.servers.MockOriginServer
import com.hotels.styx.support.StyxServerProvider
import com.hotels.styx.support.proxyHttpHostHeader
import com.hotels.styx.support.threadCount
import com.hotels.styx.support.wait
import io.kotlintest.Spec
import io.kotlintest.eventually
import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.matchers.withClue
import io.kotlintest.seconds
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files.copy
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

class OriginResourcesSpec : StringSpec() {
    val count = 20

    val mockServer = MockOriginServer.create("", "", 0, HttpConnectorConfig(0))
            .start()
            .stub(WireMock.get(WireMock.urlMatching("/.*")), WireMock.aResponse()
                    .withStatus(200)
                    .withBody("mock-server-01"))

    val initialThreadcount = threadCount("Styx-Client-Worker")
    init {
        "Client thread pool configuration" {
            val clientThreadCount = run {
                writeConfig(styxOriginsFile,
                        "---\n" + (1..count)
                                .map { appDeclaration("aaa-$it") }
                                .joinToString("\n"))

                eventually(2.seconds, AssertionError::class.java) {
                    (1..count).all { configurationApplied("/aaa-$it") }.shouldBeTrue()
                }

                threadCount("Styx-Client-Worker")
            }

            // From the static configuration below
            clientThreadCount-initialThreadcount shouldBe 3
        }

        "Uses the same thread pool for reloaded origins" {

            val threadCountBefore = run {
                writeConfig(styxOriginsFile,
                        "---\n" + (1..count)
                                .map { appDeclaration("aaa-$it") }
                                .joinToString("\n"))

                eventually(2.seconds, AssertionError::class.java) {
                    (1..count).all { configurationApplied("/aaa-$it") }.shouldBeTrue()
                }

                threadCount("Styx-Client-Worker")
            }

            withClue("No `Styx-Client-Worker` threads found. Has the thrad name changed?") {
                threadCountBefore shouldBeGreaterThan 1
            }

            val threadCountAfter = run {
                writeConfig(styxOriginsFile,
                        "---\n" + (1..count)
                                .map { appDeclaration("bbb-$it") }
                                .joinToString("\n"))

                eventually(2.seconds, AssertionError::class.java) {
                    (1..count).all { configurationApplied("/bbb-$it") }.shouldBeTrue()
                }

                threadCount("Styx-Client-Worker")
            }

            threadCountBefore shouldBe threadCountAfter
        }
    }

    val tempDir = createTempDir()
    val styxOriginsFile = File(tempDir, "origins.yml").toPath()

    private val configTemplate = """
        ---
        - id: "%s"
          path: "%s"
          connectionPool:
            maxConnectionsPerHost: 45
            maxPendingConnectionsPerHost: 15
            socketTimeoutMillis: 120000
            connectTimeoutMillis: 1000
            pendingConnectionTimeoutMillis: 8000
          healthCheck:
            uri: "/version.txt"
            intervalMillis: 5000
          responseTimeoutMillis: 60000
          origins:
          - { id: "app1", host: "localhost:%d" }
    """.trimIndent()

    val styxServer = StyxServerProvider("""
        ---
        proxy:
          connectors:
            http:
              port: 0
          clientWorkerThreadsCount: 3

        admin:
          connectors:
            http:
              port: 0

        services:
          factories:
            backendServiceRegistry:
              class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry${'$'}Factory"
              config:
                originsFile: "${styxOriginsFile.toString().replace("\\", "/")}"
                monitor:
                  enabled: true
    """.trimIndent())


    override fun beforeSpec(spec: Spec) {
        writeConfig(styxOriginsFile, configTemplate.format("appv1", "/app01/", mockServer.port()))
        styxServer.restart()
    }

    override fun afterSpec(spec: Spec) {
        styxServer.stop()
        mockServer.stop()
    }

    fun appDeclaration(prefix: String) = """
                        - id: "$prefix"
                          path: "/$prefix"
                          origins:
                            - { id: "$prefix", host: "localhost:${mockServer.port()}" }
                        """.trimIndent()

    fun configurationApplied(prefix: String) = client.send(
            get(prefix)
                    .header(HOST, styxServer().proxyHttpHostHeader())
                    .build())!!
            .wait(debug = false)!!
            .status() == OK
}

val client: StyxHttpClient = StyxHttpClient.Builder().build()

fun writeConfig(path: Path, text: String): Unit {
    text.toByteArray(UTF_8)
    copy(ByteArrayInputStream(text.toByteArray(UTF_8)), path, REPLACE_EXISTING)
}

