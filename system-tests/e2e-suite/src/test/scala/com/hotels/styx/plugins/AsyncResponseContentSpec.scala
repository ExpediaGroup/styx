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

import java.nio.charset.StandardCharsets.UTF_8

import com.github.tomakehurst.wiremock.client.WireMock._
import com.hotels.styx.api.HttpInterceptor.Chain
import com.hotels.styx.api.FullHttpRequest.get
import com.hotels.styx.api._
import com.hotels.styx.support._
import com.hotels.styx.support.api.BlockingObservables.waitForResponse
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origins, StyxConfig}
import com.hotels.styx.support.server.UrlMatchingStrategies._
import com.hotels.styx.{PluginAdapter, StyxClientSupplier, StyxProxySpec}
import _root_.io.netty.buffer.ByteBuf
import _root_.io.netty.handler.codec.http.HttpHeaders.Names._
import _root_.io.netty.handler.codec.http.HttpHeaders.Values._
import com.hotels.styx.api.StyxInternalObservables.{fromRxObservable, toRxObservable}
import org.scalatest.FunSpec
import rx.Observable
import rx.schedulers.Schedulers

import scala.concurrent.duration._


class AsyncResponseContentSpec extends FunSpec
  with StyxProxySpec
  with StyxClientSupplier
  with NettyOrigins {
  val mockServer = FakeHttpServer.HttpStartupConfig().start()

  override val styxConfig = StyxConfig(plugins = List("asyncDelayPlugin" -> new AsyncDelayPlugin()))

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    mockServer.start()

    styxServer.setBackends(
      "/foobar" -> HttpBackend("appOne", Origins(mockServer), responseTimeout = 5.seconds)
    )
  }

  override protected def afterAll(): Unit = {
    mockServer.stop()
    super.afterAll()
  }

  describe("Styx as a plugin container") {
    it("Proxies requests when plugin processes response content asynchronously on a separate thread pool") {
      mockServer.stub(urlStartingWith("/foobar"), aResponse
        .withStatus(200)
        .withHeader(TRANSFER_ENCODING, CHUNKED)
        .withBody("I should be here!")
      )

      val request = get(styxServer.routerURL("/foobar"))
        .addHeader("Content-Length", "0")
        .build()

      val response = decodedRequest(request)

      mockServer.verify(1, getRequestedFor(urlStartingWith("/foobar")))
      response.bodyAs(UTF_8) should be("I should be here!")
    }
  }
}

import rx.lang.scala.ImplicitFunctionConversions._
import scala.compat.java8.FunctionConverters.asJavaFunction

class AsyncDelayPlugin extends PluginAdapter {
  override def intercept(request: HttpRequest, chain: Chain): StyxObservable[HttpResponse] = {
    chain.proceed(request)
      .flatMap(asJavaFunction((response: HttpResponse) => {

        val transformedContent: Observable[ByteBuf] = toRxObservable(response.body())
          .observeOn(Schedulers.computation())
          .flatMap((byteBuf: ByteBuf) => {
            Thread.sleep(1000)
            Observable.just(byteBuf)
          })

        StyxObservable.of(response.newBuilder().body(fromRxObservable(transformedContent)).build())
      }))
  }
}
