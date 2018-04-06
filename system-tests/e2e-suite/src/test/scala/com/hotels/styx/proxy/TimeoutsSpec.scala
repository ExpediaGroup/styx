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

import java.util.concurrent.TimeUnit.MILLISECONDS

import com.github.tomakehurst.wiremock.client.WireMock._
import com.hotels.styx.api.HttpHeaderNames._
import com.hotels.styx.api.messages.FullHttpResponse
import com.hotels.styx.api.messages.HttpResponseStatus._
import com.hotels.styx.api.{HttpRequest, HttpResponse}
import com.hotels.styx.support.ResourcePaths.fixturesHome
import com.hotels.styx.support.TestClientSupport
import com.hotels.styx.support.api.BlockingObservables.waitForResponse
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origins, ProxyConfig, StyxConfig}
import com.hotels.styx.{StyxClientSupplier, StyxProxySpec}
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled.copiedBuffer
import io.netty.handler.codec.http.HttpMethod._
import org.scalatest.FunSpec
import org.scalatest.concurrent.Eventually
import rx.Observable

import scala.concurrent.duration._

class TimeoutsSpec extends FunSpec
  with StyxProxySpec
  with TestClientSupport
  with StyxClientSupplier
  with Eventually {

  val normalBackend = FakeHttpServer.HttpStartupConfig().start()

  val responseTimeout = 500
  val slowBackend = FakeHttpServer.HttpStartupConfig().start()
    .stub(urlPathEqualTo("/slowResponseHeader"), aResponse.withStatus(200).withFixedDelay(2 * responseTimeout))

  override val styxConfig = StyxConfig(
    ProxyConfig(requestTimeoutMillis = 100),
    logbackXmlLocation = fixturesHome(this.getClass, "/conf/logback/logback-debug-stdout.xml")
  )

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    styxServer.setBackends(
      "/backends" -> HttpBackend("normal", Origins(normalBackend)),
      "/slowResponseHeader" -> HttpBackend("slow", Origins(slowBackend), responseTimeout = responseTimeout.millis)
    )
  }

  override protected def afterAll(): Unit = {
    normalBackend.stop()
    slowBackend.stop()
    super.afterAll()
  }

  override protected def beforeEach() = {
    normalBackend.reset()
    normalBackend.stub(urlPathEqualTo("/headers"), aResponse.withStatus(200))
  }

  describe("Timeouts handling") {

    describe("Request timeouts") {
      it("should log and return 408 Request Timeout when client does not send a full HTTP request within configurable time.") {
        val delayedRequestBody: Observable[ByteBuf] = Observable.just(copiedBuffer("content".getBytes()))
          .delay(styxConfig.proxyConfig.requestTimeoutMillis, MILLISECONDS)

        val slowRequest = new HttpRequest.Builder(GET, "/backends")
          .header(HOST, styxServer.proxyHost)
          .header(CONTENT_TYPE, "text/html; charset=UTF-8")
          .header(CONTENT_LENGTH, "500")
          .body(delayedRequestBody)
          .build()

        val resp = decodedRequest(slowRequest, debug = true)

        assert(resp.status() == REQUEST_TIMEOUT)
      }
    }

    describe("Response timeouts") {

      it("should return a 504 if a backend takes longer than the configured response timeout to start returning a response") {
        val req = new HttpRequest.Builder(GET, "/slowResponseHeader")
          .addHeader(HOST, styxServer.proxyHost)
          .build()

        val transaction = client.sendRequest(req)
        val (resp, responseTime) = responseAndResponseTime(transaction)

        slowBackend.verify(getRequestedFor(urlPathEqualTo("/slowResponseHeader")))

        assert(resp.status() == GATEWAY_TIMEOUT)
        assert(responseTime > responseTimeout)
      }

      ignore("should still return the response if the body takes longer than the header timeout") {
        val req = new HttpRequest.Builder(GET, "/slowResponseBody")
          .addHeader(HOST, styxServer.proxyHost)
          .build()

        val resp = decodedRequest(req)

        slowBackend.verify(getRequestedFor(urlPathEqualTo("/slowResponseBody")))

        assert(resp.status() == OK)
      }
    }
  }

  def responseAndResponseTime(transaction: Observable[HttpResponse]): (FullHttpResponse, Long) = {
    var response = FullHttpResponse.response().build()

    val duration = time {
      response = waitForResponse(transaction)
    }

    (response, duration)
  }


  def time[A](codeBlock: => A) = {
    val s = System.nanoTime
    codeBlock
    ((System.nanoTime - s) / 1e6).asInstanceOf[Int]
  }

}
