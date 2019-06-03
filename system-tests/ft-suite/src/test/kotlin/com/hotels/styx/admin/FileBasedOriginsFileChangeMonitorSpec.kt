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
package com.hotels.styx.admin

import com.github.tomakehurst.wiremock.client.WireMock
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.server.HttpConnectorConfig
import com.hotels.styx.servers.MockOriginServer
import com.hotels.styx.support.StyxServerProvider
import com.hotels.styx.support.proxyHttpHostHeader
import com.hotels.styx.support.wait
import io.kotlintest.Spec
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files.copy
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

class FileBasedOriginsFileChangeMonitorSpec: StringSpec() {

    init {
        "Automatically detects changes in origins config file." {
            val reqToApp01 = HttpRequest.get("/app01/x")
                    .header(HOST, styxServer().proxyHttpHostHeader())
                    .build()

            val reqToApp02 = HttpRequest.get("/app02/x")
                    .header(HOST, styxServer().proxyHttpHostHeader())
                    .build()

            client.send(reqToApp01).wait().status() shouldBe OK
            client.send(reqToApp02).wait().status() shouldBe BAD_GATEWAY

            writeConfig(styxOriginsFile, configTemplate.format("appv2", "/app02/", mockServer.port()))
            Thread.sleep(2000)

            client.send(reqToApp01).wait().status() shouldBe BAD_GATEWAY
            client.send(reqToApp02).wait().status() shouldBe OK
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

        admin:
          connectors:
            http:
              port: 0

        services:
          factories:
            backendServiceRegistry:
              class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry${'$'}Factory"
              config:
                originsFile: "${styxOriginsFile.toString().replace("\\","/")}"
                monitor:
                  enabled: true
    """.trimIndent())

    val mockServer = MockOriginServer.create("", "", 0, HttpConnectorConfig(0))
            .start()
            .stub(WireMock.get(WireMock.urlMatching("/.*")), WireMock.aResponse()
                    .withStatus(200)
                    .withBody("mock-server-01"))

    override fun beforeSpec(spec: Spec) {
        writeConfig(styxOriginsFile, configTemplate.format("appv1", "/app01/", mockServer.port()))
        styxServer.restart()
    }

    override fun afterSpec(spec: Spec) {
        styxServer.stop()
        mockServer.stop()
        client.shutdown()
    }
}

val client: StyxHttpClient = StyxHttpClient.Builder().build()

fun writeConfig(path: Path, text: String): Unit {
    println("Updating origins configuration to $path")
    println(text)
    text.toByteArray(UTF_8)
    copy(ByteArrayInputStream(text.toByteArray(UTF_8)), path, REPLACE_EXISTING)
}
