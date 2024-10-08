/*
  Copyright (C) 2013-2024 Expedia Inc.

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
import java.time.Duration

import com.hotels.styx.MockServer.responseSupplier
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api._
import com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY
import com.hotels.styx.support.configuration.{HttpBackend, Origins, StyxConfig}
import com.hotels.styx.{MockServer, StyxProxySpec}
import com.hotels.styx.api.HttpResponseStatus._
import io.micrometer.core.instrument.Tags
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.concurrent.Eventually
import reactor.core.publisher.Flux

import scala.concurrent.duration._

class AggregatingPluginContentOverflowSpec extends AnyFunSpec
  with StyxProxySpec
  with Eventually {
  val mockServer = new MockServer("origin-1", 0)
  override val styxConfig = StyxConfig(plugins = Map("aggregator" -> new AggregationTesterPlugin(2750)))

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    mockServer.startAsync().awaitRunning()
    styxServer.setBackends(
      "/" -> HttpBackend("app1", Origins(mockServer.origin), responseTimeout = 3.seconds))
  }

  override protected def afterAll(): Unit = {
    mockServer.stopAsync().awaitTerminated()
    val metrics = styxServer.metricsSnapshot
    super.afterAll()
  }

  describe("Styx as a plugin container") {

    /*
     * Note: Ensure this test runs on its own, so that a styx instance is a fresh one.
     * Otherwise we cannot do assertions on metric values, as they may have influenced
     * by the other tests.
     */

    it("Returns 502 Bad Gateway when content exceeds maximum allowed number of bytes") {

      mockServer.stub("/body", responseSupplier(
        () => {
          LiveHttpResponse.response(OK).body(new ByteStream(
                        delay(Duration.ofMillis(500),
                          Seq(
                            buf("a" * 1000),
                            buf("b" * 1000),
                            buf("c" * 1000),
                            buf("d" * 1000),
                            buf("e" * 1000),
                            buf("f" * 1000)))
                        )).build()
        }))

      val request = get(styxServer.routerURL("/body"))
        .build()
      val resp = decodedRequest(request)

      assert(resp.status() == BAD_GATEWAY)

      eventually(timeout(3 seconds)) {
        val tags = Tags.of("appId", "app1", "originId", "origin-1")
        meterRegistry.find("proxy.client.connectionpool.busyConnections").tags(tags).gauge().value() should be(0.0)
        meterRegistry.find("proxy.client.connectionpool.connectionsClosed").tags(tags).gauge().value() should be(1.0)
      }
    }
  }

  def buf(string: String): Buffer = new Buffer(string, UTF_8)

  def delay(time: Duration, buffers: Seq[Buffer]): Flux[Buffer] = {
    val buffy: Array[Buffer] = buffers.toArray
    val bufferFlux : Flux[Buffer] = Flux.fromArray(buffy)
    Flux.interval(time)
      .zipWith(bufferFlux)
      .map(i => i.getT2)
  }
}
