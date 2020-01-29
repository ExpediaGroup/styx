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
package com.hotels.styx.proxy

import java.util.Optional

import com.google.common.base.Charsets.UTF_8
import com.hotels.styx.api.HttpHeaderNames.CONNECTION
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY
import com.hotels.styx.client.StyxHeaderConfig.STYX_INFO_DEFAULT
import com.hotels.styx.support.configuration.{ConnectionPoolSettings, HttpBackend, Origins}
import com.hotels.styx.support.matchers.IsOptional.matches
import com.hotels.styx.support.matchers.RegExMatcher.matchesRegex
import com.hotels.styx.support.{NettyOrigins, TestClientSupport}
import com.hotels.styx.{DefaultStyxConfiguration, StyxProxySpec}
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpHeaders.Names.{CONTENT_LENGTH, HOST, TRANSFER_ENCODING}
import io.netty.handler.codec.http.HttpHeaders.Values.CHUNKED
import io.netty.handler.codec.http.HttpMethod._
import io.netty.handler.codec.http.HttpResponseStatus.OK
import io.netty.handler.codec.http.HttpVersion._
import io.netty.handler.codec.http.LastHttpContent._
import io.netty.handler.codec.http._
import org.hamcrest.MatcherAssert.assertThat
import org.scalatest.FunSpec
import org.scalatest.concurrent.Eventually
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.util.Random

class BadResponseFromOriginSpec extends FunSpec
  with StyxProxySpec
  with DefaultStyxConfiguration
  with NettyOrigins
  with TestClientSupport
  with Eventually {

  private val LOGGER = LoggerFactory.getLogger(classOf[BadResponseFromOriginSpec])
  val (originOne, originOneServer) = originAndCustomResponseWebServer("NettyOrigin")

  override protected def beforeAll() = {
    super.beforeAll()
    styxServer.setBackends(
      "/badResponseFromOriginSpec/" -> HttpBackend(
        "app-1", Origins(originOne),
        connectionPoolConfig = ConnectionPoolSettings(maxConnectionsPerHost = 1)
      )
    )
  }

  override protected def afterAll(): Unit = {
    originOneServer.stopAsync().awaitTerminated()
    // This test is failing intermittently. Print the metrics snapshot in case it fails,
    // to offer insight into what is going wrong:
    LOGGER.info("Styx metrics after BadResponseFromOriginSpec: " + styxServer.metricsSnapshot)
    super.afterAll()
  }

  describe("Handling of bad responses from origins.") {

    ignore("Keeps serving requests after an origin responds before client has transmitted the previous request in full.") {
      originRespondingWith(response200OkWithoutWaitingForFullRequest("This is a response body."))

      val client = aggregatingTestClient("localhost", styxServer.httpPort)
      client.write(requestHeadersWithSomeContent("/badResponseFromOriginSpec/1"))
      client.write(new DefaultHttpContent(Unpooled.copiedBuffer("foo=bar", UTF_8)))

      val response = client.waitForResponse(5, SECONDS).asInstanceOf[FullHttpResponse]
      response.getStatus.code() should be(200)

      val client2 = aggregatingTestClient("localhost", styxServer.httpPort)
      val response2 = transactionWithTestClient[FullHttpResponse](client2) {
        client2.write(fullHttpRequest("/badResponseFromOriginSpec/2"))
        client2.waitForResponse(5, SECONDS)
      }
      response2.get.getStatus.code() should be(200)
    }

    it("Responds with 502 Bad Gateway when Styx is unable to read response from origin (maxHeaderSize exceeded).") {
      originRespondingWith(
        responseWithHeaders(
          HttpHeader("Accept-Language", Random.alphanumeric.take(9000).mkString), // This will make the call fall with header too long
        ))


      val request = get(styxServer.routerURL("/badResponseFromOriginSpec/3")).build()
      val response = decodedRequest(request)
      response.status() should be(BAD_GATEWAY)
      assertThat(response.headers().get(STYX_INFO_DEFAULT), matches(matchesRegex("noJvmRouteSet;[0-9a-f-]+")))
      response.bodyAs(UTF_8) should be("Site temporarily unavailable.")
      response.header(CONNECTION) should be(Optional.of("close"))

      eventually(timeout(7.seconds)) {
        styxServer.metricsSnapshot.count("styx.response.status.502").get should be(1)
      }
    }
  }

  def response200OkWithoutWaitingForFullRequest(messageBody: String): (ChannelHandlerContext, Any) => Unit = {
    (ctx: ChannelHandlerContext, msg: scala.Any) => {
      LOGGER.info("origin received: " + msg)
      if (msg.isInstanceOf[io.netty.handler.codec.http.HttpRequest]) {
        LOGGER.info("response 200 sent")
        val response = new DefaultHttpResponse(HTTP_1_1, OK)
        response.headers().set(TRANSFER_ENCODING, CHUNKED)
        ctx.writeAndFlush(response)
        sendContentInChunks(ctx, messageBody, 100 millis)
      }
    }
  }

  def sendContentInChunks(ctx: ChannelHandlerContext, data: String, delay: Duration): Unit = {
    val chunkData = data.take(100)
    if (chunkData.length == 0) {
      ctx.writeAndFlush(EMPTY_LAST_CONTENT)
    } else {
      ctx.writeAndFlush(new DefaultHttpContent(Unpooled.copiedBuffer(chunkData, UTF_8)))
      sendContentInChunks(ctx, data.drop(100), delay)
    }
  }

  def requestHeadersWithSomeContent(urlPath: String) = {
    val request = new DefaultFullHttpRequest(HTTP_1_1, GET, urlPath)
    request.headers().add(HOST, styxServer.proxyHost)
    request.headers().add(CONTENT_LENGTH, 1000)
    request
  }

  def fullHttpRequest(urlPath: String) = {
    val request = new DefaultFullHttpRequest(HTTP_1_1, GET, urlPath)
    request.headers().add(HOST, styxServer.proxyHost)
    request.headers().add(CONTENT_LENGTH, "0")
    request
  }

}
