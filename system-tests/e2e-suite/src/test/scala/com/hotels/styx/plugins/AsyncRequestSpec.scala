/*
  Copyright (C) 2013-2020 Expedia Inc.

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
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.{Eventual, LiveHttpRequest, LiveHttpResponse}
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origins, StyxConfig}
import com.hotels.styx.support.server.UrlMatchingStrategies._
import io.netty.handler.codec.http.HttpHeaders.Names._
import io.netty.handler.codec.http.HttpHeaders.Values._
import org.scalatest.FunSpec

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.compat.java8.FutureConverters.CompletionStageOps

class AsyncRequestSpec extends FunSpec
  with StyxProxySpec
  with StyxClientSupplier {
  val mockServer = FakeHttpServer.HttpStartupConfig().start()

  override val styxConfig = StyxConfig(plugins = Map("asyncDelayPlugin" -> new AsyncRequestDelayPlugin()))

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
    it("Proxies requests when plugin processes request headers asynchronously on a separate thread pool") {
      mockServer.stub(urlStartingWith("/foobar"), aResponse
        .withStatus(200)
        .withHeader(TRANSFER_ENCODING, CHUNKED)
        .withBody("I should be here!")
      )

      val request = get(styxServer.routerURL("/foobar"))
        .addHeader("Content-Length", "0")
        .build()

      val response = Await.result(client.sendRequest(request).toScala, 5.seconds)

      mockServer.verify(1, getRequestedFor(urlStartingWith("/foobar")))
      response.bodyAs(UTF_8) should be("I should be here!")
    }
  }
}

import scala.compat.java8.FutureConverters.FutureOps
import scala.compat.java8.FunctionConverters.asJavaFunction
import scala.concurrent.ExecutionContext.Implicits.global

class AsyncRequestDelayPlugin extends PluginAdapter {
  override def intercept(request: LiveHttpRequest, chain: Chain): Eventual[LiveHttpResponse] = {
    def asyncRequest(request: LiveHttpRequest): Eventual[LiveHttpRequest] = {
      Eventual.from(
        Future {
          Thread.sleep(1000)
        }.map(_ => request)
          .toJava
      )
    }

    Eventual.of(request)
      .flatMap(asJavaFunction(x => asyncRequest(request)))
      .flatMap(asJavaFunction(y => chain.proceed(y)))
  }

}
