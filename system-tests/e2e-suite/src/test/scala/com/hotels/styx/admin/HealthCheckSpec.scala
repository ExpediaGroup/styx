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
package com.hotels.styx.admin

import java.nio.charset.StandardCharsets.UTF_8

import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpInterceptor.Chain
import com.hotels.styx.api.HttpRequest.Builder.get
import com.hotels.styx.api._
import com.hotels.styx.api.messages.HttpResponseStatus.INTERNAL_SERVER_ERROR
import com.hotels.styx.infrastructure.HttpResponseImplicits
import com.hotels.styx.support.ResourcePaths.fixturesHome
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration._
import com.hotels.styx.{PluginAdapter, StyxClientSupplier, StyxProxySpec}
import org.scalatest.FunSpec
import org.scalatest.concurrent.Eventually
import rx.Observable

import scala.concurrent.duration._

class HealthCheckSpec extends FunSpec
  with StyxProxySpec
  with HttpResponseImplicits
  with StyxClientSupplier
  with Eventually {

  val originOne = FakeHttpServer.HttpStartupConfig(appId = "app", originId="h1").start()
  val logback = fixturesHome(this.getClass, "/conf/logback/logback-suppress-errors.xml")

  override val styxConfig = StyxConfig(
    plugins = List("faulty" -> new FaultyPlugin),
    logbackXmlLocation = logback
  )

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    styxServer.setBackends("/healthCheckSpec/" -> HttpBackend("origin-one", Origins(originOne)))
  }

  override protected def afterAll(): Unit = {
    originOne.stop()
    super.afterAll()
  }

  describe("Styx admin health check") {
    it("Reports unhealthy if error rate is greater than the configured error threshold") {
      for (i <- 1 to 120) {
        val response = decodedRequest(
          get("/")
            .addHeader(HOST, styxServer.proxyHost)
            .build())

        assert(response.status == INTERNAL_SERVER_ERROR)
      }

      eventually(timeout(5.seconds)) {
        val healthCheckRequest = get("/admin/healthcheck")
          .addHeader(HOST, styxServer.adminHost)
          .build()

        val healthCheckResponse = decodedRequest(healthCheckRequest)

        assert(healthCheckResponse.status == INTERNAL_SERVER_ERROR)
        assert(healthCheckResponse.isNotCacheAble())
        healthCheckResponse.bodyAs(UTF_8) should include regex "\\{\"errors.rate.500\":\\{\"healthy\":false,\"message\":\"error count=[0-9]+ m1_rate=[0-9.]+ is greater than 1.0\",\"timestamp\":\".*\"}"
      }
    }
  }

  class FaultyPlugin extends PluginAdapter {
    override def intercept(request: HttpRequest, chain: Chain): Observable[HttpResponse] = {
      Observable.error(new RuntimeException)
    }
  }

}
