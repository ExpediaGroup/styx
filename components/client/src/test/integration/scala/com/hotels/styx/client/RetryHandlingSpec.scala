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
package com.hotels.styx.client

import java.nio.charset.Charset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpRequest.get
import com.hotels.styx.api.extension.Origin._
import com.hotels.styx.api.extension.service.{BackendService, StickySessionConfig}
import com.hotels.styx.api.extension.{ActiveOrigins, Origin}
import com.hotels.styx.client.OriginsInventory.newOriginsInventoryBuilder
import com.hotels.styx.client.StyxBackendServiceClient.newHttpClientBuilder
import com.hotels.styx.client.loadbalancing.strategies.RoundRobinStrategy
import com.hotels.styx.client.retry.RetryNTimes
import com.hotels.styx.client.stickysession.StickySessionLoadBalancingStrategy
import com.hotels.styx.common.FreePorts.freePort
import com.hotels.styx.support.Support.requestContext
import com.hotels.styx.support.server.FakeHttpServer
import com.hotels.styx.support.server.UrlMatchingStrategies._
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpHeaders.Names._
import io.netty.handler.codec.http.HttpHeaders.Values._
import io.netty.handler.codec.http.LastHttpContent
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

class RetryHandlingSpec extends FunSuite with BeforeAndAfterAll with Matchers with OriginSupport {

  val LOGGER = LoggerFactory.getLogger(classOf[RetryHandlingSpec])

  val response = "Response From localhost"

  val server1 = new FakeHttpServer(0, "app", "HEALTHY_ORIGIN_ONE")
  val server2 = new FakeHttpServer(0, "app", "HEALTHY_ORIGIN_TWO")

  var healthyOriginOne: Origin = _
  var healthyOriginTwo: Origin = _

  val originServer1 = new FakeHttpServer(0, "app", "ORIGIN_ONE")
  val originServer2 = new FakeHttpServer(0, "app", "ORIGIN_TWO")
  val originServer3 = new FakeHttpServer(0, "app", "ORIGIN_THREE")
  val originServer4 = new FakeHttpServer(0, "app", "ORIGIN_FOUR")

  var originOne: Origin = _
  var originTwo: Origin = _
  var originThree: Origin = _
  var originFour: Origin = _

  val unhealthyOriginOne: Origin = newOriginBuilder("localhost", freePort).id("UNHEALTHY_ORIGIN_ONE").build
  val unhealthyOriginTwo: Origin = newOriginBuilder("localhost", freePort).id("UNHEALTHY_ORIGIN_TWO").build
  val unhealthyOriginThree: Origin = newOriginBuilder("localhost", freePort).id("UNHEALTHY_ORIGIN_THREE").build

  var servers: List[FakeHttpServer] = _

  override def beforeAll() = {
    server1.start()
    healthyOriginOne = originFrom(server1)
    server1.stub(urlStartingWith("/"), aResponse
      .withStatus(200)
      .withHeader(CONTENT_LENGTH.toString, response.getBytes(Charset.defaultCharset()).size.toString)
      .withHeader("Stub-Origin-Info", healthyOriginOne.applicationInfo())
      .withBody(response)
    )

    server2.start()
    healthyOriginTwo = originFrom(server2)
    server2.stub(urlStartingWith("/"), aResponse
      .withStatus(200)
      .withHeader(CONTENT_LENGTH.toString, response.getBytes(Charset.defaultCharset()).size.toString)
      .withHeader("Stub-Origin-Info", healthyOriginOne.applicationInfo())
      .withBody(response)
    )

    originServer1.start()
    originOne = originFrom(originServer1)

    originServer2.start()
    originTwo = originFrom(originServer2)

    originServer3.start()
    originThree = originFrom(originServer3)

    originServer4.start()
    originFour = originFrom(originServer4)

    originServer2.start()
    originServer3.start()
    originServer4.start()
  }

  override protected def afterAll() = {
    server1.stop()
    server2.stop()
    originServer1.stop()
    originServer2.stop()
    originServer3.stop()
    originServer4.stop()
  }

  private def activeOrigins(backendService: BackendService) = newOriginsInventoryBuilder(backendService).build()

  private def stickySessionStrategy(activeOrigins: ActiveOrigins) = new StickySessionLoadBalancingStrategy(
    activeOrigins,
    new RoundRobinStrategy(activeOrigins, activeOrigins.snapshot()))

  test("retries the next available origin on failure") {
    val backendService = new BackendService.Builder()
      .origins(unhealthyOriginOne, unhealthyOriginTwo, unhealthyOriginThree, healthyOriginTwo)
      .build()

    val client: StyxBackendServiceClient = newHttpClientBuilder(backendService.id)
      .retryPolicy(new RetryNTimes(3))
      .loadBalancer(stickySessionStrategy(activeOrigins(backendService)))
      .build

    val response = Mono.from(client.sendRequest(get("/version.txt").build, requestContext())).block()
    response.status() should be (OK)
  }

  test("propagates the last observed exception if all retries failed") {
    val backendService = new BackendService.Builder()
      .origins(unhealthyOriginOne, unhealthyOriginTwo, unhealthyOriginThree)
      .build()
    val client: StyxBackendServiceClient = newHttpClientBuilder(backendService.id)
      .loadBalancer(stickySessionStrategy(activeOrigins(backendService)))
      .retryPolicy(new RetryNTimes(2))
      .build
  }

  test("It should add sticky session id after a retry succeeded") {
    val StickySessionEnabled = new StickySessionConfig.Builder()
      .enabled(true)
      .build()

    val backendService = new BackendService.Builder()
      .origins(unhealthyOriginOne, unhealthyOriginTwo, unhealthyOriginThree, healthyOriginTwo)
      .stickySessionConfig(StickySessionEnabled)
      .build()

    val client: StyxBackendServiceClient = newHttpClientBuilder(backendService.id)
      .retryPolicy(new RetryNTimes(3))
      .loadBalancer(stickySessionStrategy(activeOrigins(backendService)))
      .build

    val request: LiveHttpRequest = get("/version.txt").build

    val response = Mono.from(client.sendRequest(request, requestContext())).block()

    val cookie = response.cookie("styx_origin_generic-app").get()

    cookie.value() should be("HEALTHY_ORIGIN_TWO")
    cookie.path().get() should be("/")
    cookie.httpOnly() should be(true)
    cookie.maxAge().isPresent should be(true)
  }

  private def respondWithHeadersOnly(): ResponseDefinitionBuilder = aResponse
      .withStatus(200)
      .withHeader(TRANSFER_ENCODING, CHUNKED)

  private def doNotRespond(retryCount: AtomicInteger): (ChannelHandlerContext, Any) => Unit = {
    (ctx: ChannelHandlerContext, msg: scala.Any) => {
      if (msg.isInstanceOf[LastHttpContent]) {
        LOGGER.warn("Origin received request, but not responding.")
        retryCount.incrementAndGet
      }
    }
  }

  private def doNotRespond(latch: CountDownLatch, responseCount: AtomicInteger): (ChannelHandlerContext, Any) => Unit = {
    (ctx: ChannelHandlerContext, msg: scala.Any) => {
      if (msg.isInstanceOf[LastHttpContent]) {
        LOGGER.warn("Origin received request, but not responding.")
        responseCount.incrementAndGet
        latch.countDown()
      }
    }
  }
}
