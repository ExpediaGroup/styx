/*
  Copyright (C) 2013-2024 Expedia Inc.

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

import ch.qos.logback.classic.Level.ERROR
import com.hotels.styx.MockServer.responseSupplier
import com.hotels.styx.api.HttpResponseStatus._
import com.hotels.styx.api.{HttpResponse, LiveHttpResponse}
import com.hotels.styx.proxy.HttpErrorStatusCauseLogger
import com.hotels.styx.support.configuration.{HttpBackend, Origins, ProxyConfig, StyxConfig}
import com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent
import com.hotels.styx.support.matchers.LoggingTestSupport
import com.hotels.styx.support.{ResourcePaths, TestClientSupport}
import com.hotels.styx.{MockServer, StyxProxySpec}
import io.netty.handler.codec.http.HttpMethod.GET
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import io.netty.handler.codec.http.{DefaultFullHttpRequest, FullHttpResponse}
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasItem
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.concurrent.Eventually
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

class DoubleSubscribingPluginSpec extends AnyFunSpec
  with StyxProxySpec
  with Eventually
  with TestClientSupport {

  private val LOGGER = LoggerFactory.getLogger(classOf[DoubleSubscribingPluginSpec])
  var logger: LoggingTestSupport = _
  val mockServer = new MockServer("origin-1", 0)

  override val styxConfig = StyxConfig(
    proxyConfig = ProxyConfig(requestTimeoutMillis = 2 * 1000),
    logbackXmlLocation = ResourcePaths.fixturesHome(this.getClass, "/conf/logback/logback-debug-stdout.xml"),
    plugins = Map("aggregator" -> new ContentCutoffPlugin())
  )

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    mockServer.startAsync().awaitRunning()

    val function: () => LiveHttpResponse = () => {
      HttpResponse.response(OK).build().stream
    }

    mockServer.stub("/", responseSupplier(function))

    styxServer.setBackends(
      "/" -> HttpBackend("app1", Origins(mockServer.origin), responseTimeout = 1.seconds))
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    logger = new LoggingTestSupport(classOf[HttpErrorStatusCauseLogger])
  }

  override protected def afterEach(): Unit = {
    logger.stop()
    super.afterEach()
  }

  override protected def afterAll(): Unit = {
    mockServer.stopAsync().awaitTerminated()
    super.afterAll()
  }


  describe("Styx as a plugin container") {
    it("Tolerates plugins that break the content observable chain") {
      val testClient = aggregatingTestClient("localhost", styxServer.httpPort)

      withTestClient(testClient) {
        val request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/")
        request.headers().add("Host", s"localhost:${styxServer.httpPort}")

        testClient.write(request)

        val response = testClient.waitForResponse(3, SECONDS).asInstanceOf[FullHttpResponse]
        LOGGER.info("got response: " + response)

        // Note - In this scenario the Styx HttpResponseWriter manages to send the response headers (200 OK)
        // before the content observable fails with error. For this reason the content observable error cannot
        // be mapped to any other HTTP status code and the 200 OK will come out.
        response.status.code() should be(200)
        eventually(timeout(2.seconds)) {
          testClient.isOpen should be (false)
        }

        eventually(timeout(3.seconds)) {
//          styxServer.meterRegistry().counter(EXCEPTION, TYPE_TAG, "java_lang_IllegalStateException").count() should be(1.0)

          assertThat(logger.log(), hasItem(
            loggingEvent(
              ERROR,
              """Error writing response.*""",
              classOf[IllegalStateException],
              "Secondary subscription occurred.*")))
        }
      }
    }
  }

}
