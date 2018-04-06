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

import com.hotels.styx.MockServer.responseSupplier
import com.hotels.styx.api.HttpRequest.Builder._
import com.hotels.styx.api.HttpResponse.Builder._
import com.hotels.styx.api.messages.HttpResponseStatus.OK
import com.hotels.styx.support.configuration.{HttpBackend, Origins, StyxConfig}
import com.hotels.styx.{MockServer, StyxProxySpec}
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.handler.codec.http.HttpResponseStatus
import org.scalatest.FunSpec
import org.scalatest.concurrent.Eventually
import rx.Observable.just

import scala.concurrent.duration._

class AggregatingPluginSpec extends FunSpec
  with StyxProxySpec
  with Eventually {
  val mockServer = new MockServer("origin-1", 0)
  override val styxConfig = StyxConfig(plugins = List("aggregator" -> new AggregationTesterPlugin(2750)))

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    mockServer.startAsync().awaitRunning()
    styxServer.setBackends(
      "/" -> HttpBackend("app1", Origins(mockServer.origin), responseTimeout = 3.seconds))
  }

  override protected def afterAll(): Unit = {
    mockServer.stopAsync().awaitTerminated()
    super.afterAll()
  }

  describe("Styx as a plugin container") {

    it("Gets response from aggregating plugin (no body)") {
      mockServer.stub("/", responseSupplier(() => response(HttpResponseStatus.OK).build()))

      val request = get(styxServer.routerURL("/")).build()
      val resp = decodedRequest(request)

      assert(resp.status() == OK)
      assert(resp.header("test_plugin").get() == "yes")
      assert(resp.header("bytes_aggregated").get() == "0")
      assert(resp.bodyAs(UTF_8) == "")
    }

    it("Gets response from aggregating plugin (with body)") {
      mockServer.stub("/body", responseSupplier(
        () => response(HttpResponseStatus.OK).body(just(chunk("a"), chunk("b"), chunk("c"), chunk("d"), chunk("e"))).build()
      ))

      val request = get(styxServer.routerURL("/body")).build()
      val resp = decodedRequest(request)

      assert(resp.status() == OK)
      assert(resp.header("test_plugin").get() == "yes")
      assert(resp.header("bytes_aggregated").get() == "2500")
      assert(resp.bodyAs(UTF_8) == chunkString("a") + chunkString("b") + chunkString("c") + chunkString("d") + chunkString("e"))
    }
  }

  def chunk(from: String): ByteBuf = buf(chunkString(from))

  def chunkString(from: String): String = from * 500

  def buf(string: String): ByteBuf = Unpooled.copiedBuffer(string, UTF_8)

}
