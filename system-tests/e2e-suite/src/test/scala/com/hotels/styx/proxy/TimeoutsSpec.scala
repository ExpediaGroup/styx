/*
  Copyright (C) 2013-2019 Expedia Inc.

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

import java.nio.charset.StandardCharsets.UTF_8

import com.github.tomakehurst.wiremock.client.WireMock._
import com.hotels.styx.StyxProxySpec
import com.hotels.styx.support.ResourcePaths.fixturesHome
import com.hotels.styx.support.TestClientSupport
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration._
import io.netty.buffer.Unpooled.copiedBuffer
import io.netty.handler.codec.http.HttpHeaderNames.{CONTENT_LENGTH, CONTENT_TYPE, HOST}
import io.netty.handler.codec.http.HttpResponseStatus.{GATEWAY_TIMEOUT, REQUEST_TIMEOUT}
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import io.netty.handler.codec.http._
import org.scalatest.FunSpec
import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._

class TimeoutsSpec extends FunSpec
  with StyxProxySpec
  with TestClientSupport
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
        val testClient = aggregatingTestClient("localhost", styxServer.httpPort)

        val response = withTestClient(testClient) {

          val slowRequest = new DefaultHttpRequest(HTTP_1_1, HttpMethod.GET, "/backends")
          slowRequest.headers().add(HOST, styxServer.proxyHost)
          slowRequest.headers().add(CONTENT_TYPE, "text/html; charset=UTF-8")
          slowRequest.headers().add(CONTENT_LENGTH, "500")

          testClient.write(slowRequest)
          testClient.write(new DefaultHttpContent(copiedBuffer("xys", UTF_8)))

          val response = testClient.waitForResponse().asInstanceOf[FullHttpResponse]

          assert(response.status() == REQUEST_TIMEOUT)
        }
      }
    }

    describe("Response timeouts") {

      it("should return a 504 if a backend takes longer than the configured response timeout to start returning a response") {
        val testClient = aggregatingTestClient("localhost", styxServer.httpPort)

        withTestClient(testClient) {

          val slowRequest = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/slowResponseHeader")
          slowRequest.headers().add(HOST, styxServer.proxyHost)


          val responseTime = time {
            testClient.write(slowRequest)
            val response = testClient.waitForResponse().asInstanceOf[FullHttpResponse]
            assert(response.status() == GATEWAY_TIMEOUT)
          }
          assert(responseTime > responseTimeout)
        }
      }
    }

    def time[A](codeBlock: => A) = {
      val s = System.nanoTime
      codeBlock
      ((System.nanoTime - s) / 1e6).asInstanceOf[Int]
    }

  }
}
