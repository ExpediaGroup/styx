/*
  Copyright (C) 2013-2018 Expedia Inc.

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
package com.hotels.styx.client

import java.nio.charset.StandardCharsets.UTF_8

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.client.{ValueMatchingStrategy, WireMock}
import com.hotels.styx.api.FullHttpRequest
import com.hotels.styx.api.FullHttpRequest.get
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpResponseStatus._
import com.hotels.styx.support.ResourcePaths.fixturesHome
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration._
import com.hotels.styx.utils.StubOriginHeader.STUB_ORIGIN_INFO
import com.hotels.styx.{StyxClientSupplier, StyxProxySpec}
import org.scalatest.{FunSpec, SequentialNestedSuiteExecution}

import scala.concurrent.duration._

class HealthCheckSpec extends FunSpec
  with StyxProxySpec
  with StyxClientSupplier
  with SequentialNestedSuiteExecution {

  val logback = fixturesHome(this.getClass, "/conf/logback/logback-debug-stdout.xml")

  val httpApp = FakeHttpServer.HttpStartupConfig(
    appId = "httpApp",
    originId = "httpApp-01"
  )
    .start()
    .stub(WireMock.get(urlMatching("/.*")), originResponse("httpApp-01"))

  val httpsApp = FakeHttpServer.HttpsStartupConfig(
    appId = "httpsApp",
    originId = "httpsApp-01",
    protocols = Seq("TLSv1.1", "TLSv1.2")
  )
    .start()
    .stub(WireMock.get(urlMatching("/.*")), originResponse("httpsApp-01"))

  val mapper = new ObjectMapper(new YAMLFactory) with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)


  override val styxConfig = StyxYamlConfig(
    """
      |proxy:
      |  connectors:
      |    http:
      |      port: 0
      |admin:
      |  connectors:
      |    http:
      |      port: 0
      |request-logging:
      |  enabled: true
    """.stripMargin,
    logbackXmlLocation = logback)

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    styxServer.setBackends(
      "/http/" -> HttpBackend(
        "httpApp",
        healthCheckConfig = HealthCheckConfig(uri = Some("/version.txt"), interval = 100.millis, timeout = 500.millis, healthyThreshold = 1, unhealthyThreshold = 1),
        origins = Origins(httpApp)),
      "/https/" -> HttpsBackend(
        "httpsApp",
        healthCheckConfig = HealthCheckConfig(uri = Some("/version.txt"), interval = 100.millis, timeout = 500.millis, healthyThreshold = 1, unhealthyThreshold = 1),
        tlsSettings = TlsSettings(authenticate = false, sslProvider = "JDK"),
        origins = Origins(httpsApp)
      ))
  }

  override protected def afterAll(): Unit = {
    httpApp.stop()
    httpsApp.stop()
    super.afterAll()
  }

  describe("Backend Service TLS Protocol Setting") {

    it("Marks origins healthy") {
      Thread.sleep(1000L)

      val response = decodedRequest(FullHttpRequest
        .post("/admin/origins/status?pretty")
        .header(HOST, "localhost:" + styxServer.adminPort)
        .build())

      activeOrigins(response.bodyAs(UTF_8), "httpApp") should be (Seq("httpApp-01"))
      activeOrigins(response.bodyAs(UTF_8), "httpsApp") should be (Seq("httpsApp-01"))
    }
  }

  private def httpRequest(path: String) = get(styxServer.routerURL(path)).build()

  private def valueMatchingStrategy(matches: String) = {
    val matchingStrategy = new ValueMatchingStrategy()
    matchingStrategy.setMatches(matches)
    matchingStrategy
  }

  private def originResponse(appId: String) = aResponse
    .withStatus(OK.code())
    .withHeader(STUB_ORIGIN_INFO.toString, appId)
    .withBody("Hello, World!")

  private def activeOrigins(response: String, appId: String) = originStatuses(response, appId, "activeOrigins")

  private def inactiveOrigins(response: String, appId: String) = originStatuses(response, appId, "inactiveOrigins")

  private def originStatuses(response: String, appId: String, state: String): Seq[String] = {
    val tree0 = mapper.readTree(response).get(appId)
    val tree = tree0.get(state)

    val origins = mapper.treeToValue(tree, classOf[List[Map[String, String]]])
    origins.map(o => o.getOrElse("id", ""))
  }

}