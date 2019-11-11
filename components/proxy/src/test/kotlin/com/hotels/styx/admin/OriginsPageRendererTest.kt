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
import com.google.common.net.MediaType.HTML_UTF_8
import com.hotels.styx.common.http.handler.HttpContentHandler
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig
import com.hotels.styx.lbGroupTag
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.server.AdminHttpRouter
import com.hotels.styx.server.HttpConnectorConfig
import com.hotels.styx.server.netty.NettyServerBuilder
import com.hotels.styx.server.netty.WebServerConnectorFactory
import com.hotels.styx.services.OriginsPageRenderer
import com.hotels.styx.sourceTag
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.matchers.string.shouldNotContain
import io.kotlintest.specs.FunSpec
import io.mockk.mockk
import java.nio.charset.StandardCharsets.UTF_8

fun jsonNodeDef(text: String) = YamlConfig(text).`as`(JsonNode::class.java)

fun httpConfig(host: String) = jsonNodeDef("""
            host: $host
            """)!!

fun httpsConfig(host: String) = jsonNodeDef("""
            host: $host
            tlsSettings:
              trustAllCerts: true
            """)!!

private val assetsRoot = ""

class OriginsPageRendererTest : FunSpec({
    context("!Origins Dashboard Page") {
        val routeDb = StyxObjectStore<RoutingObjectRecord>()

        routeDb.insert("router", RoutingObjectRecord.create(
                "PathPrefixRouter",
                setOf(sourceTag("dcProvider")),
                jsonNodeDef("""
                    routes:
                    - prefix: /
                      destination: landingApp
                    - prefix: /shopping
                      destination: shoppingApp
                """.trimIndent()),
                mockk()))

        routeDb.insert("landingApp", RoutingObjectRecord.create(
                "LoadBalancingGroup",
                setOf(sourceTag("dcProvider")),
                jsonNodeDef("""
                    origins: landing
                """.trimIndent()),
                mockk()))

        routeDb.insert("landing-01", RoutingObjectRecord.create(
                "HostProxy",
                setOf(lbGroupTag("landing"),
                        sourceTag("dcProvider")),
                httpConfig("lahost:80"),
                mockk()))

        routeDb.insert("landing-02", RoutingObjectRecord.create(
                "HostProxy",
                setOf(lbGroupTag("landing"),
                        sourceTag("dcProvider"),
                        "state:inactive"),
                httpConfig("lahost2:80"),
                mockk()))

        routeDb.insert("shoppingApp", RoutingObjectRecord.create(
                "LoadBalancingGroup",
                setOf(sourceTag("dcProvider")),
                jsonNodeDef("""
                    origins: shopping
                """.trimIndent()),
                mockk()))

        routeDb.insert("shopping-01", RoutingObjectRecord.create(
                "HostProxy",
                setOf(lbGroupTag("shopping"),
                        sourceTag("dcProvider")),
                httpsConfig("shhost2:80"),
                mockk()))

        routeDb.insert("cloudRouter", RoutingObjectRecord.create(
                "PathPrefixRouter",
                setOf(sourceTag("cloudProvider")),
                jsonNodeDef("""
                    routes:
                    - prefix: /
                      destination: cloudIngress
                """.trimIndent()),
                mockk()))

        routeDb.insert("cloudIngress", RoutingObjectRecord.create(
                "LoadBalancingGroup",
                setOf(sourceTag("cloudProvider")),
                jsonNodeDef("""
                    origins: cloudIngress
                """.trimIndent()),
                mockk()))

        routeDb.insert("cloudIngress-01", RoutingObjectRecord.create(
                "HostProxy",
                setOf(lbGroupTag("cloudIngress"),
                        sourceTag("cloudProvider")),
                httpsConfig("cloudhost:80"),
                mockk()))

        val page = OriginsPageRenderer(assetsRoot, "dcProvider", routeDb).render()

        println("page: ")
        println(page)

        test("Provider name appears in title") {
            page.shouldContain("<h3 class=\"title grey-text text-darken-3 left-align\">Configured Services</h3>")
            page.shouldContain("<h6 class=\"title grey-text text-darken-3 left-align\">Provider: dcProvider</h6>")
        }

        test("Application title shows path prefix and application name") {
            page.shouldContain("<h6>/ -&gt; landingApp</h6>")
            page.shouldContain("<h6>/shopping -&gt; shoppingApp</h6>")
        }

        test("Ignores objects that were created by other providers") {
            page.shouldNotContain("cloudIngress")
        }

        test("Indicates active origins") {
            val lines = page.split("\n")

            lines.find { it.contains("landing-01") }
                    .shouldContain(">active</span>")

            lines.find { it.contains("shopping-01") }
                    .shouldContain(">active</span>")
        }

        test("Indicates inactive origins") {
            val lines = page.split("\n")

            lines.find { it.contains("landing-02") }
                    .shouldContain(">inactive</span>")
        }
    }

    context("LoadBalancingGroup objects wrapped inside InterceptorPipeline") {
        val routeDb = StyxObjectStore<RoutingObjectRecord>()

        routeDb.insert("router", RoutingObjectRecord.create(
                "PathPrefixRouter",
                setOf(sourceTag("dcProvider")),
                jsonNodeDef("""
                    routes:
                    - prefix: /rewrites/
                      destination: appWithRewrites
                """.trimIndent()),
                mockk()))

        routeDb.insert("appWithRewrites", RoutingObjectRecord.create(
                "InterceptorPipeline",
                setOf(sourceTag("dcProvider")),
                jsonNodeDef("""
                  pipeline:
                  - type: "Rewrite"
                    config:
                    - urlPattern: "/abc/(.*)"
                      replacement: "/${'$'}1"
                  handler:
                    type: "LoadBalancingGroup"
                    config:
                      origins: "rwsApplied"
                """.trimIndent()),
                mockk()))

        routeDb.insert("rwsApplied-01", RoutingObjectRecord.create(
                "HostProxy",
                setOf(lbGroupTag("rwsApplied"),
                        sourceTag("dcProvider")),
                httpConfig("rwshost:80"),
                mockk()))

        val page = OriginsPageRenderer(assetsRoot, "dcProvider", routeDb).render()

        println("page: ")
        println(page)

        test("It detects them wrapped LoadBalancingGroup objects as configured applications") {
            page.shouldContain("<h6>/rewrites/ -&gt; appWithRewrites</h6>")
        }
    }

    context("!Show the page in browser") {
        test("Show page in browser") {
            val routeDb = StyxObjectStore<RoutingObjectRecord>()

            val routingDef = jsonNodeDef("""
              routes:
                - prefix: /
                  destination: landing
                - prefix: /b
                  destination: landingB
                - prefix: /search/
                  destination: search
                - prefix: /searchC/
                  destination: searchC
            """.trimIndent())

            val httpConfig = jsonNodeDef("""
                host: abc
                """)

            val httpsConfig = jsonNodeDef("""
                host: abc
                tlsSettings:
                  trustAllCerts: true
                """)

            routeDb.insert("pathPrefixRouter", RoutingObjectRecord.create("PathPrefixRouter", setOf(sourceTag("provider")), routingDef, mockk()))

            routeDb.insert("landing", RoutingObjectRecord.create("LoadBalancingGroup", setOf(sourceTag("provider")), mockk(), mockk()))
            routeDb.insert("landing-01", RoutingObjectRecord.create("HostProxy", setOf(lbGroupTag("landing"), sourceTag("provider"), "state:active:0"), httpConfig, mockk()))
            routeDb.insert("landing-02", RoutingObjectRecord.create("HostProxy", setOf(lbGroupTag("landing"), sourceTag("provider"), "state:inactive:0"), httpConfig, mockk()))

            routeDb.insert("landingB", RoutingObjectRecord.create("LoadBalancingGroup", setOf(sourceTag("provider")), mockk(), mockk()))
            routeDb.insert("landingB-01", RoutingObjectRecord.create("HostProxy", setOf(lbGroupTag("landingB"), sourceTag("provider"), "state:active"), httpsConfig, mockk()))
            routeDb.insert("landingB-02", RoutingObjectRecord.create("HostProxy", setOf(lbGroupTag("landingB"), sourceTag("provider"), "state:active"), httpsConfig, mockk()))

            routeDb.insert("search", RoutingObjectRecord.create("LoadBalancingGroup", setOf("search", sourceTag("provider")), mockk(), mockk()))
            routeDb.insert("search-01", RoutingObjectRecord.create("HostProxy", setOf(lbGroupTag("search"), sourceTag("provider"), "state:active"), httpConfig, mockk()))
            routeDb.insert("search-02", RoutingObjectRecord.create("HostProxy", setOf(lbGroupTag("search"), sourceTag("provider"), "state:active"), httpConfig, mockk()))
            routeDb.insert("search-03", RoutingObjectRecord.create("HostProxy", setOf(lbGroupTag("search"), sourceTag("provider"), "state:active"), httpConfig, mockk()))

            routeDb.insert("searchC", RoutingObjectRecord.create("LoadBalancingGroup", setOf(lbGroupTag("searchC"), sourceTag("provider")), mockk(), mockk()))
            routeDb.insert("searchC-01", RoutingObjectRecord.create("HostProxy", setOf(lbGroupTag("searchC"), sourceTag("provider"), "state:active"), httpsConfig, mockk()))


            NettyServerBuilder.newBuilder()
                    .host("localhost")
                    .setHttpConnector(
                            WebServerConnectorFactory()
                                    .create(HttpConnectorConfig(9090)))
                    .handlerFactory {
                        AdminHttpRouter().stream("/",
                                HttpContentHandler(HTML_UTF_8.toString(), UTF_8) {
                                    OriginsPageRenderer(assetsRoot, "provider", routeDb).render()
                                })
                    }
                    .build()
                    .startAsync()
                    .awaitRunning()

            Thread.sleep(100000)
        }
    }
})
