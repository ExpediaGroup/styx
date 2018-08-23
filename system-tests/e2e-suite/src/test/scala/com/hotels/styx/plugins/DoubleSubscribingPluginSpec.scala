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
package com.hotels.styx.plugins

import com.hotels.styx.MockServer.responseSupplier
import com.hotels.styx.api.HttpResponse
import com.hotels.styx.api.HttpResponse.Builder._
import com.hotels.styx.support.configuration.{HttpBackend, Origins, ProxyConfig, StyxConfig}
import com.hotels.styx.support.{ResourcePaths, TestClientSupport}
import com.hotels.styx.{MockServer, StyxProxySpec}
import io.netty.handler.codec.http.HttpMethod.GET
import com.hotels.styx.api.HttpResponseStatus._
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import io.netty.handler.codec.http.{DefaultFullHttpRequest, FullHttpResponse}
import com.hotels.styx.api.{FullHttpResponse => StyxFullHttpResponse }
import org.scalatest.FunSpec
import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._

class DoubleSubscribingPluginSpec extends FunSpec
  with StyxProxySpec
  with Eventually
  with TestClientSupport {

  val mockServer = new MockServer("origin-1", 0)

  override val styxConfig = StyxConfig(
    proxyConfig = ProxyConfig(requestTimeoutMillis = 2 * 1000),
    logbackXmlLocation = ResourcePaths.fixturesHome(this.getClass, "/conf/logback/logback-debug-stdout.xml"),
    plugins = List("aggregator" -> new ContentCutoffPlugin())
  )

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    mockServer.startAsync().awaitRunning()

    val function: () => HttpResponse = () => {
      StyxFullHttpResponse.response(OK).build().toStreamingResponse
    }

    mockServer.stub("/", responseSupplier(function))

    styxServer.setBackends(
      "/" -> HttpBackend("app1", Origins(mockServer.origin), responseTimeout = 1.seconds))
  }

  override protected def afterAll(): Unit = {
    mockServer.stopAsync().awaitTerminated()
    super.afterAll()
  }


  describe("Styx as a plugin container") {

    // TODO: Mikko: Styx 2.0 API: Test fails. Look into it.
    ignore("Tolerates plugins that break the content observable chain") {
      val testClient = aggregatingTestClient("localhost", styxServer.httpPort)

      withTestClient(testClient) {
        val request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/")
        request.headers().add("Host", s"localhost:${styxServer.httpPort}")

        testClient.write(request)

        val response = testClient.waitForResponse(3, SECONDS).asInstanceOf[FullHttpResponse]
        println("got response: " + response)

        // Note - In this scenario the Styx HttpResponseWriter manages to send the response headers (200 OK)
        // before the content observable fails with error. For this reason the content observable error cannot
        // be mapped to any other HTTP status code and the 200 OK will come out.
        response.status.code() should be(200)
        eventually(timeout(2.seconds)) {
          testClient.isOpen should be (false)
        }
      }
    }
  }

}
