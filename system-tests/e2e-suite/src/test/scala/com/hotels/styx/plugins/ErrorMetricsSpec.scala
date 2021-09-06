/*
  Copyright (C) 2013-2021 Expedia Inc.

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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, urlMatching}
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpInterceptor.Chain
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.{BAD_GATEWAY, INTERNAL_SERVER_ERROR, OK}
import com.hotels.styx.api.LiveHttpResponse.response
import com.hotels.styx.api.extension.service.BackendService
import com.hotels.styx.api.{HttpResponseStatus, _}
import com.hotels.styx.infrastructure.{MemoryBackedRegistry, RegistryServiceAdapter}
import com.hotels.styx.support.backends.FakeHttpServer.HttpStartupConfig
import com.hotels.styx.support.configuration.{StyxConfig, _}
import com.hotels.styx.support.server.FakeHttpServer
import com.hotels.styx.support.{ImplicitStyxConversions, configuration}
import com.hotels.styx.{BackendServicesRegistrySupplier, StyxClientSupplier, StyxConfiguration, StyxServerSupport, _}
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FunSpec, Matchers}

import java.lang.Thread.sleep
import scala.compat.java8.OptionConverters.RichOptionalGeneric
import scala.concurrent.duration._

class ErrorMetricsSpec extends FunSpec
  with StyxServerSupport
  with BeforeAndAfterAll
  with BackendServicesRegistrySupplier
  with BeforeAndAfterEach
  with Matchers
  with ImplicitOriginConversions
  with ImplicitStyxConversions
  with StyxConfiguration
  with StyxClientSupplier
  with Eventually {

  var normalBackend: FakeHttpServer = _

  var backendsRegistry: MemoryBackedRegistry[BackendService] = _
  var styxServer: StyxServer = _

  override val styxConfig: StyxConfig = configuration.StyxConfig(ProxyConfig(), plugins = Map(
    "failAtOnCompletedPlugin" -> new OnCompleteErrorPlugin(),
    "generateErrorStatusPlugin" -> new Return500Interceptor(),
    "mapToErrorStatusPlugin" -> new MapTo500Interceptor(),
    "throwExceptionPlugin" -> new ThrowExceptionInterceptor(),
    "mapToExceptionPlugin" -> new MapToExceptionInterceptor(),
    "generateBadGatewayStatusPlugin" -> new Return502Interceptor(),
    "mapToBadGatewayStatusPlugin" -> new MapTo502Interceptor()
  ))


  override protected def beforeAll(): Unit = {
    super.beforeAll()
    normalBackend = HttpStartupConfig(appId = "appOne", originId = "01")
      .start()
      .stub(urlMatching("/.*"), aResponse.withStatus(200))
      .stub(urlMatching("/fail"), aResponse.withStatus(500))
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    backendsRegistry = new MemoryBackedRegistry[BackendService]
    styxServer = styxConfig.startServer(new RegistryServiceAdapter(backendsRegistry))
    styxServer.meterRegistry().micrometerRegistry().asInstanceOf[CompositeMeterRegistry].add(new SimpleMeterRegistry())
    setBackends(
      backendsRegistry,
      "/" -> HttpBackend(
        "appOne",
        Origins(normalBackend),
        responseTimeout = 5.seconds,
        connectionPoolConfig = ConnectionPoolSettings(maxConnectionsPerHost = 2)
      ))
  }

  override protected def afterEach(): Unit = {
    styxServer.stopAsync().awaitTerminated()
    super.afterEach()
  }

  override protected def afterAll(): Unit = {
    normalBackend.stop()
    super.afterAll()
  }

  describe("Styx as a plugin container") {
    it("Ignores failures at the end of proxying") {
      for (_ <- 1 to 2) {
        val request = get("/foo")
          .addHeader(HOST, styxServer.proxyHost)
          .header("Fail_at_onCompleted", "true")
          .build()

        val response = decodedRequest(request)

        assert(response.status() == OK)
      }

      Thread.sleep(1000)

      assert(internalServerErrorMetric == 0)
    }

    it("Records 500s created in plugins as plugin errors") {
      val request = get("/foo")
        .addHeader(HOST, styxServer.proxyHost)
        .header("Generate_error_status", "true")
        .build()

      val response = decodedRequest(request)

      assert(response.status() == INTERNAL_SERVER_ERROR)

      eventually(timeout(1.second)) {
        assert(pluginInternalServerErrorMetric("generateErrorStatusPlugin") == 1)
        assert(pluginUnexpectedErrorMetric("generateErrorStatusPlugin") == 1)
      }

      sleep(1000)

      assert(internalServerErrorMetric == 0)
      assert(pluginInternalServerErrorMetric("failAtOnCompletedPlugin") == 0)
      assert(styxUnexpectedErrorMetric == 0)
    }

    it("Records 500s mapped from responses in plugins as plugin errors") {
      val request = get("/foo")
        .addHeader(HOST, styxServer.proxyHost)
        .header("Map_to_error_status", "true")
        .build()

      val response = decodedRequest(request)

      assert(response.status() == INTERNAL_SERVER_ERROR)

      eventually(timeout(1.second)) {
        assert(pluginInternalServerErrorMetric("mapToErrorStatusPlugin") == 1)
        assert(pluginUnexpectedErrorMetric("mapToErrorStatusPlugin") == 1)
      }

      sleep(1000)

      assert(internalServerErrorMetric == 0)
      assert(pluginInternalServerErrorMetric("failAtOnCompletedPlugin") == 0)
      assert(styxUnexpectedErrorMetric == 0)
    }

    it("Does not record 500s from origins as plugin errors") {
      val request = get("/fail")
        .addHeader(HOST, styxServer.proxyHost)
        .build()

      val response = decodedRequest(request)

      assert(response.status() == INTERNAL_SERVER_ERROR)

      sleep(1000)

      assert(pluginInternalServerErrorMetric("generateErrorStatusPlugin") == 0)
      assert(pluginInternalServerErrorMetric("failAtOnCompletedPlugin") == 0)
      assert(pluginUnexpectedErrorMetric("generateErrorStatusPlugin") == 0)
      assert(pluginUnexpectedErrorMetric("failAtOnCompletedPlugin") == 0)
      assert(internalServerErrorMetric == 0)
      assert(styxUnexpectedErrorMetric == 0)
    }

    it("Does not record non-500 5xxs created in plugins as plugin errors or styx errors") {
      val request = get("/foo")
        .addHeader(HOST, styxServer.proxyHost)
        .header("Generate_bad_gateway_status", "true")
        .build()

      val response = decodedRequest(request)

      assert(response.status() == BAD_GATEWAY)

      sleep(1000)

      assert(pluginUnexpectedErrorMetric("generateBadGatewayStatusPlugin") == 0)
      assert(styxUnexpectedErrorMetric == 0)
    }

    it("Does not record non-500 5xxs mapped from responses in plugins as plugin errors or styx errors") {
      val request = get("/foo")
        .addHeader(HOST, styxServer.proxyHost)
        .header("Map_to_bad_gateway_status", "true")
        .build()

      val response = decodedRequest(request)

      assert(response.status() == BAD_GATEWAY)

      sleep(1000)

      assert(pluginUnexpectedErrorMetric("mapToBadGatewayStatusPlugin") == 0)
      assert(styxUnexpectedErrorMetric == 0)
    }

    it("Records Exceptions from plugins as plugin exceptions") {
      val request = get("/foo")
        .addHeader(HOST, styxServer.proxyHost)
        .header("Throw_an_exception", "true")
        .build()

      val response = decodedRequest(request)

      assert(response.status() == INTERNAL_SERVER_ERROR)

      eventually(timeout(1.second)) {
        assert(pluginExceptionMetric("throwExceptionPlugin") == 1)
        assert(pluginInternalServerErrorMetric("throwExceptionPlugin") == 1)
        assert(pluginUnexpectedErrorMetric("throwExceptionPlugin") == 1)
      }

      sleep(1000)

      assert(pluginExceptionMetric("generateErrorStatusPlugin") == 0)
      assert(pluginExceptionMetric("mapToExceptionPlugin") == 0)
      assert(styxExceptionMetric == 0)
      assert(styxUnexpectedErrorMetric == 0)

    }

    it("Records Exceptions from plugin response mapping as plugin exceptions") {
      val request = get("/foo")
        .addHeader(HOST, styxServer.proxyHost)
        .header("Map_to_exception", "true")
        .build()

      val response = decodedRequest(request)

      assert(response.status() == INTERNAL_SERVER_ERROR)

      eventually(timeout(1.second)) {
        assert(pluginExceptionMetric("mapToExceptionPlugin") == 1)
        assert(pluginInternalServerErrorMetric("mapToExceptionPlugin") == 1)
        assert(pluginUnexpectedErrorMetric("mapToExceptionPlugin") == 1)
      }

      sleep(1000)

      assert(pluginExceptionMetric("generateErrorStatusPlugin") == 0)
      assert(pluginExceptionMetric("throwExceptionPlugin") == 0)
      assert(styxExceptionMetric == 0)
      assert(styxUnexpectedErrorMetric == 0)
    }
  }

  private def styxExceptionMetric = {
    styxServer.metricsSnapshot.count("styx.exception.com.hotels.styx.support.JustATestException").getOrElse(0)
  }

  def pluginExceptionMetric(pluginName: String): Double = {
    try {
      styxServer.meterRegistry()
        .get("proxy.plugins.exceptions")
        .tag("plugin", pluginName)
        .tag("type", "com_hotels_styx_plugins_ErrorMetricsSpec$TestException")
        .counter().count()
    } catch {
      case _: io.micrometer.core.instrument.search.MeterNotFoundException => 0.0
    }
  }

  def internalServerErrorMetric: Long = {
    sleep(1000)
    val metrics = styxServer.metricsSnapshot

    metrics.count("styx.response.status.500").getOrElse(0)
  }

  def pluginInternalServerErrorMetric(pluginName: String): Double = {
    try {
      styxServer.meterRegistry()
        .get("proxy.plugins.errorResponses")
        .tag("plugin", pluginName)
        .tag("statusCode", "500")
        .counter().count()
    } catch {
      case _: io.micrometer.core.instrument.search.MeterNotFoundException => 0.0
    }
  }

  def pluginUnexpectedErrorMetric(pluginName: String): Double = {
    styxServer.meterRegistry()
      .get("proxy.plugins.errors")
      .tag("plugin", pluginName)
      .counter().count()
  }

  def styxUnexpectedErrorMetric(): Int = {
    styxServer.metricsSnapshot.meter("proxy.unexpectedErrors").map(meter => meter.count).getOrElse(0)
  }

  private class Return500Interceptor extends PluginAdapter {
    override def intercept(request: LiveHttpRequest, chain: Chain): Eventual[LiveHttpResponse] = {
      if (request.header("Generate_error_status").asScala.contains("true"))
        Eventual.of(response(HttpResponseStatus.INTERNAL_SERVER_ERROR).build())
      else
        chain.proceed(request)
    }
  }

  import scala.compat.java8.FunctionConverters.asJavaFunction

  private class MapTo500Interceptor extends PluginAdapter {
    override def intercept(request: LiveHttpRequest, chain: Chain): Eventual[LiveHttpResponse] = {
      if (request.header("Map_to_error_status").asScala.contains("true"))
        chain.proceed(request).flatMap(
          asJavaFunction((_: LiveHttpResponse) => Eventual.of(response(HttpResponseStatus.INTERNAL_SERVER_ERROR).build())
          ))
      else
        chain.proceed(request)
    }
  }

  private class Return502Interceptor extends PluginAdapter {
    override def intercept(request: LiveHttpRequest, chain: Chain): Eventual[LiveHttpResponse] = {
      if (request.header("Generate_bad_gateway_status").asScala.contains("true"))
        Eventual.of(response(HttpResponseStatus.BAD_GATEWAY).build())
      else
        chain.proceed(request)
    }
  }

  private class MapTo502Interceptor extends PluginAdapter {
    override def intercept(request: LiveHttpRequest, chain: Chain): Eventual[LiveHttpResponse] = {
      if (request.header("Map_to_bad_gateway_status").asScala.contains("true"))
        chain.proceed(request).flatMap(
          asJavaFunction((_: LiveHttpResponse) => Eventual.of(response(HttpResponseStatus.BAD_GATEWAY).build())
          ))
      else
        chain.proceed(request)
    }
  }

  private class ThrowExceptionInterceptor extends PluginAdapter {
    override def intercept(request: LiveHttpRequest, chain: Chain): Eventual[LiveHttpResponse] = {
      if (request.header("Throw_an_exception").asScala.contains("true"))
        throw new TestException()//JustATestException()
      else
        chain.proceed(request)
    }
  }

  private class MapToExceptionInterceptor extends PluginAdapter {

    override def intercept(request: LiveHttpRequest, chain: Chain): Eventual[LiveHttpResponse] = {
      if (request.header("Map_to_exception").asScala.contains("true"))
        chain.proceed(request).flatMap(asJavaFunction((_: LiveHttpResponse) => Eventual.error(new TestException())))
      else
        chain.proceed(request)
    }
  }

  private class TestException extends RuntimeException {}
}
