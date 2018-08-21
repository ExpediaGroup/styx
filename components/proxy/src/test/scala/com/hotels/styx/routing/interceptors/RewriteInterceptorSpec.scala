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
package com.hotels.styx.routing.interceptors

import com.hotels.styx.api.HttpResponse.response
import com.hotels.styx.api.{HttpInterceptor, HttpRequest, HttpResponse, StyxObservable}
import com.hotels.styx.common.StyxFutures
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig
import com.hotels.styx.routing.config.RouteHandlerDefinition
import com.hotels.styx.api.HttpResponseStatus.OK
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSpec, ShouldMatchers}

class RewriteInterceptorSpec extends FunSpec with ShouldMatchers with MockitoSugar {

  it("performs replacement") {
    val config = configBlock(
      """
        |config:
        |    name: rewrite
        |    type: Rewrite
        |    config:
        |        - urlPattern:  /prefix/(.*)
        |          replacement: /app/$1
        |        - urlPattern:  /(.*)
        |          replacement: /app/$1
        |
        |""".stripMargin)

    val interceptor = new RewriteInterceptor.ConfigFactory().build(config)
    val capturingChain = new CapturingChain

    val response = StyxFutures.await(interceptor.intercept(HttpRequest.get("/foo").build(), capturingChain).asCompletableFuture())
    capturingChain.request().path() should be ("/app/foo")
  }

  it("Empty config block does nothing") {
    val config = configBlock(
      """
        |config:
        |    name: rewrite
        |    type: Rewrite
        |    config:
        |
        |""".stripMargin)

    val interceptor = new RewriteInterceptor.ConfigFactory().build(config)
    val capturingChain = new CapturingChain

    val response = StyxFutures.await(interceptor.intercept(HttpRequest.get("/foo").build(), capturingChain).asCompletableFuture())
    capturingChain.request().path() should be ("/foo")
  }



  private def configBlock(text: String) = new YamlConfig(text).get("config", classOf[RouteHandlerDefinition]).get()

  class CapturingChain extends HttpInterceptor.Chain {
    var storedRequest: HttpRequest = _

    override def proceed(request: HttpRequest): StyxObservable[HttpResponse] = {
      storedRequest = request
      StyxObservable.of(response(OK).build())
    }

    def request() = storedRequest
  }

}
