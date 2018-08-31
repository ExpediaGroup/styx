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
import com.hotels.styx.api.FullHttpRequest.get
import com.hotels.styx.api.{HttpResponse, StyxInternalObservables}
import com.hotels.styx.api.HttpResponse._
import com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY
import com.hotels.styx.common.HostAndPorts._
import com.hotels.styx.support.configuration.{HttpBackend, Origins, StyxConfig}
import com.hotels.styx.{MockServer, StyxProxySpec}
import io.netty.buffer.{ByteBuf, Unpooled}
import com.hotels.styx.api.HttpResponseStatus._
import org.scalatest.FunSpec
import org.scalatest.concurrent.Eventually
import rx.Observable
import rx.lang.scala.JavaConversions._

import scala.concurrent.duration._
import com.hotels.styx.api.StyxInternalObservables.fromRxObservable

class AggregatingPluginContentOverflowSpec extends FunSpec
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
    val metrics = styxServer.metricsSnapshot
    println("Styx metrics after AggregatingPluginContentOverflowSpec:\n" + metrics)
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
          HttpResponse.response(OK).body(
            StyxInternalObservables.fromRxObservable(toJavaObservable(
              delay(500.millis,
                Seq(
                  buf("a" * 1000),
                  buf("b" * 1000),
                  buf("c" * 1000),
                  buf("d" * 1000),
                  buf("e" * 1000),
                  buf("f" * 1000))))
              .asInstanceOf[Observable[ByteBuf]])).build()
        }))

      val request = get(styxServer.routerURL("/body"))
        .build()
      val resp = decodedRequest(request)

      assert(resp.status() == BAD_GATEWAY)

      eventually(timeout(3 seconds)) {
        val metrics = styxServer.metricsSnapshot
        metrics.gauge("origins.app1.origin-1.connectionspool.available-connections").get should be(0)
        metrics.gauge("origins.app1.origin-1.connectionspool.busy-connections").get should be(0)
        metrics.gauge("origins.app1.origin-1.connectionspool.pending-connections").get should be(0)
      }
    }
  }

  def buf(string: String): ByteBuf = Unpooled.copiedBuffer(string, UTF_8)

  import rx.lang.scala.Observable

  def delay(time: Duration, buffers: Seq[ByteBuf]) = {
    Observable.interval(time)
      .zip(Observable.from(buffers))
      .map { case (i, buf) => buf }
  }

}
