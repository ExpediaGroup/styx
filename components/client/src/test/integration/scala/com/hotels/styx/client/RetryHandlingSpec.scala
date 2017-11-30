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
package com.hotels.styx.client

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.{MILLISECONDS, SECONDS}
import java.util.concurrent.atomic.AtomicInteger

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import com.hotels.styx.support.api.BlockingObservables.waitForResponse
import com.hotels.styx.api.HttpRequest.Builder.get
import StyxHeaderConfig.ORIGIN_ID_DEFAULT
import com.hotels.styx.api.client.Origin
import com.hotels.styx.api.client.Origin._
import com.hotels.styx.api.support.HostAndPorts.localHostAndFreePort
import com.hotels.styx.api.{HttpRequest, HttpResponse}
import com.hotels.styx.client.StyxHttpClient.newHttpClientBuilder
import com.hotels.styx.client.applications.BackendService
import com.hotels.styx.client.connectionpool.ConnectionPoolSettings
import com.hotels.styx.client.retry.RetryNTimes
import com.hotels.styx.client.stickysession.StickySessionConfig
import com.hotels.styx.support.server.FakeHttpServer
import com.hotels.styx.support.server.UrlMatchingStrategies._
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpHeaders.Names._
import io.netty.handler.codec.http.HttpHeaders.Values._
import io.netty.handler.codec.http.LastHttpContent
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import rx.observers.TestSubscriber

import scala.util.Try

class RetryHandlingSpec extends FunSuite with BeforeAndAfterAll with Matchers with OriginSupport {

  val (healthyOriginOne, server1) = originAndServer("app", "HEALTHY_ORIGIN_ONE")
  val (healthyOriginTwo, server2) = originAndServer("app", "HEALTHY_ORIGIN_TWO")

  val originOne = newOriginBuilder(localHostAndFreePort).id("ORIGIN_ONE").build
  val originTwo = newOriginBuilder(localHostAndFreePort).id("ORIGIN_TWO").build
  val originThree = newOriginBuilder(localHostAndFreePort).id("ORIGIN_THREE").build
  val originFour = newOriginBuilder(localHostAndFreePort).id("ORIGIN_FOUR").build

  val originServer1 = new FakeHttpServer(originOne.host().getPort)
  val originServer2 = new FakeHttpServer(originTwo.host().getPort)
  val originServer3 = new FakeHttpServer(originThree.host().getPort)
  val originServer4 = new FakeHttpServer(originFour.host().getPort)


  val unhealthyOriginOne: Origin = newOriginBuilder(localHostAndFreePort).id("UNHEALTHY_ORIGIN_ONE").build
  val unhealthyOriginTwo: Origin = newOriginBuilder(localHostAndFreePort).id("UNHEALTHY_ORIGIN_TWO").build
  val unhealthyOriginThree: Origin = newOriginBuilder(localHostAndFreePort).id("UNHEALTHY_ORIGIN_THREE").build

  var servers: List[FakeHttpServer] = _

  override def beforeAll() = {
    server1.start()
    server2.start()

    originServer1.start()
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

  // Ignored tests do not assert anything. They were previously checking for the absence of a header that is never added..
  ignore("does not retry on success") {
    val client: StyxHttpClient = newHttpClientBuilder(
      new BackendService.Builder()
        .origins(healthyOriginOne, healthyOriginTwo)
        .build())
      .build

    val request: HttpRequest = get("/version.txt").build

    val response = waitForResponse(client.sendRequest(request))
  }

  test("retries the next available origin on failure") {
    val client: StyxHttpClient = newHttpClientBuilder(new BackendService.Builder()
      .origins(unhealthyOriginOne, unhealthyOriginTwo, unhealthyOriginThree, healthyOriginTwo)
      .build())
      .retryPolicy(new RetryNTimes(3))
      .build

    val request: HttpRequest = get("/version.txt").build

    val response = waitForResponse(client.sendRequest(request))

    assertThat(response.header(ORIGIN_ID_DEFAULT).get(), containsString("HEALTHY_ORIGIN_TWO"))
  }

  test("propagates the last observed exception if all retries failed") {
    val client: StyxHttpClient = newHttpClientBuilder(
      new BackendService.Builder()
        .origins(unhealthyOriginOne, unhealthyOriginTwo, unhealthyOriginThree)
        .build())
      .retryPolicy(new RetryNTimes(2))
      .build

    val request: HttpRequest = get("/version.txt").build
    val subscriber = new TestSubscriber[HttpResponse]
    client.sendRequest(request).subscribe(subscriber)
    subscriber.awaitTerminalEvent

    subscriber.getOnErrorEvents should not be empty
  }

  ignore("retries once if successful before retries runs out") {
    val client: StyxHttpClient = newHttpClientBuilder(
      new BackendService.Builder()
        .origins(healthyOriginOne, healthyOriginTwo, unhealthyOriginOne)
        .build())
      .retryPolicy(new RetryNTimes(1))
      .build
    val request: HttpRequest = get("/version.txt").build
    val response = waitForResponse(client.sendRequest(request))
  }

  test("It should add sticky session id after a retry succeeded") {
    val StickySessionEnabled = new StickySessionConfig.Builder()
      .enabled(true)
      .build()

    val client: StyxHttpClient = newHttpClientBuilder(new BackendService.Builder()
      .origins(unhealthyOriginOne, unhealthyOriginTwo, unhealthyOriginThree, healthyOriginTwo)
      .stickySessionConfig(StickySessionEnabled)
      .build())
      .retryPolicy(new RetryNTimes(3))
      .build

    val request: HttpRequest = get("/version.txt").build

    val response = waitForResponse(client.sendRequest(request))

    response.cookie("styx_origin_generic-app").get().toString should fullyMatch regex "styx_origin_generic-app=HEALTHY_ORIGIN_TWO; Max-Age=.*; Path=/; HttpOnly"
  }

  // Instead of origins, use an injected connection pool as the means to track & control the styx HTTP client retries.
  ignore("retries at most N times") {
    val originRequestCount = new AtomicInteger()
    //    server1Handler.setBehaviour(doNotRespond(originRequestCount))
    //    server2Handler.setBehaviour(doNotRespond(originRequestCount))
    //    server3Handler.setBehaviour(doNotRespond(originRequestCount))

    val client: StyxHttpClient = newHttpClientBuilder(new BackendService.Builder()
      .origins(originOne, originTwo, originThree)
      .connectionPoolConfig(new ConnectionPoolSettings.Builder().socketTimeout(50, MILLISECONDS).build)
      .build())
      .retryPolicy(new RetryNTimes(1))
      .build

    val request: HttpRequest = get("/version.txt").build
    val result = Try {
      client.sendRequest(request).toBlocking.first()
    }

    result.isFailure should be(true)
    originRequestCount.get() should be(2)
  }

  ignore("does not retry when failure occurs in content.") {
    originServer1.stub(urlStartingWith("/"), respondWithHeadersOnly())
    originServer2.stub(urlStartingWith("/"), respondWithHeadersOnly())
    originServer3.stub(urlStartingWith("/"), respondWithHeadersOnly())

    val client: StyxHttpClient = newHttpClientBuilder(new BackendService.Builder()
      .origins(originOne, originTwo, originThree)
      .connectionPoolConfig(new ConnectionPoolSettings.Builder().socketTimeout(2, SECONDS).build)
      .build()
    )
      .retryPolicy(new RetryNTimes(2))
      .build

    val request = get("/version.txt").build
    val response = waitForResponse(client.sendRequest(request))
  }

  // Instead of origins, use an injected connection pool as the means to track & control the styx HTTP client retries.
  ignore("cancels transaction after first retry.") {
    //    val (twoOriginsSeenRequest, originRequestCount) = useNonResponsiveNettyOrigins()
    //
    //    val client: StyxHttpClient = newHttpClientBuilder
    //      .origins(originOne, originTwo, originThree, originFour)
    //      .connectionPoolSettings(SettableConnectionPoolSettings.newBuilder.socketTimeout(100, MILLISECONDS).build)
    //      .retryPolicy(new RetryNTimes(3))
    //      .build
    //
    //    val request: HttpRequest = get("/version.txt").build
    //    val transaction: HttpTransaction = client.newTransaction(request)
    //    val subscriber = new TestSubscriber[HttpResponse]()
    //    transaction.response().subscribe(subscriber)
    //
    //    twoOriginsSeenRequest.await()
    //    transaction.cancel()
    //
    //    subscriber.awaitTerminalEvent(2, SECONDS)
    //
    //    originRequestCount.get() should be <= 3
  }

  private def respondWithHeadersOnly(): ResponseDefinitionBuilder = {
    return aResponse
      .withStatus(200)
      .withHeader(TRANSFER_ENCODING, CHUNKED)
  }

  private def doNotRespond(retryCount: AtomicInteger): (ChannelHandlerContext, Any) => Unit = {
    (ctx: ChannelHandlerContext, msg: scala.Any) => {
      if (msg.isInstanceOf[LastHttpContent]) {
        println("Origin received request, but not responding.")
        retryCount.incrementAndGet
      }
    }
  }

  private def doNotRespond(latch: CountDownLatch, responseCount: AtomicInteger): (ChannelHandlerContext, Any) => Unit = {
    (ctx: ChannelHandlerContext, msg: scala.Any) => {
      if (msg.isInstanceOf[LastHttpContent]) {
        println("Origin received request, but not responding.")
        responseCount.incrementAndGet
        latch.countDown()
      }
    }
  }
}
