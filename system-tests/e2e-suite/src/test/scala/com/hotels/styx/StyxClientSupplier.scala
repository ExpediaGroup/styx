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
package com.hotels.styx

import com.hotels.styx.api.client.UrlConnectionHttpClient
import com.hotels.styx.api.messages.FullHttpResponse
import com.hotels.styx.api.{HttpClient, HttpRequest, HttpResponse}
import com.hotels.styx.client.HttpConfig.newHttpConfigBuilder
import com.hotels.styx.client.HttpRequestOperationFactory.Builder.httpRequestOperationFactoryBuilder
import com.hotels.styx.client.connectionpool.CloseAfterUseConnectionDestination.Factory
import com.hotels.styx.client.netty.connectionpool.NettyConnectionFactory
import com.hotels.styx.client.{ConnectionSettings, SimpleNettyHttpClient}
import rx.lang.scala.JavaConversions.toScalaObservable
import rx.lang.scala.Observable

import scala.concurrent.duration._

trait StyxClientSupplier {
  val TWO_SECONDS: Int = 2 * 1000
  val FIVE_SECONDS: Int = 5 * 1000

  val client: HttpClient = new SimpleNettyHttpClient.Builder()
    .connectionDestinationFactory(new Factory()
      .connectionSettings(new ConnectionSettings(1000))
      .connectionFactory(new NettyConnectionFactory.Builder()
        .name("scalatest-e2e-client")
        .httpConfig(newHttpConfigBuilder().setMaxHeadersSize(2 * 8192).build())
          .httpRequestOperationFactory(httpRequestOperationFactoryBuilder().build())
        .build())
    )
    .build

  val httpsClient: HttpClient = new UrlConnectionHttpClient(TWO_SECONDS, FIVE_SECONDS)

  private def doHttpRequest(request: HttpRequest, debug: Boolean = false): Observable[HttpResponse] = toScalaObservable(client.sendRequest(request))

  private def doSecureRequest(request: HttpRequest): Observable[HttpResponse] = httpsClient.sendRequest(request)

  private def doRequest(request: HttpRequest, debug: Boolean = false): Observable[HttpResponse] = if (request.isSecure)
    doSecureRequest(request)
  else
    doHttpRequest(request, debug = debug)

  def decodedRequest(request: HttpRequest,
                     debug: Boolean = false,
                     maxSize: Int = 1024 * 1024, timeout: Duration = 30.seconds): FullHttpResponse = {
    doRequest(request, debug = debug)
      .doOnNext(response => if (debug) println("StyxClientSupplier: received response for: " + request.url().path()))
      .flatMap(response => response.toFullResponse(maxSize))
      .timeout(timeout)
      .toBlocking
      .first
  }

  def decodedRequestWithClient(client: HttpClient,
                               request: HttpRequest,
                               debug: Boolean = false,
                               maxSize: Int = 1024 * 1024, timeout: Duration = 30.seconds): FullHttpResponse = {
    toScalaObservable(client.sendRequest(request))
      .doOnNext(response => if (debug) println("StyxClientSupplier: received response for: " + request.url().path()))
      .flatMap(response => response.toFullResponse(maxSize))
      .timeout(timeout)
      .toBlocking
      .first
  }
}
