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

import com.github.tomakehurst.wiremock.client.WireMock
import com.hotels.styx.STATE_INACTIVE
import com.hotels.styx.STATE_UNREACHABLE
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY
import com.hotels.styx.api.HttpResponseStatus.CREATED
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.RequestCookie.requestCookie
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.server.HttpConnectorConfig
import com.hotels.styx.servers.MockOriginServer
import com.hotels.styx.stateTag
import com.hotels.styx.support.StyxServerProvider
import com.hotels.styx.support.newRoutingObject
import com.hotels.styx.support.proxyHttpHostHeader
import com.hotels.styx.support.removeRoutingObject
import com.hotels.styx.support.wait
import io.kotlintest.Spec
import io.kotlintest.eventually
import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.matchers.string.shouldMatch
import io.kotlintest.matchers.withClue
import io.kotlintest.seconds
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Optional

class LoadBalancingGroupSpec : FeatureSpec() {

    val client: StyxHttpClient = StyxHttpClient.Builder().build()

    val styxServer = StyxServerProvider("""
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
                                  factories: {}

                                httpPipeline: hostProxy
                              """.trimIndent())

    init {
        feature("Initial Configuration") {
            scenario("Load balances between tagged origins") {

                styxServer.restart(configuration = """
                                proxy:
                                  connectors:
                                    http:
                                      port: 0

                                admin:
                                  connectors:
                                    http:
                                      port: 0
                                      
                                services:
                                  factories: {}

                                routingObjects:
                                  app-A-01:
                                    type: HostProxy
                                    tags:
                                      - lbGroup=App-A
                                    config:
                                      host: localhost:${appA01.port()}

                                  app-A-02:
                                    type: HostProxy
                                    tags:
                                      - lbGroup=App-A
                                    config:
                                      host: localhost:${appA02.port()}

                                  app-A-03:
                                    type: HostProxy
                                    tags:
                                      - lbGroup=App-A
                                    config:
                                      host: localhost:${appA03.port()}

                                  app-B-01:
                                    type: HostProxy
                                    tags:
                                      - lbGroup=App-B
                                    config:
                                      host: localhost:${appB01.port()}

                                httpPipeline:
                                  type: LoadBalancingGroup
                                  config:
                                    origins: App-A
                        """.trimIndent())

                for (i in 1..50) {
                    client.send(get("/").header(HOST, styxServer().proxyHttpHostHeader()).build())
                            .wait()
                            .let {
                                withClue("Response should come from App-A instances only") {
                                    it.bodyAs(UTF_8) shouldContain "mock-server-0.".toRegex()
                                }
                            }
                }
            }
        }

        feature("Object discovery") {
            scenario("Ignores unreachable and closed objects") {
                styxServer.restart(configuration = """
                                proxy:
                                  connectors:
                                    http:
                                      port: 0

                                admin:
                                  connectors:
                                    http:
                                      port: 0

                                services:
                                  factories: {}

                                routingObjects:
                                  app-A-01:
                                    type: HostProxy
                                    tags:
                                      - lbGroup=App-A
                                      - ${stateTag(STATE_INACTIVE)}
                                    config:
                                      host: localhost:${appA01.port()}

                                  app-A-02:
                                    type: HostProxy
                                    tags:
                                      - lbGroup=App-A
                                      - ${stateTag(STATE_UNREACHABLE)}
                                      - health=success:3
                                    config:
                                      host: localhost:${appA02.port()}

                                  app-A-03:
                                    type: HostProxy
                                    tags:
                                      - lbGroup=App-A
                                    config:
                                      host: localhost:${appA03.port()}

                                  app-B-01:
                                    type: HostProxy
                                    tags:
                                      - lbGroup=App-B
                                    config:
                                      host: localhost:${appB01.port()}

                                httpPipeline:
                                  type: LoadBalancingGroup
                                  config:
                                    origins: App-A
                        """.trimIndent())

                for (i in 1..50) {
                    withClue("Response should come from active instances only") {
                        client.send(get("/").header(HOST, styxServer().proxyHttpHostHeader()).build())
                                .wait(debug = false)!!.bodyAs(UTF_8) shouldBe "mock-server-03"
                    }
                }
            }
        }

        feature("Configuration via REST API") {
            styxServer.restart(configuration = """
                                proxy:
                                  connectors:
                                    http:
                                      port: 0

                                admin:
                                  connectors:
                                    http:
                                      port: 0
                                      
                                services:
                                  factories: {}

                                httpPipeline:
                                  type: LoadBalancingGroup
                                  config:
                                    origins: App-A
                        """.trimIndent())

            scenario("Detects newly added origins") {

                withClue("Load balancing group shouldn't have been configured yet") {
                    client.send(get("/").header(HOST, styxServer().proxyHttpHostHeader()).build())
                            .wait()!!
                            .let {
                                it.status() shouldBe (BAD_GATEWAY)
                            }
                }

                withClue("Creating HostProxy (host-1) object failed.") {
                    styxServer().newRoutingObject("host-1", """
                        type: HostProxy
                        tags:
                          - lbGroup=App-A
                        config:
                          host: localhost:${appA01.port()}
                    """.trimIndent()) shouldBe (CREATED)
                }

                withClue("Couldn't get a response from mock-server-01") {
                    eventually(1.seconds, AssertionError::class.java) {
                        client.send(get("/").header(HOST, styxServer().proxyHttpHostHeader()).build())
                                .wait()!!
                                .let {
                                    it.status() shouldBe OK
                                    it.bodyAs(UTF_8) shouldBe "mock-server-01"
                                }
                    }
                }
            }

            scenario("... and detects removed origins") {
                withClue("Origin is not reachable") {
                    client.send(get("/").header(HOST, styxServer().proxyHttpHostHeader()).build())
                            .wait()!!
                            .let {
                                it.status() shouldBe (OK)
                            }
                }

                styxServer().removeRoutingObject("host-1") shouldBe OK

                withClue("Origin is still reachable after removal") {
                    client.send(get("/").header(HOST, styxServer().proxyHttpHostHeader()).build())
                            .wait()!!
                            .let {
                                it.status() shouldBe (BAD_GATEWAY)
                            }
                }

            }
        }

        feature("Session Affinity (aka Sticky sessions)") {
            styxServer.restart(configuration = """
                                proxy:
                                  connectors:
                                    http:
                                      port: 0

                                admin:
                                  connectors:
                                    http:
                                      port: 0
                                      
                                services:
                                  factories: {}

                                routingObjects:
                                    app-A-01:
                                      type: HostProxy
                                      tags:
                                        - lbGroup=App-A
                                      config:
                                        host: localhost:${appA01.port()}

                                    app-A-02:
                                      type: HostProxy
                                      tags:
                                        - lbGroup=App-A
                                      config:
                                        host: localhost:${appA02.port()}

                                    stickyApp:
                                      type: LoadBalancingGroup
                                      config:
                                        origins: App-A
                                        stickySession:
                                          enabled: true
                                          timeoutSeconds: 100

                                    normalApp:
                                      type: LoadBalancingGroup
                                      config:
                                        origins: App-B

                                httpPipeline:
                                  type: PathPrefixRouter
                                  config:
                                    routes:
                                      - prefix: /sticky/
                                        destination: stickyApp
                                      - prefix: /normal/
                                        destination: normalApp
                        """.trimIndent())

            scenario("Responds with sticky session cookie when STICKY_SESSION_ENABLED=true") {
                client.send(get("/sticky/").header(HOST, styxServer().proxyHttpHostHeader()).build())
                        .wait()!!
                        .cookie("styx_origin_stickyApp")
                        .get()
                        .let {
                            it.value() shouldMatch "app-A-0[12]".toRegex()
                            it.path().get() shouldBe "/"
                            it.httpOnly().shouldBeTrue()
                            it.maxAge().isPresent.shouldBeTrue()
                            it.maxAge().get() shouldBe (100L)
                        }
            }

            scenario("Responds without sticky session cookie when sticky session is not enabled") {
                client.send(get("/normal/").header(HOST, styxServer().proxyHttpHostHeader()).build())
                        .wait()!!
                        .cookie("styx_origin_app") shouldBe Optional.empty()
            }

            scenario("Routes to origins indicated by sticky session cookie") {
                (1..10).forEach {
                    client.send(get("/sticky/")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .cookies(requestCookie("styx_origin_stickyApp", "app-A-02"))
                            .build())
                            .wait()!!
                            .let {
                                it.status() shouldBe OK
                                it.bodyAs(UTF_8) shouldBe ("mock-server-02")
                            }
                }
            }

            scenario("Routes to origins indicated by sticky session cookie when other cookies are provided") {
                (1..10).forEach {
                    client.send(get("/sticky/")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .cookies(
                                    requestCookie("other_cookie1", "foo"),
                                    requestCookie("other_cookie2", "bar"),
                                    requestCookie("styx_origin_stickyApp", "app-A-02"))
                            .build())
                            .wait()!!
                            .let {
                                it.status() shouldBe OK
                                it.bodyAs(UTF_8) shouldBe ("mock-server-02")
                            }
                }

            }

            scenario("Routes to new origin when the origin indicated by sticky session cookie does not exist") {
                client.send(get("/sticky/")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .cookies(
                                requestCookie("styx_origin_stickyApp", "NA"))
                        .build())
                        .wait()!!
                        .let {
                            it.status() shouldBe OK
                            it.bodyAs(UTF_8) shouldMatch ("mock-server-0.")
                        }
            }

            scenario("!Routes to new origin when the origin indicated by sticky session cookie is no longer available") {
                // See bug: https://github.com/HotelsDotCom/styx/issues/434
            }
        }

        feature("Origins restriction") {

            styxServer.restart(configuration = """
                                proxy:
                                  connectors:
                                    http:
                                      port: 0

                                admin:
                                  connectors:
                                    http:
                                      port: 0
                                      
                                services:
                                  factories: {}

                                routingObjects:
                                    appA-01:
                                      type: HostProxy
                                      tags:
                                        - lbGroup=appA
                                      config:
                                        host: localhost:${appA01.port()}

                                    appA-02:
                                      type: HostProxy
                                      tags:
                                        - lbGroup=appA
                                      config:
                                        host: localhost:${appA02.port()}

                                    appA-03:
                                      type: HostProxy
                                      tags:
                                        - lbGroup=appA
                                      config:
                                        host: localhost:${appA03.port()}

                                    appA-04:
                                      type: HostProxy
                                      tags:
                                        - lbGroup=appA
                                      config:
                                        host: localhost:${appB01.port()}

                                    normalApp:
                                      type: LoadBalancingGroup
                                      config:
                                        origins: appA
                                        originRestrictionCookie: orc

                                httpPipeline: normalApp
                        """.trimIndent())

            scenario("Routes to origin indicated by cookie") {
                for (i in 1..10) {
                    client.send(get("/")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .cookies(requestCookie("orc", "appA-02"))
                            .build())
                            .wait()!!
                            .let {
                                it.status() shouldBe OK
                                it.bodyAs(UTF_8) shouldBe "mock-server-02"
                            }
                }
            }

            scenario("Routes to range of origins indicated by cookie") {
                for (i in 1..10) {
                    client.send(get("/")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .cookies(requestCookie("orc", "appA-0(2|3)"))
                            .build())
                            .wait()!!
                            .let {
                                it.status() shouldBe OK
                                it.bodyAs(UTF_8) shouldMatch "mock-server-0[23]"
                            }
                }
            }

            scenario("Respond with BAD_GATEWAY when all desired hosts are unavailable") {
                for (i in 1..10) {
                    client.send(get("/")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .cookies(requestCookie("orc", "(?!)"))
                            .build())
                            .wait()!!
                            .let {
                                it.status() shouldBe BAD_GATEWAY
                            }
                }
            }

            scenario("Routes to list of origins indicated by cookie.") {
                for (i in 1..10) {
                    client.send(get("/")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .cookies(requestCookie("orc", "appA-02,appA-0[13]"))
                            .build())
                            .wait()!!
                            .let {
                                it.status() shouldBe OK
                                it.bodyAs(UTF_8) shouldMatch "mock-server-0[123]"
                            }
                }
            }
        }
    }

    override fun afterSpec(spec: Spec) {
        appA01.stop()
        appA02.stop()
        appA03.stop()
        appB01.stop()
        styxServer.stop()
    }

    val appA01 = MockOriginServer.create("", "", 0, HttpConnectorConfig(0))
            .start()
            .stub(WireMock.get(WireMock.urlMatching("/.*")), WireMock.aResponse()
                    .withStatus(200)
                    .withBody("mock-server-01"))

    val appA02 = MockOriginServer.create("", "", 0, HttpConnectorConfig(0))
            .start()
            .stub(WireMock.get(WireMock.urlMatching("/.*")), WireMock.aResponse()
                    .withStatus(200)
                    .withBody("mock-server-02"))

    val appA03 = MockOriginServer.create("", "", 0, HttpConnectorConfig(0))
            .start()
            .stub(WireMock.get(WireMock.urlMatching("/.*")), WireMock.aResponse()
                    .withStatus(200)
                    .withBody("mock-server-03"))

    val appB01 = MockOriginServer.create("", "", 0, HttpConnectorConfig(0))
            .start()
            .stub(WireMock.get(WireMock.urlMatching("/.*")), WireMock.aResponse()
                    .withStatus(200)
                    .withBody("mock-server-app-b"))
}
