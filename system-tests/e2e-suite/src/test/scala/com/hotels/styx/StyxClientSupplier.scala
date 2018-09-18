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

import java.util.concurrent.TimeUnit.MILLISECONDS

import com.hotels.styx.api._
import com.hotels.styx.client.StyxHttpClient
import org.scalatest.{BeforeAndAfterAll, Suite}

import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait StyxClientSupplier extends BeforeAndAfterAll {
  this: Suite =>

  val TWO_SECONDS: Int = 2 * 1000
  val FIVE_SECONDS: Int = 5 * 1000

  val client: StyxHttpClient = new StyxHttpClient.Builder()
    .threadName("scalatest-e2e-client")
    .connectTimeout(1000, MILLISECONDS)
    .maxHeaderSize(2 * 8192)
    .build()

  override protected def afterAll() = {
    client.shutdown()
    super.afterAll()
  }

  private def doRequest(request: FullHttpRequest, secure: Boolean = false): Future[FullHttpResponse] = if (secure)
    client.secure().send(request).toScala
  else
    client.sendRequest(request).toScala

  def decodedRequest(request: FullHttpRequest,
                     debug: Boolean = false,
                     maxSize: Int = 1024 * 1024, timeout: Duration = 30.seconds,
                     secure: Boolean = false
                    ): FullHttpResponse = {
    val future = doRequest(request, secure = secure)
      .map(response => {
        if (debug) {
          println("StyxClientSupplier: received response for: " + request.url().path())
        }
        response
      })
    Await.result(future, timeout)
  }

}
