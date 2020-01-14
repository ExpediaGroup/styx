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
package com.hotels.styx.servers

import com.hotels.styx.InetServer
import com.hotels.styx.StyxObjectRecord
import com.hotels.styx.StyxServers.toGuavaService
import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponse
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.routing.RoutingObject
import com.hotels.styx.routing.RoutingObjectFactoryContext
import com.hotels.styx.routing.configBlock
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.ref
import com.hotels.styx.routing.routeLookup
import com.hotels.styx.routing.wait
import com.hotels.styx.support.ResourcePaths.fixturesHome
import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import java.nio.charset.StandardCharsets.UTF_8

class StyxHttpServerTest : FeatureSpec({
    feature("HTTP request handling") {
        val serverConfig = configBlock("""
                port: 0
                handler: aHandler
              """.trimIndent())

        val server = StyxHttpServerFactory().create("test-01", routingContext, serverConfig, db)
        val guavaServer = toGuavaService(server)

        try {
            guavaServer.startAsync().awaitRunning()

            StyxHttpClient.Builder().build()
                    .send(get("/bar")
                            .header(HOST, "localhost:${server.inetAddress().port}")
                            .build())
                    .wait()
                    ?.let {
                        it.status() shouldBe OK
                        it.bodyAs(UTF_8) shouldBe "Hello, test!"
                    }
        } finally {
            guavaServer.stopAsync().awaitTerminated()
        }
    }

    feature("HTTPS request handling") {
        val serverConfig = configBlock("""
                port: 0
                handler: aHandler
                tlsSettings:
                  certificateFile: $crtFile
                  certificateKeyFile: $keyFile
                  sslProvider: JDK 
              """.trimIndent())

        val server = StyxHttpServerFactory().create("test-01", routingContext, serverConfig, db)
        val guavaServer = toGuavaService(server)

        try {
            guavaServer.startAsync().awaitRunning()

            StyxHttpClient.Builder().build()
                    .secure()
                    .send(get("/bar")
                            .header(HOST, "localhost:${server.inetAddress().port}")
                            .build())
                    .wait()
                    ?.let {
                        it.status() shouldBe OK
                        it.bodyAs(UTF_8) shouldBe "Hello, test!"
                    }
        } finally {
            guavaServer.stopAsync().awaitTerminated()
        }
    }

    feature("Max initial line length") {
        val serverConfig = configBlock("""
                port: 0
                handler: aHandler
                maxInitialLength: 100
              """.trimIndent())

        val server = StyxHttpServerFactory().create("test-01", routingContext, serverConfig, db)
        val guavaServer = toGuavaService(server)
        guavaServer.startAsync().awaitRunning()

        scenario("Accepts requests within max initial length") {
            StyxHttpClient.Builder().build().send(get("/a/" + "b".repeat(80))
                    .header(HOST, "localhost:${server.inetAddress().port}")
                    .build())
                    .wait()!!
                    .let {
                        it.status() shouldBe OK
                        it.bodyAs(UTF_8) shouldBe "Hello, test!"
                    }
        }

        scenario("Rejects requests exceeding the initial line length") {
            StyxHttpClient.Builder().build().send(get("/a/" + "b".repeat(95))
                    .header(HOST, "localhost:${server.inetAddress().port}")
                    .build())!!
                    .wait()!!
                    .let {
                        it.status() shouldBe REQUEST_ENTITY_TOO_LARGE
                        it.bodyAs(UTF_8) shouldBe "Request Entity Too Large"
                        it.header("X-Styx-Info").isPresent.shouldBeTrue()
                    }
        }

        guavaServer.stopAsync().awaitTerminated()
    }
})

private val response = HttpResponse.response(OK)
        .header("source", "secure")
        .body("Hello, test!", UTF_8)
        .build()

private val routingContext = RoutingObjectFactoryContext(
        routeRefLookup = routeLookup {
            ref("aHandler" to RoutingObject { _, _ -> Eventual.of(response.stream()) })
        })
        .get()

private val db = StyxObjectStore<StyxObjectRecord<InetServer>>()

private val crtFile = fixturesHome(StyxHttpServerTest::class.java, "/ssl/testCredentials.crt").toString()
private val keyFile = fixturesHome(StyxHttpServerTest::class.java, "/ssl/testCredentials.key").toString()
