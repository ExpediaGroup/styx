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

import java.util.Optional

import com.hotels.styx._
import com.hotels.styx.api.HttpRequest.Builder.get
import com.hotels.styx.api._
import com.hotels.styx.api.messages.HttpResponseStatus.INTERNAL_SERVER_ERROR
import com.hotels.styx.support.ImplicitScalaRxConversions.toJavaObservable
import com.hotels.styx.support.configuration.{ConnectionPoolSettings, HttpBackend, Origins, StyxConfig}
import com.hotels.styx.support.backends.FakeHttpServer
import org.scalatest.FunSpec
import rx.lang.scala.JavaConversions.toScalaObservable

import scala.concurrent.duration._

class PluginErrorHandlingSpec extends FunSpec
  with StyxProxySpec
  with StyxClientSupplier {

  val normalBackend = FakeHttpServer.HttpStartupConfig().start()

  override val styxConfig = StyxConfig(plugins = List(
    "failBeforeInterceptor" -> new FailBeforeHandleInterceptor(),
    "failAfterInterceptor" -> new FailAfterHandleInterceptor(),
    "failAfterContentDecode" -> new DecodedContentFailurePlugin(10 * 1024),
    "failDuringContentDecoding" -> new ContentDecodeFailurePlugin(10 * 1024)
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
          .body("foo")
          .build()
        val resp = decodedRequest(request)
        assert(resp.status() == INTERNAL_SERVER_ERROR)
      }
    }

    it("Catches exceptions from plugins processing decoded content, and maps them to INTERNAL_SERVER_ERRORs") {
      for (i <- 1 to 2) {
        val request = get(styxServer.routerURL("/foo"))
          .header("Fail_after_content", "true")
          .build()
        val resp = decodedRequest(request)
        assert(resp.status() == INTERNAL_SERVER_ERROR)
      }
    }

    it("Catches exceptions from plugins performing content decoding, and maps them to INTERNAL_SERVER_ERRORs") {
      for (i <- 1 to 2) {
        val request = get(styxServer.routerURL("/foo"))
          .header("Fail_during_decoder", "true")
          .build()
        val resp = decodedRequest(request)
        assert(resp.status() == INTERNAL_SERVER_ERROR)
      }
    }

  }

  private class FailBeforeHandleInterceptor extends PluginAdapter {
    override def intercept(request: HttpRequest, chain: HttpInterceptor.Chain): rx.Observable[HttpResponse] = {
      failIfHeaderPresent(request)
      chain.proceed(request)
    }
  }

  private class FailAfterHandleInterceptor extends PluginAdapter {
    override def intercept(request: HttpRequest, chain: HttpInterceptor.Chain): rx.Observable[HttpResponse] = {
      toJavaObservable(toScalaObservable(chain.proceed(request)).map(
        (response: HttpResponse) => {
          val fail: Optional[String] = request.header("Fail_after_handle")
          if (isTrue(fail)) {
            throw new RuntimeException("something went wrong")
          }
          response
        }))
    }
  }

  private def failIfHeaderPresent(request: HttpRequest) {
    val fail: Optional[String] = request.header("Fail_before_handle")
    if (isTrue(fail)) {
      throw new RuntimeException("something went wrong")
    }
  }

  private def isTrue(fail: Optional[String]): Boolean = {
    valuePresent(fail, "true")
  }

  private def valuePresent[T](optional: Optional[T], value: T): Boolean = {
    optional.isPresent && (optional.get == value)
  }

}
