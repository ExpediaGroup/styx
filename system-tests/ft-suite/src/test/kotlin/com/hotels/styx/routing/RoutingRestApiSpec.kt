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
package com.hotels.styx.routing

import com.hotels.styx.StyxConfig
import com.hotels.styx.StyxServer
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpRequest.delete
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpRequest.put
import com.hotels.styx.api.HttpResponseStatus.CREATED
import com.hotels.styx.api.HttpResponseStatus.NOT_FOUND
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.startup.StyxServerComponents
import com.hotels.styx.support.ResourcePaths.fixturesHome
import io.kotlintest.Spec
import io.kotlintest.TestCase
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import reactor.core.publisher.toMono
import java.nio.charset.StandardCharsets.UTF_8

class RoutingRestApiSpec : StringSpec() {

    val originsOk = fixturesHome(RoutingRestApiSpec::class.java, "/conf/origins/origins-correct.yml")
    val yamlText = """
        proxy:
          connectors:
            http:
              port: 0

            https:
              port: 0
              sslProvider: JDK
              sessionTimeoutMillis: 300000
              sessionCacheSize: 20000

        services:
          factories:
            backendServiceRegistry:
              class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry${'$'}Factory"
              config: {originsFile: "$originsOk"}

        admin:
          connectors:
            http:
              port: 0

        httpPipeline: root
      """.trimIndent()

    init {
        "Creates and updates new routing object" {
            client.send(
                    put("/admin/routing/objects/responder")
                            .header(HOST, "${styxServer.adminHttpAddress().hostName}:${styxServer.adminHttpAddress().port}")
                            .body("""
                                type: StaticResponseHandler
                                config:
                                  status: 200
                                  content: "Responder"
                            """.trimIndent(), UTF_8)
                            .build())
                    .toMono()
                    .block()!!
                    .status() shouldBe CREATED

            client.send(
                    put("/admin/routing/objects/root")
                            .header(HOST, "${styxServer.adminHttpAddress().hostName}:${styxServer.adminHttpAddress().port}")
                            .body("""
                                type: InterceptorPipeline
                                config:
                                  handler: responder
                            """.trimIndent(), UTF_8)
                            .build())
                    .toMono()
                    .block()!!
                    .status() shouldBe CREATED

            client.send(
                    get("/11")
                            .header(HOST, "${styxServer.proxyHttpAddress().hostName}:${styxServer.proxyHttpAddress().port}")
                            .build())
                    .toMono()
                    .block()
                    .let {
                        it!!.status() shouldBe (OK)
                        it.bodyAs(UTF_8) shouldBe ("Responder")
                    }

        }

        "Removes routing objects" {
            client.send(
                    delete("/admin/routing/objects/root")
                            .header(HOST, "${styxServer.adminHttpAddress().hostName}:${styxServer.adminHttpAddress().port}")
                            .build())
                    .toMono()
                    .block()!!
                    .status() shouldBe OK

            client.send(
                    get("/11")
                            .header(HOST, "${styxServer.proxyHttpAddress().hostName}:${styxServer.proxyHttpAddress().port}")
                            .build())
                    .toMono()
                    .block()!!
                    .status() shouldBe NOT_FOUND
        }

        "Lists routing objects" {
            client.send(
                    put("/admin/routing/objects/responder")
                            .header(HOST, "${styxServer.adminHttpAddress().hostName}:${styxServer.adminHttpAddress().port}")
                            .body("""
                                type: StaticResponseHandler
                                config:
                                  status: 200
                                  content: "Responder"
                            """.trimIndent(), UTF_8)
                            .build())
                    .toMono()
                    .block()!!
                    .status() shouldBe CREATED

            client.send(
                    get("/admin/routing/objects")
                            .header(HOST, "${styxServer.adminHttpAddress().hostName}:${styxServer.adminHttpAddress().port}")
                            .build())
                    .toMono()
                    .block()
                    .let {
                        it!!.status() shouldBe OK
                        it.bodyAs(UTF_8).trim() shouldBe """
                                ---
                                name: "responder"
                                type: "StaticResponseHandler"
                                tags: []
                                config:
                                  status: 200
                                  content: "Responder"

                                ---
                                name: "root"
                                type: "StaticResponseHandler"
                                tags: []
                                config:
                                  status: 200
                                  content: "Root"
                                """.trimIndent().trim()
                    }
        }
    }

    val client: StyxHttpClient = StyxHttpClient.Builder().build()

    val styxServer = StyxServer(StyxServerComponents.Builder()
            .styxConfig(StyxConfig.fromYaml(yamlText))
            .build())

    override fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)
        client.send(
                put("/admin/routing/objects/root")
                        .header(HOST, "${styxServer.adminHttpAddress().hostName}:${styxServer.adminHttpAddress().port}")
                        .body("""
                                type: StaticResponseHandler
                                config:
                                  status: 200
                                  content: "Root"
                            """.trimIndent(), UTF_8)
                        .build())
                .toMono()
                .block()!!
                .status() shouldBe CREATED
    }

    override fun beforeSpec(spec: Spec) {
        styxServer.startAsync().awaitRunning()
    }

    override fun afterSpec(spec: Spec) {
        styxServer.stopAsync().awaitTerminated()
    }
}
