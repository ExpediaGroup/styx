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

import com.hotels.styx.api.FullHttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.support.configuration.{ConnectionPoolSettings, HttpBackend, Origins}
import com.hotels.styx.support.{NettyOrigins, TestClientSupport}
import com.hotels.styx.{DefaultStyxConfiguration, StyxProxySpec, api}
import org.scalatest.FunSpec
import org.scalatest.concurrent.Eventually
import com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY
import com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH

class OriginCancellationMetrics extends FunSpec
  with StyxProxySpec
  with DefaultStyxConfiguration
  with NettyOrigins
  with TestClientSupport
  with Eventually {


  val (originOne, originOneServer) = originAndCustomResponseWebServer("NettyOrigin")

  override protected def beforeAll() = {
    super.beforeAll()
    styxServer.setBackends(
      "/OriginCancellationMetrics/" -> HttpBackend(
        "app-1", Origins(originOne),
        connectionPoolConfig = ConnectionPoolSettings(maxConnectionsPerHost = 1)
      )
    )
  }

  override protected def afterAll(): Unit = {
    originOneServer.stopAsync().awaitTerminated()
    // This test is failing intermittently. Print the metrics snapshot in case it fails,
    // to offer insight into what is going wrong:
    println("Styx metrics after BadResponseFromOriginSpec: " + styxServer.metricsSnapshot)
    super.afterAll()
  }

  describe("origins.<ID>.cancelled metric") {
    it("Is not incremented on success") {
      originRespondingWith(
        responseWithHeaders(
          HttpHeader(CONTENT_LENGTH, "0")
        ))

      val request = get(styxServer.routerURL("/OriginCancellationMetrics/1")).build()
      val response = decodedRequest(request)
      response.status() should be(OK)

      styxServer.metricsSnapshot.count("origins.app-1.requests.cancelled").get should be(0)
    }

    it("Is incremented on an origin error") {
      originRespondingWith(
        responseWithHeaders(
          HttpHeader(CONTENT_LENGTH, "0"),
          HttpHeader(CONTENT_LENGTH, "0")
        ))

      val request = get(styxServer.routerURL("/OriginCancellationMetrics/2")).build()
      val response = decodedRequest(request)
      response.status() should be(BAD_GATEWAY)

      styxServer.metricsSnapshot.count("origins.app-1.requests.cancelled").get should be(1)
    }
  }

}
