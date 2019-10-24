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

import com.fasterxml.jackson.databind.JsonNode
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.server.AdminHttpRouter
import com.hotels.styx.server.HttpConnectorConfig
import com.hotels.styx.server.HttpInterceptorContext
import com.hotels.styx.server.netty.NettyServerBuilder
import com.hotels.styx.server.netty.WebServerConnectorFactory
import io.kotlintest.specs.StringSpec
import io.mockk.mockk
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.dom.create
import kotlinx.html.dom.serialize
import kotlinx.html.head
import kotlinx.html.html
import reactor.core.publisher.toMono
import java.nio.charset.StandardCharsets.UTF_8
import javax.xml.parsers.DocumentBuilderFactory

fun jsonNodeDef(text: String) = YamlConfig(text).`as`(JsonNode::class.java)

class KotlinHtmlHandlerTest : StringSpec() {

    init {
        val routeDb = StyxObjectStore<RoutingObjectRecord>()

        val httpRouter = AdminHttpRouter()
        httpRouter.aggregate("/admin/kotlin/test", KotlinHtmlHandler(routeDb))

        val server = NettyServerBuilder.newBuilder()
                .host("localhost")
                .setHttpConnector(
                        WebServerConnectorFactory()
                                .create(HttpConnectorConfig(9090)))
                .handlerFactory { httpRouter }
                .build()

        val routingDef = jsonNodeDef("""
              routes:
                - prefix: /
                  destination: landing 
                - prefix: /b
                  destination: landingB
                - prefix: /c
                  destination: landingC
                - prefix: /d
                  destination: landingD
                - prefix: /shopping/
                  destination: shopping
                - prefix: /shoppingB/
                  destination: shoppingB
                - prefix: /shoppingC/
                  destination: shoppingC
                - prefix: /shoppingD/
                  destination: shoppingD
                - prefix: /search/
                  destination: search
                - prefix: /searchB/
                  destination: searchB
                - prefix: /searchC/
                  destination: searchC 
                - prefix: /searchD/
                  destination: searchD 
            """.trimIndent())

        val httpConfig = jsonNodeDef("""
            host: abc
            """)

        val httpsConfig = jsonNodeDef("""
            host: abc
            tlsSettings:
              trustAllCerts: true
            """)

        "document should be created " {
            routeDb.insert("pathPrefixRouter", RoutingObjectRecord.create("PathPrefixRouter", setOf(), routingDef, mockk()))

            routeDb.insert("landing", RoutingObjectRecord.create("LoadBalancingGroup", setOf("landing"), mockk(), mockk()))
            routeDb.insert("landing-01", RoutingObjectRecord.create("HostProxy", setOf("landing", "state:active:0"), httpConfig, mockk()))
            routeDb.insert("landing-02", RoutingObjectRecord.create("HostProxy", setOf("landing", "state:inactive:0"), httpConfig, mockk()))

            routeDb.insert("shopping", RoutingObjectRecord.create("LoadBalancingGroup", setOf("shopping"), mockk(), mockk()))
            routeDb.insert("shopping-01", RoutingObjectRecord.create("HostProxy", setOf("shopping", "state:active"), httpsConfig, mockk()))
            routeDb.insert("shopping-02", RoutingObjectRecord.create("HostProxy", setOf("shopping", "state:active"), httpsConfig, mockk()))
            routeDb.insert("shopping-03", RoutingObjectRecord.create("HostProxy", setOf("shopping", "state:inactive"), httpsConfig, mockk()))
            routeDb.insert("shopping-04", RoutingObjectRecord.create("HostProxy", setOf("shopping", "state:inactive"), httpsConfig, mockk()))

            routeDb.insert("search", RoutingObjectRecord.create("LoadBalancingGroup", setOf("search"), mockk(), mockk()))
            routeDb.insert("search-02", RoutingObjectRecord.create("HostProxy", setOf("search", "state:active"), httpConfig, mockk()))

            routeDb.insert("landingB", RoutingObjectRecord.create("LoadBalancingGroup", setOf("landing"), mockk(), mockk()))
            routeDb.insert("landingB-01", RoutingObjectRecord.create("HostProxy", setOf("landingB", "state:active"), httpConfig, mockk()))
            routeDb.insert("landingB-02", RoutingObjectRecord.create("HostProxy", setOf("landingB", "state:active"), httpConfig, mockk()))

            routeDb.insert("shoppingB", RoutingObjectRecord.create("LoadBalancingGroup", setOf("shopping"), mockk(), mockk()))
            routeDb.insert("shoppingB-01", RoutingObjectRecord.create("HostProxy", setOf("shoppingB", "state:active"), httpConfig, mockk()))
            routeDb.insert("shoppingB-02", RoutingObjectRecord.create("HostProxy", setOf("shoppingB", "state:active"), httpConfig, mockk()))
            routeDb.insert("shoppingB-03", RoutingObjectRecord.create("HostProxy", setOf("shoppingB", "state:active"), httpConfig, mockk()))
            routeDb.insert("shoppingB-04", RoutingObjectRecord.create("HostProxy", setOf("shoppingB", "state:active"), httpConfig, mockk()))

            routeDb.insert("searchB", RoutingObjectRecord.create("LoadBalancingGroup", setOf("searchB"), mockk(), mockk()))
            routeDb.insert("searchB-02", RoutingObjectRecord.create("HostProxy", setOf("searchB", "state:active"), httpConfig, mockk()))

            routeDb.insert("landingC", RoutingObjectRecord.create("LoadBalancingGroup", setOf("landingC"), mockk(), mockk()))
            routeDb.insert("landingC-01", RoutingObjectRecord.create("HostProxy", setOf("landingC", "state:active"), httpConfig, mockk()))
            routeDb.insert("landingC-02", RoutingObjectRecord.create("HostProxy", setOf("landingC", "state:active"), httpConfig, mockk()))

            routeDb.insert("shoppingC", RoutingObjectRecord.create("LoadBalancingGroup", setOf("shoppingC"), mockk(), mockk()))
            routeDb.insert("shoppingC-01", RoutingObjectRecord.create("HostProxy", setOf("shoppingC", "state:active"), httpConfig, mockk()))
            routeDb.insert("shoppingC-02", RoutingObjectRecord.create("HostProxy", setOf("shoppingC", "state:active"), httpConfig, mockk()))
            routeDb.insert("shoppingC-03", RoutingObjectRecord.create("HostProxy", setOf("shoppingC", "state:active"), httpConfig, mockk()))
            routeDb.insert("shoppingC-04", RoutingObjectRecord.create("HostProxy", setOf("shoppingC", "state:active"), httpConfig, mockk()))

            routeDb.insert("searchC", RoutingObjectRecord.create("LoadBalancingGroup", setOf("searchC"), mockk(), mockk()))
            routeDb.insert("searchC-02", RoutingObjectRecord.create("HostProxy", setOf("searchC", "state:active"), httpConfig, mockk()))

            routeDb.insert("landingD", RoutingObjectRecord.create("LoadBalancingGroup", setOf("landingD"), mockk(), mockk()))
            routeDb.insert("landingD-01", RoutingObjectRecord.create("HostProxy", setOf("landingD", "state:active"), httpConfig, mockk()))
            routeDb.insert("landingD-02", RoutingObjectRecord.create("HostProxy", setOf("landingD", "state:active"), httpConfig, mockk()))

            routeDb.insert("shoppingD", RoutingObjectRecord.create("LoadBalancingGroup", setOf("shoppingD"), mockk(), mockk()))
            routeDb.insert("shoppingD-01", RoutingObjectRecord.create("HostProxy", setOf("shoppingD", "state:active"), httpConfig, mockk()))
            routeDb.insert("shoppingD-02", RoutingObjectRecord.create("HostProxy", setOf("shoppingD", "state:active"), httpConfig, mockk()))
            routeDb.insert("shoppingD-03", RoutingObjectRecord.create("HostProxy", setOf("shoppingD", "state:active"), httpConfig, mockk()))
            routeDb.insert("shoppingD-04", RoutingObjectRecord.create("HostProxy", setOf("shoppingD", "state:active"), httpConfig, mockk()))

            routeDb.insert("searchD", RoutingObjectRecord.create("LoadBalancingGroup", setOf("searchD"), mockk(), mockk()))
            routeDb.insert("searchD-02", RoutingObjectRecord.create("HostProxy", setOf("searchD", "state:active"), httpConfig, mockk()))

            server.startAsync().awaitRunning()

            Thread.sleep(100000)

            val response = KotlinHtmlHandler(routeDb)
                    .handle(get("/").build(), HttpInterceptorContext.create())
                    .toMono()
                    .block()!!

            val htmlText = response.bodyAs(UTF_8)

            println("my text: $htmlText")
        }

        "!bb" {

            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()!!

            val bar = document.create.div {
                +"hello world!"
            }


            val baz = document.create.html {
                head {

                }
                body {
                    div {
                        +"here:"

                    }

                }
            }

            println(baz.serialize())
        }
    }

}
