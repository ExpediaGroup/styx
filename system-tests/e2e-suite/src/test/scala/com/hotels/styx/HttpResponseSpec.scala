/**
 * Copyright (C) 2013-2018 Expedia Inc.
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
package com.hotels.styx

import java.lang
import java.nio.charset.StandardCharsets.UTF_8

import com.hotels.styx.api.HttpRequest.Builder.get
import com.hotels.styx.api.client.{ActiveOrigins, RemoteHost}
import com.hotels.styx.api.messages.HttpResponseStatus._
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry
import com.hotels.styx.client.OriginsInventory.RemoteHostWrapper
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.client.StyxHttpClient._
import com.hotels.styx.client.connectionpool.ConnectionPools
import com.hotels.styx.client.loadbalancing.strategies.RoundRobinStrategy
import com.hotels.styx.client.stickysession.StickySessionLoadBalancingStrategy
import com.hotels.styx.support.NettyOrigins
import com.hotels.styx.support.api.BlockingObservables.waitForResponse
import com.hotels.styx.support.configuration.{BackendService, ImplicitOriginConversions, Origins}
import io.netty.buffer.Unpooled._
import io.netty.channel.ChannelFutureListener.CLOSE
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpVersion._
import io.netty.handler.codec.http._
import org.scalatest._
import rx.observers.TestSubscriber

import scala.collection.JavaConverters._
import scala.concurrent.duration._

class HttpResponseSpec extends FunSuite
  with ImplicitOriginConversions
  with BeforeAndAfterAll
  with ShouldMatchers
  with BeforeAndAfter
  with Matchers
  with NettyOrigins {

  val (originOne, originOneServer) = originAndCustomResponseWebServer("app", "h1")

  var client: StyxHttpClient = _

  val responseTimeout = 1000.millis

  var testSubscriber: TestSubscriber[com.hotels.styx.api.HttpResponse] = _

  override protected def afterAll(): Unit = {
    originOneServer.stopAsync().awaitTerminated()
  }

  before {
    testSubscriber = new TestSubscriber[com.hotels.styx.api.HttpResponse]()

    val backendService = BackendService(
      origins = Origins(originOne),
      responseTimeout = responseTimeout)

    client = newHttpClientBuilder(backendService.asJava)
      .loadBalancingStrategy(roundRobinStrategy(activeOrigins(backendService.asJava)))
      .build
  }

  def activeOrigins(backendService: com.hotels.styx.client.applications.BackendService): ActiveOrigins = {
    new ActiveOrigins {
      /**
        * Returns the list of the origins ready to accept traffic.
        *
        * @return a list of connection pools for each active origin
        */
      override def snapshot(): lang.Iterable[RemoteHost] = backendService.origins().asScala
        .map(origin => new RemoteHostWrapper(ConnectionPools.poolForOrigin(origin, new CodaHaleMetricRegistry, backendService.responseTimeoutMillis())).asInstanceOf[RemoteHost])
        .asJava
    }
  }

  def roundRobinStrategy(activeOrigins: ActiveOrigins): RoundRobinStrategy = new RoundRobinStrategy(activeOrigins)

  def stickySessionStrategy(activeOrigins: ActiveOrigins) = new StickySessionLoadBalancingStrategy(activeOrigins, roundRobinStrategy(activeOrigins))


  test("Determines response content length from server closing the connection.") {
    originRespondingWith(response200OkFollowedFollowedByServerConnectionClose("Test message body."))

    val response = waitForResponse(client.sendRequest(get("/foo/3").build()))

    assert(response.status() == OK, s"\nDid not get response with 200 OK status.\n$response\n")
    assert(response.bodyAs(UTF_8) == "Test message body.", s"\nIncorrect response body.")
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
