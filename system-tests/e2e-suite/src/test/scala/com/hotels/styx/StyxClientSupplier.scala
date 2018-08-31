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

import com.hotels.styx.api._
import com.hotels.styx.api.extension.service.TlsSettings
import com.hotels.styx.client.{ConnectionSettings, SimpleHttpClient}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.ExecutionContext.Implicits.global

trait StyxClientSupplier {
  val TWO_SECONDS: Int = 2 * 1000
  val FIVE_SECONDS: Int = 5 * 1000

  val client: FullHttpClient = new SimpleHttpClient.Builder()
    .threadName("scalatest-e2e-client")
    .connectionSettings(new ConnectionSettings(1000))
    .maxHeaderSize(2 * 8192)
    .build()

  val httpsClient: FullHttpClient = new SimpleHttpClient.Builder()
    .threadName("scalatest-e2e-secure-client")
    .connectionSettings(new ConnectionSettings(1000))
    .maxHeaderSize(2 * 8192)
    .tlsSettings(new TlsSettings.Builder().build())
    .build()


  private def doHttpRequest(request: FullHttpRequest, debug: Boolean = false): Future[FullHttpResponse] = client.sendRequest(request).toScala

  private def doSecureRequest(request: FullHttpRequest): Future[FullHttpResponse] = httpsClient.sendRequest(request).toScala

  private def doRequest(request: FullHttpRequest, debug: Boolean = false): Future[FullHttpResponse] = if (request.isSecure)
    doSecureRequest(request)
  else
    doHttpRequest(request, debug = debug)

  def decodedRequest(request: FullHttpRequest,
                     debug: Boolean = false,
                     maxSize: Int = 1024 * 1024, timeout: Duration = 30.seconds): FullHttpResponse = {
    val future = doRequest(request, debug = debug)
      .map(response => {
        if (debug) {
          println("StyxClientSupplier: received response for: " + request.url().path())
        }
        response
      })
    Await.result(future, timeout)
  }

}
