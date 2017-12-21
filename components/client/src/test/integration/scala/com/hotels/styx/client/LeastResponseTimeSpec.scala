/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.client

import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{CountDownLatch, Executor, Executors}

import com.codahale.metrics.json.MetricsModule
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.base.Objects
import com.hotels.styx.api.HttpRequest.Builder.get
import com.hotels.styx.api.client.{ActiveOrigins, Origin, UrlConnectionHttpClient}
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry
import com.hotels.styx.api.{HttpClient, HttpResponse, Id}
import com.hotels.styx.client.OriginsInventory.newOriginsInventoryBuilder
import com.hotels.styx.client.StyxHttpClient.newHttpClientBuilder
import com.hotels.styx.client.applications.BackendService
import com.hotels.styx.client.healthcheck.HealthCheckConfig
import com.hotels.styx.client.loadbalancing.strategies.{AdaptiveStrategy, RoundRobinStrategy}
import com.hotels.styx.support.server.FakeHttpServer
import org.mockito.Mockito
import org.scalatest.{BeforeAndAfter, FunSuite, ShouldMatchers}
import rx.Observer

import scala.collection.immutable

class LeastResponseTimeSpec extends FunSuite with BeforeAndAfter with ShouldMatchers with OriginSupport {
  val client: HttpClient = new UrlConnectionHttpClient(10000, 10000)

  val (appOriginOne, server1) = originAndServer("webapp", "webapp-01")
  val (appOriginTwo, server2) = originAndServer("webapp", "webapp-02")

  var httpClient: StyxHttpClient = _
  val metricRegistry = new CodaHaleMetricRegistry()
  val ADAPTIVE_WARMUP_COUNT = 200

  var servers: Map[Origin, FakeHttpServer] = _

  val twoSecondsInterval = HealthCheckConfig.newHealthCheckConfigBuilder()
    .interval(2, SECONDS)
    .build()

  before {
    servers = Map(
      appOriginOne -> server1,
      appOriginTwo -> server2)

    val service = new BackendService.Builder()
      .id(Id.id("webapp"))
      .origins(appOriginOne, appOriginTwo)
      .healthCheckConfig(twoSecondsInterval)
      .build()

    val activeOrigins = Mockito.mock(classOf[ActiveOrigins])

    val httpClient = newHttpClientBuilder(service)
      .loadBalancingStrategy(new AdaptiveStrategy(activeOrigins))
      .build()
  }

  after {
    servers(appOriginOne).stop()
    servers(appOriginTwo).stop()
  }

  val executor: Executor = Executors.newFixedThreadPool(100)

  def displayMetrics(metrics: CodaHaleMetricRegistry) = {
    val mapper = new ObjectMapper()
    mapper.registerModule(new MetricsModule(SECONDS, SECONDS, false))
    mapper.writer().withDefaultPrettyPrinter().writeValueAsString(metrics)
  }

  def warmUpLoadBalancer() = {
    makeRequestsAndCollectResponses(2)
  }

  def makeRequestsAndCollectResponses(totalRequests: Int) = {
    val start = new CountDownLatch(1)
    val end = new CountDownLatch(totalRequests)

    val collector: ResponseCountingObserver = new ResponseCountingObserver(end)

    repeat(totalRequests) {
      executor.execute(new Worker(start, collector))
    }

    start.countDown
    end.await

    collector
  }

  def repeat(count: Int)(body: => Unit) {
    for (i <- 1 to count) {
      body
      Thread.sleep(5)
    }
  }

  class ResponseCountingObserver(val latch: CountDownLatch) extends Observer[HttpResponse] {

    var responsesCountByOrigin = immutable.Map[String, AtomicInteger]()
    val totalCount = new AtomicInteger()

    override def onCompleted(): Unit = latch.countDown()

    override def onError(e: Throwable): Unit = latch.countDown()

    override def onNext(result: HttpResponse) = {
      incrementCount(result)
    }

    private def incrementCount(response: HttpResponse) {
      val applicationInfo = response.header("X-Hcom-Info").get()
      responsesCountByOrigin.get(applicationInfo) match {
        case None => responsesCountByOrigin = responsesCountByOrigin.updated(applicationInfo, new AtomicInteger())
        case Some(v) => v.incrementAndGet
      }
      totalCount.incrementAndGet
    }

    override def toString: String = {
      return Objects.toStringHelper(this).add("responseCountByOrigin", this.responsesCountByOrigin).toString
    }

    def proportion(origin: Origin): Double = {
      return responsesCountByOrigin.get(origin.applicationInfo()).get.get() / totalCount.get.asInstanceOf[Double]
    }
  }

  private class Worker(val start: CountDownLatch, val collector: ResponseCountingObserver) extends Runnable {
    def run {
      val request = get("/version.txt")
        .build()

      httpClient.sendRequest(request).subscribe(collector)
    }
  }

}
