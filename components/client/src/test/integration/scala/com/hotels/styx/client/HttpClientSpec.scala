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

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import com.google.common.base.Charsets._
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.extension.Origin._
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer
import com.hotels.styx.api.extension.{ActiveOrigins, Origin}
import com.hotels.styx.api.exceptions.ResponseTimeoutException
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.extension.service.BackendService
import com.hotels.styx.client.OriginsInventory.newOriginsInventoryBuilder
import com.hotels.styx.client.StyxHttpClient._
import com.hotels.styx.client.loadbalancing.strategies.BusyConnectionsStrategy
import com.hotels.styx.support.api.BlockingObservables.{waitForResponse, waitForStreamingResponse}
import com.hotels.styx.support.server.FakeHttpServer
import com.hotels.styx.support.server.UrlMatchingStrategies._
import io.netty.buffer.Unpooled._
import io.netty.channel.ChannelFutureListener.CLOSE
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpHeaders.Names._
import io.netty.handler.codec.http.HttpVersion._
import io.netty.handler.codec.http._
import org.scalatest._
import org.scalatest.mock.MockitoSugar
import rx.observers.TestSubscriber

class HttpClientSpec extends FunSuite with BeforeAndAfterAll with ShouldMatchers with BeforeAndAfter with Matchers with MockitoSugar {
  var webappOrigin: Origin = _

  val originOneServer = new FakeHttpServer(0)

  var client: StyxHttpClient = _

  val responseTimeout = 1000

  var testSubscriber: TestSubscriber[com.hotels.styx.api.HttpResponse] = _

  override protected def beforeAll(): Unit = {
    originOneServer.start()
    webappOrigin = newOriginBuilder("localhost", originOneServer.port()).applicationId("webapp").id("webapp-01").build()
  }

  override protected def afterAll(): Unit = {
    originOneServer.stop()
  }

  def activeOrigins(backendService: BackendService): ActiveOrigins = newOriginsInventoryBuilder(backendService).build()

  def busyConnectionStrategy(activeOrigins: ActiveOrigins): LoadBalancer = new BusyConnectionsStrategy(activeOrigins)

  before {
    originOneServer.reset()
    testSubscriber = new TestSubscriber[com.hotels.styx.api.HttpResponse]()

    val backendService = new BackendService.Builder()
      .origins(webappOrigin)
      .responseTimeoutMillis(responseTimeout)
      .build()

    client = newHttpClientBuilder(backendService)
      .loadBalancer(busyConnectionStrategy(activeOrigins(backendService)))
      .build
  }

  test("Emits an HTTP response even when content observable remains un-subscribed.") {
    originOneServer.stub(urlStartingWith("/"), response200OkWithContentLengthHeader("Test message body."))
    val response = waitForResponse(client.sendRequest(get("/foo/1").build()))
    assert(response.status() == OK, s"\nDid not get response with 200 OK status.\n$response\n")
  }


  test("Emits an HTTP response containing Content-Length from persistent connection that stays open.") {
    originOneServer.stub(urlStartingWith("/"), response200OkWithContentLengthHeader("Test message body."))

    val response = waitForResponse(client.sendRequest(get("/foo/2").build()))

    assert(response.status() == OK, s"\nDid not get response with 200 OK status.\n$response\n")
    assert(response.bodyAs(UTF_8) == "Test message body.", s"\nReceived wrong/unexpected response body.")
  }


  ignore("Determines response content length from server closing the connection.") {
    // originRespondingWith(response200OkFollowedFollowedByServerConnectionClose("Test message body."))

    val response = waitForResponse(client.sendRequest(get("/foo/3").build()))
    assert(response.status() == OK, s"\nDid not get response with 200 OK status.\n$response\n")

    assert(response.body().nonEmpty, s"\nResponse body is absent.")
    assert(response.bodyAs(UTF_8) == "Test message body.", s"\nIncorrect response body.")
  }

  test("Emits onError when origin responds too slowly") {
    originOneServer.stub(urlStartingWith("/"), aResponse
      .withStatus(OK.code())
      .withFixedDelay(3000))

    client.sendRequest(get("/foo/4").build()).subscribe(testSubscriber)
    val duration = time {
      testSubscriber.awaitTerminalEvent()
    }

    assert(testSubscriber.getOnErrorEvents.get(0).isInstanceOf[ResponseTimeoutException], "- Client emitted an incorrect exception!")
    println("responseTimeout: " + duration)
    duration shouldBe responseTimeout +- 250
  }

  def time[A](codeBlock: => A) = {
    val s = System.nanoTime
    codeBlock
    ((System.nanoTime - s) / 1e6).asInstanceOf[Int]
  }

  private def response200OkWithContentLengthHeader(content: String): ResponseDefinitionBuilder = {
    return aResponse
      .withStatus(OK.code())
      .withHeader(CONTENT_LENGTH, content.length.toString)
      .withBody(content)
  }

  def response200OkFollowedFollowedByServerConnectionClose(content: String): (ChannelHandlerContext, Any) => Any = {
    (ctx: ChannelHandlerContext, msg: scala.Any) => {
      if (msg.isInstanceOf[LastHttpContent]) {
        val response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.OK, copiedBuffer(content, UTF_8))
        ctx.writeAndFlush(response).addListener(CLOSE)
      }
    }
  }

  def doesNotRespond: (ChannelHandlerContext, Any) => Any = {
    (ctx: ChannelHandlerContext, msg: scala.Any) => {
      // Do noting
    }
  }

}
