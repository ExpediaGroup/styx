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

import ch.qos.logback.classic.Level
import com.google.common.base.Charsets._
import com.hotels.styx.api.FullHttpRequest.get
import com.hotels.styx.api.{HttpResponse, extension}
import com.hotels.styx.api.extension.ActiveOrigins
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.client.OriginsInventory.newOriginsInventoryBuilder
import com.hotels.styx.client.loadbalancing.strategies.BusyConnectionsStrategy
import com.hotels.styx.client.stickysession.StickySessionLoadBalancingStrategy
import com.hotels.styx.server.netty.connectors.HttpPipelineHandler
import com.hotels.styx.support.NettyOrigins
import com.hotels.styx.support.configuration.{BackendService, HttpBackend, Origins}
import com.hotels.styx.support.matchers.LoggingTestSupport
import com.hotels.styx.support.observables.ImplicitRxConversions
import com.hotels.styx.{DefaultStyxConfiguration, StyxClientSupplier, StyxProxySpec}
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled._
import io.netty.channel.ChannelFutureListener.CLOSE
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpHeaders.Names._
import io.netty.handler.codec.http.HttpVersion._
import io.netty.handler.codec.http._
import org.scalatest._
import org.scalatest.concurrent.Eventually
import rx.observers.TestSubscriber
import com.hotels.styx.api.StyxInternalObservables.toRxObservable
import com.hotels.styx.api.exceptions.ResponseTimeoutException

import scala.compat.java8.StreamConverters._
import scala.concurrent.duration._

class OriginClosesConnectionSpec extends FunSuite
  with StyxProxySpec
  with DefaultStyxConfiguration
  with NettyOrigins
  with StyxClientSupplier
  with Eventually
  with ImplicitRxConversions {

  var loggingSupport: LoggingTestSupport = _
  val (originOne, originOneServer) = originAndCustomResponseWebServer("NettyOrigin")
  val originHost: String = s"${originOne.host().getHostText}:${originOne.host().getPort}"

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    loggingSupport = new LoggingTestSupport(classOf[HttpPipelineHandler])
    styxServer.setBackends(
      "/" -> HttpBackend("defaultApp", Origins(originOne), responseTimeout = 3.seconds)
    )
  }

  override protected def afterAll(): Unit = {
    originOneServer.stopAsync().awaitTerminated()
    loggingSupport.stop()
    super.afterAll()
  }

  test("Determines response content length from server closing the connection.") {
    originRespondingWith(response200OkFollowedFollowedByServerConnectionClose("Test message body." * 1024))

    for (i <- 1 to 10) {
      val response = decodedRequest(
        get("/foo/3")
          .addHeader(HOST, styxServer.proxyHost)
          .build()
      )

      assert(response.status() == OK, s"\nDid not get response with 200 OK status.\n$response\n")
      assert(response.body.nonEmpty, s"\nResponse body is absent.")
      assert(response.bodyAs(UTF_8) == "Test message body." * 1024, s"\nIncorrect response body.")
    }

    eventually {
      styxServer.metricsSnapshot.meter("requests.received").get.count should be(10)
    }

    val errorCount = loggingSupport.log().stream().toScala[Seq]
      .count(event => event.getLevel == Level.ERROR)

    errorCount should be(0)
  }

  def activeOrigins(backendService: extension.service.BackendService): ActiveOrigins = newOriginsInventoryBuilder(backendService).build()

  def busyConnectionStrategy(activeOrigins: ActiveOrigins): LoadBalancer = new BusyConnectionsStrategy(activeOrigins)

  def stickySessionStrategy(activeOrigins: ActiveOrigins) = new StickySessionLoadBalancingStrategy(activeOrigins, busyConnectionStrategy(activeOrigins))

  test("Emits ResponseTimeoutException when content subscriber stops requesting data") {
    val timeout = 2.seconds.toMillis.toInt
    originRespondingWith(response200OkFollowedFollowedByServerConnectionClose("Test message body." * 1024))

    val backendService = BackendService(
      origins = Origins(originOne),
      responseTimeout = timeout.milliseconds).asJava
    val styxClient = com.hotels.styx.client.StyxHttpClient.newHttpClientBuilder(
      backendService)
        .loadBalancer(busyConnectionStrategy(activeOrigins(backendService)))
      .build

    val responseSubscriber = new TestSubscriber[HttpResponse]()
    val contentSubscriber = new TestSubscriber[ByteBuf](1)

    val startTime = System.currentTimeMillis()
    val responseObservable = styxClient.sendRequest(
      get("/foo/3")
        .addHeader(HOST, originHost)
        .build()
        .toStreamingRequest)

    responseObservable
      .doOnNext((t: HttpResponse) => toRxObservable(t.body()).subscribe(contentSubscriber))
      .subscribe(responseSubscriber)

    responseSubscriber.awaitTerminalEvent()
    val duration = System.currentTimeMillis() - startTime

    responseSubscriber.getOnErrorEvents.get(0) shouldBe a[ResponseTimeoutException]
    contentSubscriber.getOnErrorEvents.get(0) shouldBe a[ResponseTimeoutException]
    duration shouldBe (timeout.toLong +- 220.millis.toMillis)
  }

  def response200OkFollowedFollowedByServerConnectionClose(content: String): (ChannelHandlerContext, Any) => Any = {
    (ctx: ChannelHandlerContext, msg: scala.Any) => {
      if (msg.isInstanceOf[LastHttpContent]) {
        val response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.OK, copiedBuffer(content, UTF_8))
        ctx.writeAndFlush(response).addListener(CLOSE)
      }
    }
  }
}
