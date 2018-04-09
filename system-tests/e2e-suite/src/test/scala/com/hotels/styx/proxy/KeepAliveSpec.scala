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

import java.lang.Thread.sleep

import com.github.tomakehurst.wiremock.client.WireMock._
import com.google.common.base.Charsets.UTF_8
import com.hotels.styx.StyxProxySpec
import com.hotels.styx.support.TestClientSupport
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origins, ProxyConfig, StyxConfig}
import io.netty.channel.ChannelFuture
import io.netty.handler.codec.http.HttpHeaders.Names.{CONNECTION, HOST}
import io.netty.handler.codec.http.HttpHeaders.Values.CLOSE
import io.netty.handler.codec.http.HttpMethod.GET
import io.netty.handler.codec.http.HttpResponseStatus.OK
import io.netty.handler.codec.http.HttpVersion.{HTTP_1_0, HTTP_1_1}
import io.netty.handler.codec.http._
import org.scalatest.FunSpec
import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._

class KeepAliveSpec extends FunSpec
  with StyxProxySpec
  with TestClientSupport
  with Eventually {

  val recordingBackend = FakeHttpServer.HttpStartupConfig().start()
  val keepAliveTimeoutMillis = 3000

  override val styxConfig = StyxConfig(proxyConfig = ProxyConfig(keepAliveTimeoutMillis = keepAliveTimeoutMillis))

  override protected def beforeEach() = {
    recordingBackend
      .stub(urlPathEqualTo("/"), aResponse.withStatus(200))
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    styxServer.setBackends("/" -> HttpBackend("app-2", Origins(recordingBackend)))
  }

  override protected def afterAll(): Unit = {
    recordingBackend.stop()
    super.afterAll()
  }

  describe("Keep Alives") {

    it("should keep HTTP1/1 client connection open after serving the response.") {
      val client = aggregatingTestClient("localhost", styxServer.httpPort)
      val request = getRequest("/test/1", HTTP_1_1)

      withTestClient(client) {
        client.write(request)
        val response = client.waitForResponse().asInstanceOf[FullHttpResponse]

        assert(response.getStatus == OK, clueMessage("Must receive 200 OK, but received: ", response))

        // The connection should remain open for some time:
        sleep(100)

        assert(client.isOpen, "\nHttp 1/1 connection did not remain connected after receiving response.")
      }
    }

    it("should close the HTTP 1/1 client connection after serving the response when Connection=close header is present, ") {
      val client = aggregatingTestClient("localhost", styxServer.httpPort)
      val request = getRequest("/test/2", HTTP_1_1, HttpHeaderEntry(CONNECTION, CLOSE))

      withTestClient(client) {
        client.write(request)
        val response = client.waitForResponse().asInstanceOf[FullHttpResponse]

        assert(response.getStatus == OK, clueMessage("Must receive 200 OK, but received: ", response))
        eventually {
          assert(client.isOpen == false, "\nHttp 1/1 connection remained connected despite 'Connection: close' header.")
        }
      }
    }

    it("should close the connection for HTTP 1/0 clients.") {
      val client = aggregatingTestClient("localhost", styxServer.httpPort)
      val request = getRequest("/test/3", HTTP_1_0)

      withTestClient(client) {
        client.write(request)
        val response = client.waitForResponse().asInstanceOf[FullHttpResponse]

        assert(response.getStatus == OK, clueMessage("Must receive 200 OK, but received: ", response))
        eventually {
          assert(client.isOpen == false, "\nHttp 1/0 connection remained connected after receiving response.")
        }
      }
    }

    it("should close connections considered idle") {
      val client = aggregatingTestClient("localhost", styxServer.httpPort)
      val future: ChannelFuture = client.connect().channelFuture()

      val maxTimeout: Int = keepAliveTimeoutMillis * 5
      eventually(timeout(maxTimeout milliseconds)) {
        assert(future.channel().isActive == false, "channel should be closed")
      }
    }
  }

  case class HttpHeaderEntry(name: String, value: String)

  def clueMessage(message: String, response: FullHttpResponse) = {
    "\n" + message + s"\nReceived response: \n$response\n\n" + response.content().toString(UTF_8) + "\n\n"
  }

  def getRequest(uri: String, version: HttpVersion, headers: HttpHeaderEntry*) = {
    val request = new DefaultFullHttpRequest(version, GET, uri)
    request.headers().add(HOST, styxServer.proxyHost)

    for (header <- headers) {
      request.headers.add(header.name, header.value)
    }
    request
  }
}
