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
import com.hotels.styx.api.ByteStream
import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpHeaderNames.CONNECTION
import com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponse.response
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE
import com.hotels.styx.api.HttpResponseStatus.REQUEST_TIMEOUT
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.extension.Origin.newOriginBuilder
import com.hotels.styx.client.ConnectionSettings
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.client.netty.connectionpool.NettyConnectionFactory
import com.hotels.styx.routing.RoutingObject
import com.hotels.styx.RoutingObjectFactoryContext
import com.hotels.styx.configBlock
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.ref
import com.hotels.styx.routeLookup
import com.hotels.styx.wait
import com.hotels.styx.support.ResourcePaths.fixturesHome
import io.kotlintest.eventually
import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.milliseconds
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import reactor.core.publisher.Flux
import reactor.core.publisher.toFlux
import reactor.core.publisher.toMono
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.util.zip.GZIPInputStream

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

    feature("Response compression") {

        val serverConfig = configBlock("""
                port: 0
                handler: aHandler
                compressResponses: true
              """.trimIndent())

        val server = StyxHttpServerFactory().create("test-01", routingContext, serverConfig, db)
        val guavaServer = toGuavaService(server)
        guavaServer.startAsync().awaitRunning()

        scenario("Responses are compressed if accept-encoding is set to gzip") {
            StyxHttpClient.Builder().build().send(get("/blah")
                    .header(HOST, "localhost:${server.inetAddress().port}")
                    .header("accept-encoding", "7z, gzip")
                    .build())
                    .wait()!!
                    .let {
                        it.status() shouldBe OK
                        it.header("content-encoding").get() shouldBe "gzip"
                        ungzip(it.body(), UTF_8) shouldBe "Hello, test!"
                    }
        }

        scenario("Does not compress response if accept-encoding not sent") {
            StyxHttpClient.Builder().build().send(get("/blah")
                    .header(HOST, "localhost:${server.inetAddress().port}")
                    .build())
                    .wait()!!
                    .let {
                        it.status() shouldBe OK
                        it.header("content-encoding").isPresent shouldBe false
                        it.bodyAs(UTF_8) shouldBe "Hello, test!"
                    }
        }

        guavaServer.stopAsync().awaitTerminated()
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

    feature("Max header size") {
        val serverConfig = configBlock("""
                port: 0
                handler: aHandler
                maxHeaderSize: 1024
              """.trimIndent())

        val server = StyxHttpServerFactory().create("test-01", routingContext, serverConfig, db)
        val guavaServer = toGuavaService(server)
        guavaServer.startAsync().awaitRunning()

        scenario("Accepts requests within max header size") {
            StyxHttpClient.Builder().build().send(get("/a/" + "b".repeat(80))
                    .header(HOST, "localhost:${server.inetAddress().port}")
                    .build())
                    .wait()!!
                    .let {
                        it.status() shouldBe OK
                        it.bodyAs(UTF_8) shouldBe "Hello, test!"
                    }
        }

        scenario("Rejects requests exceeding the max header size") {
            StyxHttpClient.Builder().build().send(get("/a/" + "b".repeat(95))
                    .header(HOST, "localhost:${server.inetAddress().port}")
                    .header("test-1", "x".repeat(1024))
                    .header("test-2", "x".repeat(1024))
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

    feature("Request timeout") {
        scenario("Responds with 408 Request Timeout when it doesn't receive HTTP request in time.") {
            val serverConfig = configBlock("""
                port: 0
                handler: aggregator
                requestTimeoutMillis: 50
              """.trimIndent())

            val server = StyxHttpServerFactory().create("test-01", routingContext, serverConfig, db)
            val guavaServer = toGuavaService(server)

            try {
                guavaServer.startAsync().awaitRunning()

                StyxHttpClient.Builder().build()
                        .streaming()
                        .send(LiveHttpRequest.get("/live")
                                .header(HOST, "localhost:${server.inetAddress().port}")
                                .header(CONTENT_LENGTH, 100)
                                .body(ByteStream(Flux.never()))
                                .build())
                        .wait()
                        .aggregate(1024)
                        .toFlux()
                        .blockFirst()!!
                        .let {
                            it.status() shouldBe REQUEST_TIMEOUT
                            it.header(CONNECTION).get() shouldBe "close"
                        }

            } finally {
                guavaServer.stopAsync().awaitTerminated()
            }
        }
    }

    feature("Keep alive settings") {
        val serverConfig = configBlock("""
                port: 0
                handler: aggregator
                keepAliveTimeoutMillis: 500
              """.trimIndent())

        val server = StyxHttpServerFactory().create("test-01", routingContext, serverConfig, db)
        val guavaServer = toGuavaService(server)
        guavaServer.startAsync().awaitRunning()

        val connection = NettyConnectionFactory.Builder()
                .build()
                .createConnection(
                        newOriginBuilder("localhost", server.inetAddress().port).build(),
                        ConnectionSettings(250))
                .block()!!

        scenario("Should keep HTTP1/1 client connection open after serving the response.") {
            connection.write(
                    get("/").header(HOST, "localhost:${server.inetAddress().port}")
                            .header(CONTENT_LENGTH, 0)
                            .build()
                            .stream())
                    .toMono()
                    .block()!!
                    .aggregate(1024)
                    .toMono()
                    .block()!!

            Thread.sleep(100)

            connection.isConnected shouldBe(true)
        }

        scenario("Should close the connection after keepAlive time") {
            Thread.sleep(500)

            connection.isConnected shouldBe(false)
        }

        guavaServer.stopAsync().awaitTerminated()
    }

    feature("maxConnectionsCount") {
        val serverConfig = configBlock("""
                port: 0
                handler: aggregator
                maxConnectionsCount: 2 
                bossThreadsCount: 1
                workerThreadsCount: 1
              """.trimIndent())

        val server = StyxHttpServerFactory().create("test-01", routingContext, serverConfig, db)
        val guavaServer = toGuavaService(server)
        guavaServer.startAsync().awaitRunning()

        scenario("Rejects concurrent connections beyond max connection count") {
            val connection1 = createConnection(server.inetAddress().port)
            val connection2 = createConnection(server.inetAddress().port)
            val connection3 = createConnection(server.inetAddress().port)

            eventually(500.milliseconds, AssertionError::class.java) {
                connection1.isConnected shouldBe (true)
                connection2.isConnected shouldBe (true)
                connection3.isConnected shouldBe (false)
            }
        }

        guavaServer.stopAsync().awaitTerminated()
    }

})

private fun createConnection(port: Int) = NettyConnectionFactory.Builder()
        .build()
        .createConnection(newOriginBuilder("localhost", port).build(), ConnectionSettings(250))
        .block()!!

private val response = response(OK)
        .header("source", "secure")
        .header("content-type", "text/plain")
        .body("Hello, test!", UTF_8)
        .build()

private val compressedResponse = response(OK)
        .header("source", "secure")
        .header("content-type", "text/plain")
        .header("content-encoding", "gzip")
        .body("Hello, test!", UTF_8) // Not actually compressed, just claims to be, which is all we want.
        .build()

private val routingContext = RoutingObjectFactoryContext(
        routeRefLookup = routeLookup {
            ref("aHandler" to RoutingObject { request, _ ->
                when(request.url().toString()) {
                    "/compressed" -> Eventual.of(compressedResponse.stream())
                    else -> Eventual.of(response.stream())
                }
            })

            ref("aggregator" to RoutingObject { request, _ ->
                request
                        .aggregate(1024)
                        .flatMap { Eventual.of(response.stream()) }
            })
        })
        .get()

private fun ungzip(content: ByteArray, charset: Charset): String = GZIPInputStream(content.inputStream()).bufferedReader(charset).use { it.readText() }

private val db = StyxObjectStore<StyxObjectRecord<InetServer>>()

private val crtFile = fixturesHome(StyxHttpServerTest::class.java, "/ssl/testCredentials.crt").toString()
private val keyFile = fixturesHome(StyxHttpServerTest::class.java, "/ssl/testCredentials.key").toString()
