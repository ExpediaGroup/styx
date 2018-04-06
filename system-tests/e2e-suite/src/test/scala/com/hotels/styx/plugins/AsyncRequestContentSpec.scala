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
import com.hotels.styx._
import com.hotels.styx.api.HttpInterceptor.Chain
import com.hotels.styx.api.HttpRequest.Builder.get
import com.hotels.styx.api.{HttpRequest, HttpResponse}
import com.hotels.styx.support.api.BlockingObservables.waitForResponse
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origins, StyxConfig}
import com.hotels.styx.support.server.UrlMatchingStrategies._
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.HttpHeaders.Names._
import io.netty.handler.codec.http.HttpHeaders.Values._
import org.scalatest.{BeforeAndAfterAll, FunSpec}

import scala.concurrent.duration._

class AsyncRequestContentSpec extends FunSpec
  with StyxProxySpec
  with BeforeAndAfterAll
  with StyxClientSupplier {

  val mockServer = FakeHttpServer.HttpStartupConfig()
    .start()
    .reset()
    .stub(urlStartingWith("/foobar"), aResponse
      .withStatus(200)
      .withHeader(TRANSFER_ENCODING, CHUNKED)
      .withBody("I should be here!")
    )

  override val styxConfig = StyxConfig(plugins = List("asyncDelayPlugin" -> new AsyncRequestContentDelayPlugin()))

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    styxServer.setBackends(
      "/foobar" -> HttpBackend("appOne", Origins(mockServer), responseTimeout = 5.seconds)
    )
  }

  override protected def afterAll(): Unit = {
    mockServer.stop()
    super.afterAll()
  }

  describe("Styx as a plugin container") {
    it("Proxies requests when plugin processes request content asynchronously on a separate thread pool") {
      val request = get(styxServer.routerURL("/foobar"))
        .addHeader("Content-Length", "0")
        .build()

      val response = waitForResponse(client.sendRequest(request))

      mockServer.verify(1, getRequestedFor(urlStartingWith("/foobar")))
      response.bodyAs(UTF_8) should be("I should be here!")
    }
  }
}

import com.hotels.styx.support.ImplicitScalaRxConversions.toJavaObservable
import rx.lang.scala.JavaConversions.toScalaObservable
import rx.lang.scala.Observable
import rx.lang.scala.schedulers._

class AsyncRequestContentDelayPlugin extends PluginAdapter {
  override def intercept(request: HttpRequest, chain: Chain): rx.Observable[HttpResponse] = {
    val contentTransformation: rx.Observable[ByteBuf] =
      request.body().content()
        .observeOn(ComputationScheduler())
        .flatMap(byteBuf => {
          Thread.sleep(1000)
          Observable.just(byteBuf)
        })

    Observable.just(request)
      .map(request => request.newBuilder().body(contentTransformation).build())
      .flatMap(request => chain.proceed(request))
  }
}
