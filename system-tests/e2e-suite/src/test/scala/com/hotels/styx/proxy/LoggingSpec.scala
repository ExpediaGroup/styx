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

import java.nio.charset.StandardCharsets.UTF_8

import ch.qos.logback.classic.Level._
import com.github.tomakehurst.wiremock.client.WireMock.{get => _, _}
import com.hotels.styx.api.HttpInterceptor.Chain
import com.hotels.styx.api.FullHttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.plugins.spi.PluginException
import com.hotels.styx.api.{HttpRequest, HttpResponse, StyxObservable}
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origins, StyxConfig}
import com.hotels.styx.support.matchers.LoggingEventMatcher._
import com.hotels.styx.support.matchers.LoggingTestSupport
import com.hotels.styx.support.server.UrlMatchingStrategies._
import com.hotels.styx.{PluginAdapter, StyxClientSupplier, StyxProxySpec}
import io.netty.handler.codec.http.HttpHeaders.Names._
import io.netty.handler.codec.http.HttpHeaders.Values._
import org.hamcrest.MatcherAssert._
import org.hamcrest.Matchers._
import org.scalatest.FunSpec
import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._

class LoggingSpec extends FunSpec
  with StyxProxySpec
  with StyxClientSupplier
  with Eventually {

  val X_THROW_AT = "X-Throw-At"
  val AT_REQUEST = "Request"
  val AT_RESPONSE = "Response"

  val mockServer = FakeHttpServer.HttpStartupConfig()
    .start()
    .stub(urlStartingWith("/foobar"), aResponse
      .withStatus(200)
      .withHeader(TRANSFER_ENCODING, CHUNKED)
      .withBody("I should be here!")
    )

  override val styxConfig = StyxConfig(plugins = List("bad-plugin" -> new BadPlugin()))

  var logger: LoggingTestSupport = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    styxServer.setBackends(
      "/foobar" -> HttpBackend("appOne", Origins(mockServer), responseTimeout = 5.seconds)
    )

    val request = get(s"http://localhost:${mockServer.port()}/foobar").build()
    val resp = decodedRequest(request)
    resp.status() should be (OK)
    resp.bodyAs(UTF_8) should be ("I should be here!")
  }

  override protected def afterAll(): Unit = {
    mockServer.stop()
    super.afterAll()
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    logger = new LoggingTestSupport(classOf[HttpErrorStatusCauseLogger])
  }

  override protected def afterEach(): Unit = {
    logger.stop()
    super.afterEach()
  }

  describe("Styx request error logging") {
    it("Should include request and cause when plugin request processing fails") {
      val request = get(styxServer.routerURL("/foobar"))
        .addHeader(X_THROW_AT, AT_REQUEST)
        .build()

      val response = decodedRequest(request)

      assertThat(response.status(), is(INTERNAL_SERVER_ERROR))

      eventually(timeout(3.seconds)) {
        assertThat(logger.log(), hasItem(
          loggingEvent(
            ERROR,
            """Failure status="500 Internal Server Error" during request=HttpRequest.*""",
            classOf[PluginException],
            "bad-plugin: Throw exception at Request")))
      }
    }
  }


  describe("Styx response error logging") {
    it("Should include request and cause, when plugin response processing fails") {
      val request = get(styxServer.routerURL("/foobar"))
        .addHeader(X_THROW_AT, AT_RESPONSE)
        .build()

      val resp = decodedRequest(request)
      assertThat(resp.status(), is(INTERNAL_SERVER_ERROR))

      eventually(timeout(3.seconds)) {
        assertThat(logger.log(), hasItem(
          loggingEvent(
            ERROR,
            """Failure status="500 Internal Server Error" during request=HttpRequest.*""",
            classOf[PluginException],
            "bad-plugin: Throw exception at Response")))
      }
    }
  }

  import com.hotels.styx.support.ImplicitScalaRxConversions.toJavaObservable
  import rx.lang.scala.JavaConversions.toScalaObservable
  import scala.compat.java8.FunctionConverters.asJavaFunction


  class BadPlugin extends PluginAdapter {
    override def intercept(request: HttpRequest, chain: Chain): StyxObservable[HttpResponse] = {
      Option(request.header(X_THROW_AT).orElse(null)) match {
        case Some(AT_REQUEST) =>
          throw new RuntimeException("Throw exception at Request")
        case Some(AT_RESPONSE) =>
          chain.proceed(request)
            .map(asJavaFunction((response: HttpResponse) => throw new RuntimeException("Throw exception at Response")))
        case _ =>
          chain.proceed(request)
      }
    }
  }

}
