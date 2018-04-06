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
package com.hotels.styx.proxy

import com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH
import com.hotels.styx.support.configuration.{HttpBackend, Origins}
import com.hotels.styx.support.{NettyOrigins, TestClientSupport}
import com.hotels.styx.{DefaultStyxConfiguration, StyxProxySpec}
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpHeaders.Names.HOST
import io.netty.handler.codec.http.HttpMethod.GET
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import io.netty.handler.codec.http._
import org.scalatest.FunSpec
import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._

class BadClientSpec extends FunSpec
  with StyxProxySpec
  with DefaultStyxConfiguration
  with TestClientSupport
  with NettyOrigins
  with Eventually {

  val (originOne, originOneServer) = originAndCustomResponseWebServer("NettyOrigin")

  private lazy val testClient = aggregatingTestClient("localhost", styxServer.httpPort)

  override protected def beforeAll() = {
    super.beforeAll()
    println("Orign port is: [%d]".format(originOne.host.getPort))
    styxServer.setBackends("/badClientSpec/" -> HttpBackend("app-1", Origins(originOneServer)))
  }

  override protected def afterAll(): Unit = {
    originOneServer.stopAsync().awaitTerminated()
    testClient.disconnect()
    super.afterAll()
  }

  describe("Handling of bad client.") {

    describe("Client connection fails while styx is sending a response.") {

      it("Failed write of response does not trigger another 5xx response.") {
        originRespondingWith(responseHeadersAndClose)

        val request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/badClientSpec/1")
        request.headers().add(HOST, "localhost:12345")

        testClient.write(request)
        val responseObject = testClient.waitForResponse(1, SECONDS)
        testClient.disconnect()

        eventually(timeout(1.second)) {
          styxServer.metricsSnapshot.count("requests.response.status.5xx").get should be(0)
        }
      }
    }
  }

  def responseHeadersAndClose: (ChannelHandlerContext, Any) => Any = {
    (ctx: ChannelHandlerContext, msg: scala.Any) => {
      if (msg.isInstanceOf[LastHttpContent]) {
        val response = new DefaultHttpResponse(HTTP_1_1, OK)
        response.headers().set(CONTENT_LENGTH, "5")
        ctx.writeAndFlush(response)
      }
    }
  }
}
