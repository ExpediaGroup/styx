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

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Optional

import com.hotels.styx._
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR
import com.hotels.styx.api._
import com.hotels.styx.support.JustATestException
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{ConnectionPoolSettings, HttpBackend, Origins, StyxConfig}
import org.scalatest.funspec.AnyFunSpec

import scala.concurrent.duration._

class PluginErrorHandlingSpec extends AnyFunSpec
  with StyxProxySpec
  with StyxClientSupplier {

  val normalBackend = FakeHttpServer.HttpStartupConfig().start()

  // TODO: See https://github.com/HotelsDotCom/styx/issues/202

  override val styxConfig = StyxConfig(plugins = Map(
    "failBeforeInterceptor" -> new FailBeforeHandleInterceptor(),
    "failAfterInterceptor" -> new FailAfterHandleInterceptor()
  ))

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    styxServer.setBackends(
      "/" -> HttpBackend(
        "appOne",
        Origins(normalBackend),
        responseTimeout = 5.seconds,
        ConnectionPoolSettings(maxConnectionsPerHost = 1))
    )
  }

  override protected def afterAll(): Unit = {
    normalBackend.stop()
    super.afterAll()
  }

  describe("Styx as a plugin container") {

    it("Catches exceptions from plugins handling requests, and maps them to INTERNAL_SERVER_ERRORs") {
      val request = get(styxServer.routerURL("/foo"))
        .header("Fail_before_handle", "true")
        .build()
      val resp = decodedRequest(request)
      assert(resp.status() == INTERNAL_SERVER_ERROR)
    }

    it("Catches exceptions from plugins handling responses, and maps them to INTERNAL_SERVER_ERRORs") {
      for (i <- 1 to 2) {
        val request = get(styxServer.routerURL("/foo"))
          .header("Fail_after_handle", "true")
          .body("foo", UTF_8)
          .build()
        val resp = decodedRequest(request)
        assert(resp.status() == INTERNAL_SERVER_ERROR)
      }
    }
  }

  private class FailBeforeHandleInterceptor extends PluginAdapter {
    override def intercept(request: LiveHttpRequest, chain: HttpInterceptor.Chain): Eventual[LiveHttpResponse] = {
      failIfHeaderPresent(request)
      chain.proceed(request)
    }
  }
  import scala.compat.java8.FunctionConverters.asJavaFunction

  private class FailAfterHandleInterceptor extends PluginAdapter {
    override def intercept(request: LiveHttpRequest, chain: HttpInterceptor.Chain): Eventual[LiveHttpResponse] = {
      chain.proceed(request).map(
        asJavaFunction((response: LiveHttpResponse) => {
          val fail: Optional[String] = request.header("Fail_after_handle")
          if (isTrue(fail)) {
            throw new JustATestException()
          }
          response
        }))
    }
  }

  private def failIfHeaderPresent(request: LiveHttpRequest) {
    val fail: Optional[String] = request.header("Fail_before_handle")
    if (isTrue(fail)) {
      throw new JustATestException()
    }
  }

  private def isTrue(fail: Optional[String]): Boolean = {
    valuePresent(fail, "true")
  }

  private def valuePresent[T](optional: Optional[T], value: T): Boolean = {
    optional.isPresent && (optional.get == value)
  }

}
