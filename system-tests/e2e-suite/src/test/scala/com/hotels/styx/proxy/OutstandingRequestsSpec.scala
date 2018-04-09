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

import java.util.concurrent.TimeUnit

import com.hotels.styx.StyxProxySpec
import com.hotels.styx.support._
import com.hotels.styx.support.configuration.{HttpBackend, Origins, ProxyConfig, StyxConfig}
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpHeaders.Names._
import io.netty.handler.codec.http.HttpMethod.GET
import io.netty.handler.codec.http.HttpResponseStatus.OK
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import io.netty.handler.codec.http._
import org.scalatest.FunSpec
import org.scalatest.concurrent.Eventually
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

class OutstandingRequestsSpec extends FunSpec
  with StyxProxySpec
  with NettyOrigins
  with TestClientSupport
  with Eventually {

  val LOGGER = LoggerFactory.getLogger(classOf[OutstandingRequestsSpec])
  val requestTimeoutMillis = 3000

  val server1Handler = new CustomResponseHandler()
  val (originOne, originOneServer) = originAndCustomResponseWebServer("appOne", "NettyOrigin", server1Handler)

  override val styxConfig = StyxConfig(proxyConfig = ProxyConfig(requestTimeoutMillis = requestTimeoutMillis))

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    styxServer.setBackends(
      "/outstandingRequestsSpec/" -> HttpBackend(
        "app-1",
        Origins(originOne),
        responseTimeout = 3.seconds
      ))
  }

  override protected def afterAll(): Unit = {
    originOneServer.stopAsync().awaitTerminated()
    super.afterAll()
  }


  describe("Styx handling of outstanding request count") {

    it("Decrements outstanding request count after successful HTTP transaction.") {
      server1Handler.setBehaviour(status200OkResponse)

      val client = aggregatingTestClient("localhost", styxServer.httpPort)
      withTestClient(client) {
        client.write(vanillaHttpGet("/outstandingRequestsSpec/1"))
        val response = client.waitForResponse(3, TimeUnit.SECONDS).asInstanceOf[FullHttpResponse]

        assert(response.getStatus == OK)

        eventually(timeout(1.seconds)) {
          styxServer.metricsSnapshot.count("requests.outstanding").get should be(0)
        }
      }
    }


    it("Decrements outstanding request when transaction is cancelled due to proxy request timeout") {
      server1Handler.setBehaviour(status200OkResponse)

      val request = new DefaultHttpRequest(HTTP_1_1, GET, "/outstandingRequestsSpec/2")
      request.headers().add(CONTENT_LENGTH, 50)
      request.headers().add(HOST, "localhost")

      val client = aggregatingTestClient("localhost", styxServer.httpPort)
      client.write(request)

      eventually(timeout(1 seconds)) {
        styxServer.metricsSnapshot.count("requests.outstanding").get should be(1)
      }

      eventually(timeout((requestTimeoutMillis + 500) millis)) {
        styxServer.metricsSnapshot.count("requests.outstanding").get should be(0)
      }
    }


    it("Decrements outstanding request count after client disconnects while ongoing HTTP transaction - origin has transmitted headers prior to disconnect") {
      server1Handler.setBehaviour(respondWithNeverEndingResponse)

      val client = connectedTestClient("localhost", styxServer.httpPort)
      client.write(vanillaHttpGet("/outstandingRequestsSpec/3"))

      val response = client.waitForResponse(1, TimeUnit.SECONDS).asInstanceOf[HttpResponse]
      response.status.code() should be(200)

      eventually(timeout(1 seconds)) {
        styxServer.metricsSnapshot.count("requests.outstanding").get should be(1)
      }

      client.disconnect()

      eventually(timeout(2 seconds)) {
        styxServer.metricsSnapshot.count("requests.outstanding").get should be(0)
      }
    }
  }

  def vanillaHttpGet(path: String) = {
    val request = new DefaultHttpRequest(HTTP_1_1, GET, path)
    request.headers().add(HOST, "localhost")
    request
  }

  def respondWithNeverEndingResponse: (ChannelHandlerContext, Any) => Unit = {
    (ctx: ChannelHandlerContext, msg: scala.Any) => {
      if (msg.isInstanceOf[LastHttpContent]) {
        val response = new DefaultHttpResponse(HTTP_1_1, OK)
        response.headers().set(CONTENT_LENGTH, 50)
        ctx.writeAndFlush(response)
      }
    }
  }

}
