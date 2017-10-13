/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.admin

import java.nio.charset.Charset

import _root_.io.netty.handler.codec.http.HttpResponseStatus.{BAD_GATEWAY, METHOD_NOT_ALLOWED}
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.google.common.net.HttpHeaders.CONTENT_LENGTH
import com.hotels.styx.api._
import com.hotels.styx.utils.StubOriginHeader.STUB_ORIGIN_INFO
import com.hotels.styx.support.backends.FakeHttpServer.HttpStartupConfig
import com.hotels.styx.support.configuration.{HealthCheckConfig, HttpBackend, Origins}
import com.hotels.styx.support.server.FakeHttpServer
import com.hotels.styx.support.server.UrlMatchingStrategies.urlStartingWith
import com.hotels.styx.{DefaultStyxConfiguration, StyxProxySpec}
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.duration._

class OriginsCommandsSpec extends FeatureSpec
  with StyxProxySpec
  with DefaultStyxConfiguration
  with GivenWhenThen
  with ShouldMatchers
  with BeforeAndAfterAll
  with Eventually {
  info("As a tech ops person")
  info("I want to be able to enable or disable an origin")
  info("So I can deploy to it or work on it.")

  val origin1 = configureAndStart("appOne", "appOne-01")
  val origin2 = configureAndStart("appTwo", "appTwo-01")

  override protected def beforeAll() = {
    super.beforeAll()

    styxServer.setBackends(
      "/appOne/" -> HttpBackend(
        "appOne",
        Origins(origin1),
        healthCheckConfig = HealthCheckConfig(
          uri = Some("/appOne/version.txt"),
          interval = 100.millis,
          healthyThreshold = 1,
          unhealthyThreshold = 1
        )
      ),
      "/appTwo/" -> HttpBackend(
        "appTwo", Origins(origin2),
        healthCheckConfig = HealthCheckConfig(
          uri = Some("/appOne/version.txt"),
          interval = 100.millis,
          healthyThreshold = 1,
          unhealthyThreshold = 1
        )
      )
    )
  }

  override protected def afterAll() = {
    val (response, body) = get(styxServer.adminURL("/admin/origins/status"))
    val originsStatus: String = body
    println("after test: " + originsStatus)
    println("Styx metrics: " + getStyxMetricsSnapshot)

    origin1.stop()
    origin2.stop()

    super.afterAll()
  }

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(500, Millis)))

  feature("Origins Admins") {

    scenario("Disable a previously active origin") {
      Given("an active origin")
      enableOrigin("appOne", "appOne-01")
      eventually(timeout(5 seconds)) {
        val (response, body) = get(styxServer.routerURL("/appOne/"))
        body should include(s"Response From appOne-01")
      }

      When("a disable command is issued")
      disableOrigin("appOne", "appOne-01")

      Then("no more traffic should be routed")
      eventually(timeout(5 seconds)) {
        val (response, body) = get(styxServer.routerURL("/appOne/"))
        assert(response.status() == BAD_GATEWAY)
      }

      And("origins status page shows the origin as disabled")
      eventually(timeout(5 seconds)) {
        getCurrentOriginsStatusSnapshot should include(
          s"""disabledOrigins":[{"id":"appOne-01","host":"localhost:${origin1.port()}"}]"""
        )
      }
    }

    scenario("Enable a previously disabled origin") {
      Given("a disabled origin")
      disableOrigin("appOne", "appOne-01")
      eventually(timeout(5 seconds)) {
        getCurrentOriginsStatusSnapshot should include(
          s"""disabledOrigins":[{"id":"appOne-01","host":"localhost:${origin1.port()}"}]"""
        )
      }

      When("a enable command is issued")
      val enableCmdResponse = enableOrigin("appOne", "appOne-01")

      Then("traffic should be routed to the newly enabled origin")
      eventually(timeout(5 seconds)) {
        val (response, body) = get(styxServer.routerURL("/appOne/"))
        body should include(s"Response From appOne-01")
      }

      And("origins status page shows the origin as active")
      eventually(timeout(5 seconds)) {
        getCurrentOriginsStatusSnapshot should include(
          s""""activeOrigins":[{"id":"appOne-01","host":"localhost:${origin1.port()}"}]"""
        )
      }
    }

    scenario("Only POST method is allowed") {
      When("a command with GET method is issued")
      val (response, body) = sendAGetRequest("appOne", "appOne-01")

      Then("A Method not allowed error should be shown")
      response.status() should be(METHOD_NOT_ALLOWED)
      body should include("Method Not Allowed. Only [POST] is allowed for this request.")
    }

    def disableOrigin(appId: String, originId: String) = {
      post(styxServer.adminURL("/admin/tasks/origins?cmd=disable_origin&appId=%s&originId=%s".format(appId, originId)), "")
    }

    def enableOrigin(appId: String, originId: String) = {
      post(styxServer.adminURL("/admin/tasks/origins?cmd=enable_origin&appId=%s&originId=%s".format(appId, originId)), "")
    }
  }

  def sendAGetRequest(appId: String, originId: String) = {
    get(styxServer.adminURL("/admin/tasks/origins?cmd=enable_origin&appId=%s&originId=%s".format(appId, originId)))
  }

  def getOriginsStatus = get(styxServer.adminURL("/admin/origins/status"))


  def getCurrentOriginsStatusSnapshot: String = {
    val (response, body) = getOriginsStatus
    body
  }

  def getStyxMetricsSnapshot: String = {
    val (originsStatus, body) = get(styxServer.adminURL("/admin/metrics"))
    body
  }

  private def get(url: String) = {
    decodedRequest(HttpRequest.Builder.get(url).build())
  }

  private def post(url: String, content: String) = {
    decodedRequest(HttpRequest.Builder.post(url).body(content).build())
  }

  def configureAndStart(appId: String, originId: String): FakeHttpServer = {
    val response = s"Response From $originId"
    val path: String = s"/$appId/"

    HttpStartupConfig(appId = appId, originId = originId)
      .start()
      .reset()
      .stub(urlStartingWith(path), aResponse
        .withStatus(200)
        .withHeader(CONTENT_LENGTH, response.getBytes(Charset.defaultCharset()).size.toString)
        .withHeader(STUB_ORIGIN_INFO.toString, s"$originId-localhost")
        .withBody(response))
  }

}
