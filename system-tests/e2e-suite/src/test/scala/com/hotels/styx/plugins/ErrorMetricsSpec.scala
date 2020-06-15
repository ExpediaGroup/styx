/*
  Copyright (C) 2013-2020 Expedia Inc.

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

import java.lang.Thread.sleep

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, urlMatching}
import com.hotels.styx._
import com.hotels.styx.{BackendServicesRegistrySupplier, StyxClientSupplier, StyxConfiguration, StyxServerSupport}
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpInterceptor.Chain
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.LiveHttpResponse.response
import com.hotels.styx.api.{HttpResponseStatus, _}
import com.hotels.styx.api.HttpResponseStatus.{BAD_GATEWAY, INTERNAL_SERVER_ERROR, OK}
import com.hotels.styx.api.extension.service.BackendService
import com.hotels.styx.infrastructure.{MemoryBackedRegistry, RegistryServiceAdapter}
import com.hotels.styx.support.ImplicitStyxConversions
import com.hotels.styx.support.backends.FakeHttpServer.HttpStartupConfig
import com.hotels.styx.support.configuration
import com.hotels.styx.support.configuration._
import com.hotels.styx.support.server.FakeHttpServer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FunSpec, Matchers}
import io.micrometer.core.instrument.{MeterRegistry, Metrics}

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
  var metricRegistry: MeterRegistry = new SimpleMeterRegistry()
  var backendsRegistry: MemoryBackedRegistry[BackendService] = _
  var styxServer: StyxServer = _

  override val styxConfig = configuration.StyxConfig(ProxyConfig(), plugins = Map(
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
    Metrics.addRegistry(metricRegistry)
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    backendsRegistry = new MemoryBackedRegistry[BackendService]
    styxServer = styxConfig.startServer(new RegistryServiceAdapter(backendsRegistry))
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
    Metrics.removeRegistry(metricRegistry)
    metricRegistry.clear()
    metricRegistry.close()
    normalBackend.stop()
    super.afterAll()
  }

  describe("Styx as a plugin container") {
    it("Ignores failures at the end of proxying") {
      for (i <- 1 to 2) {
        val request = get("/foo")
          .addHeader(HOST, styxServer.proxyHost)
          .header("Fail_at_onCompleted", "true")
          .build()

        val response = decodedRequest(request)

        assert(response.status() == OK)
      }

      Thread.sleep(1000)

      assert(internalServerErrorMetric(styxServer) == 0)
    }

    it("Records 500s created in plugins as plugin errors") {
      val token = "1"
      val server = customServer(token)


      val request = get("/foo")
        .addHeader(HOST, server.proxyHost)
        .header("Generate_error_status", "true")
        .build()

      // actually does the request
      val response = decodedRequest(request)

      assert(response.status() == INTERNAL_SERVER_ERROR)

      eventually(timeout(1.second)) {
        assert(pluginInternalServerErrorMetric("generateErrorStatusPlugin1") == 1)
        assert(pluginUnexpectedErrorMetric("generateErrorStatusPlugin1") == 1)
      }

      sleep(1000)

      assert(originErrorMetric(server) == 0)
      assert(internalServerErrorMetric(server) == 0)
      assert(pluginInternalServerErrorMetric("failAtOnCompletedPlugin1") == 0)
      assert(styxUnexpectedErrorMetric(server) == 0)

      customTeardown(server)
    }

    it("Records 500s mapped from responses in plugins as plugin errors") {
      val token = "2"
      val server = customServer(token)
      val request = get("/foo")
        .addHeader(HOST, server.proxyHost)
        .header("Map_to_error_status", "true")
        .build()

      val response = decodedRequest(request)

      assert(response.status() == INTERNAL_SERVER_ERROR)

      eventually(timeout(1.second)) {
        assert(pluginInternalServerErrorMetric("mapToErrorStatusPlugin2") == 1)
        assert(pluginUnexpectedErrorMetric("mapToErrorStatusPlugin2") == 1)
      }

      sleep(1000)

      assert(originErrorMetric(server) == 0)
      assert(internalServerErrorMetric(server) == 0)
      assert(pluginInternalServerErrorMetric("failAtOnCompletedPlugin2") == 0)
      assert(styxUnexpectedErrorMetric(server) == 0)
      customTeardown(server)
    }

    it("Does not record 500s from origins as plugin errors") {
      val token = "3"
      val server = customServer(token)
      val request = get("/fail")
        .addHeader(HOST, server.proxyHost)
        .build()

      val response = decodedRequest(request)

      assert(response.status() == INTERNAL_SERVER_ERROR)

      eventually(timeout(1.second)) {
        assert(originErrorMetric(server) == 1)
      }

      sleep(1000)

      assert(pluginInternalServerErrorMetric("generateErrorStatusPlugin3") == 0)
      assert(pluginInternalServerErrorMetric("failAtOnCompletedPlugin3") == 0)
      assert(pluginUnexpectedErrorMetric("generateErrorStatusPlugin3") == 0)
      assert(pluginUnexpectedErrorMetric("failAtOnCompletedPlugin3") == 0)
      assert(internalServerErrorMetric(server) == 0)
      assert(styxUnexpectedErrorMetric(server) == 0)
      customTeardown(server)
    }

    it("Does not record non-500 5xxs created in plugins as plugin errors or styx errors") {
      val token = "4"
      val server = customServer(token)
      val request = get("/foo")
        .addHeader(HOST, server.proxyHost)
        .header("Generate_bad_gateway_status", "true")
        .build()

      val response = decodedRequest(request)

      assert(response.status() == BAD_GATEWAY)

      sleep(1000)

      assert(originErrorMetric(server) == 0)
      assert(pluginUnexpectedErrorMetric("generateBadGatewayStatusPlugin4") == 0)
      assert(styxUnexpectedErrorMetric(server) == 0)
      customTeardown(server)
    }

    it("Does not record non-500 5xxs mapped from responses in plugins as plugin errors or styx errors") {
      val token = "5"
      val server = customServer(token)
      val request = get("/foo")
        .addHeader(HOST, styxServer.proxyHost)
        .header("Map_to_bad_gateway_status", "true")
        .build()

      val response = decodedRequest(request)

      assert(response.status() == BAD_GATEWAY)

      sleep(1000)

      assert(originErrorMetric(server) == 0)
      assert(pluginUnexpectedErrorMetric("mapToBadGatewayStatusPlugin5") == 0)
      assert(styxUnexpectedErrorMetric(server) == 0)
      customTeardown(server)
    }

    it("Records Exceptions from plugins as plugin exceptions") {
      val token = "6"
      val server = customServer(token)
      val request = get("/foo")
        .addHeader(HOST, server.proxyHost)
        .header("Throw_an_exception", "true")
        .build()

      val response = decodedRequest(request)

      assert(response.status() == INTERNAL_SERVER_ERROR)

      assert(pluginExceptionMetric("throwExceptionPlugin6") == 1)
      assert(pluginUnexpectedErrorMetric("throwExceptionPlugin6") == 1)

      sleep(1000)

      assert(pluginExceptionMetric("generateErrorStatusPlugin6") == 0)
      assert(pluginExceptionMetric("mapToExceptionPlugin6") == 0)
      assert(styxExceptionMetric(server) == 0)
      assert(styxUnexpectedErrorMetric(server) == 0)
      customTeardown(server)
    }

    it("Records Exceptions from plugin response mapping as plugin exceptions") {
      val token = "7"
      val server = customServer(token)
      val request = get("/foo")
        .addHeader(HOST, server.proxyHost)
        .header("Map_to_exception", "true")
        .build()

      val response = decodedRequest(request)

      assert(response.status() == INTERNAL_SERVER_ERROR)

      assert(pluginExceptionMetric("mapToExceptionPlugin7") == 1)
      assert(pluginUnexpectedErrorMetric("mapToExceptionPlugin7") == 1)

      sleep(1000)

      assert(pluginExceptionMetric("generateErrorStatusPlugin7") == 0)
      assert(pluginExceptionMetric("throwExceptionPlugin7") == 0)
      assert(styxExceptionMetric(server) == 0)
      assert(styxUnexpectedErrorMetric(server) == 0)
      customTeardown(server)
    }
  }

  private def styxExceptionMetric(server: StyxServer) = {
    server.metricsSnapshot.count("styx.exception.com.hotels.styx.plugins.ErrorMetricsSpec$TestException").getOrElse(0)
  }

  def pluginExceptionMetric(pluginName: String): Double = {
    Metrics.counter("plugins.exception", "plugin", pluginName, "type", "com_hotels_styx_plugins_ErrorMetricsSpec$TestException").count()
  }

  private def originErrorMetric(server: StyxServer) = {
    server.metricsSnapshot.meter("origins.appOne.01.requests.error-rate").map(meter => meter.count).getOrElse(0)
  }

  def internalServerErrorMetric(server: StyxServer): Long = {
    sleep(1000)
    val metrics = server.metricsSnapshot

    metrics.count("styx.response.status.500").getOrElse(0)
  }

  def pluginInternalServerErrorMetric(pluginName: String): Double = {
    Metrics.counter("plugins.response.status", "plugin", pluginName, "status", "500").count()
  }

  def pluginUnexpectedErrorMetric(pluginName: String): Double = {
    Metrics.counter("plugins.errors", "plugin", pluginName).count()
  }

  def styxUnexpectedErrorMetric(server: StyxServer): Int = {
    server.metricsSnapshot.meter("styx.errors").map(meter => meter.count).getOrElse(0)
  }

  class Return500Interceptor(token: String = "") extends PluginAdapter {
    val pluginName = "generateErrorStatusPlugin" + token
    override def intercept(request: LiveHttpRequest, chain: Chain): Eventual[LiveHttpResponse] = {
      if (request.header("Generate_error_status").asScala.contains("true")) {
        Metrics.counter("plugins.response.status", "plugin", pluginName, "status", "500").increment()
        Metrics.counter("plugins.errors", "plugin", pluginName).increment()
        Eventual.of(response(HttpResponseStatus.INTERNAL_SERVER_ERROR).build())
      } else
        chain.proceed(request)
    }
  }

  import scala.compat.java8.FunctionConverters.asJavaFunction

  class MapTo500Interceptor(token: String = "") extends PluginAdapter {
    val pluginName = "mapToErrorStatusPlugin" + token
    override def intercept(request: LiveHttpRequest, chain: Chain): Eventual[LiveHttpResponse] = {
      if (request.header("Map_to_error_status").asScala.contains("true")) {
        Metrics.counter("plugins.response.status", "plugin", pluginName, "status", "500").increment()
        Metrics.counter("plugins.errors", "plugin", pluginName).increment()
        chain.proceed(request).flatMap(
          asJavaFunction((t: LiveHttpResponse) => Eventual.of(response(HttpResponseStatus.INTERNAL_SERVER_ERROR).build())
          ))
      } else
        chain.proceed(request)
    }
  }

  class Return502Interceptor(token: String = "") extends PluginAdapter {
    val pluginName = "generateBadGatewayStatusPlugin" + token
    override def intercept(request: LiveHttpRequest, chain: Chain): Eventual[LiveHttpResponse] = {
      if (request.header("Generate_bad_gateway_status").asScala.contains("true")) {
        Metrics.counter("plugins.response.status", "plugin", pluginName, "status", "502").increment()
        Eventual.of(response(HttpResponseStatus.BAD_GATEWAY).build())
      } else
        chain.proceed(request)
    }
  }

  class MapTo502Interceptor(token: String = "") extends PluginAdapter {
    val pluginName = "mapToBadGatewayStatusPlugin" + token
    override def intercept(request: LiveHttpRequest, chain: Chain): Eventual[LiveHttpResponse] = {
      if (request.header("Map_to_bad_gateway_status").asScala.contains("true")) {
        Metrics.counter("plugins.response.status", "plugin", pluginName, "status", "502").increment()
        Metrics.counter("plugins.errors", "plugin", pluginName).increment()
        chain.proceed(request).flatMap(
          asJavaFunction((t: LiveHttpResponse) => Eventual.of(response(HttpResponseStatus.BAD_GATEWAY).build())
          ))
      } else
        chain.proceed(request)
    }
  }

  private class ThrowExceptionInterceptor(token: String = "") extends PluginAdapter {
    val pluginName: String = "throwExceptionPlugin" + token

    override def intercept(request: LiveHttpRequest, chain: Chain): Eventual[LiveHttpResponse] = {
      if (request.header("Throw_an_exception").asScala.contains("true")) {
        Metrics.counter("plugins.response.status", "plugin", pluginName, "status", "500").increment()
        Metrics.counter("plugins.errors", "plugin", pluginName).increment()
        Metrics.counter("plugins.exception", "plugin", pluginName, "type", "com_hotels_styx_plugins_ErrorMetricsSpec$TestException").increment()
        throw new TestException()
      } else {
        chain.proceed(request)
      }
    }
  }

  private class MapToExceptionInterceptor(token: String = "") extends PluginAdapter {
    val pluginName: String = "mapToExceptionPlugin" + token

    override def intercept(request: LiveHttpRequest, chain: Chain): Eventual[LiveHttpResponse] = {
      if (request.header("Map_to_exception").asScala.contains("true")) {
        Metrics.counter("plugins.response.status", "plugin", pluginName, "status", "500").increment()
        Metrics.counter("plugins.errors", "plugin", pluginName).increment()
        Metrics.counter("plugins.exception", "plugin", pluginName, "type", "com_hotels_styx_plugins_ErrorMetricsSpec$TestException").increment()
        chain.proceed(request).flatMap(asJavaFunction((t: LiveHttpResponse) => Eventual.error(new TestException())))
      } else
        chain.proceed(request)
    }
  }

  private def customServer(tok: String): StyxServer = {
    val config = configuration.StyxConfig(ProxyConfig(), plugins = Map(
      "failAtOnCompletedPlugin" -> new OnCompleteErrorPlugin(tok),
      "generateErrorStatusPlugin" -> new Return500Interceptor(tok),
      "mapToErrorStatusPlugin" -> new MapTo500Interceptor(tok),
      "throwExceptionPlugin" -> new ThrowExceptionInterceptor(tok),
      "mapToExceptionPlugin" -> new MapToExceptionInterceptor(tok),
      "generateBadGatewayStatusPlugin" -> new Return502Interceptor(tok),
      "mapToBadGatewayStatusPlugin" -> new MapTo502Interceptor(tok)
    ))
    val bregistry = new MemoryBackedRegistry[BackendService]
    val server: StyxServer = config.startServer(new RegistryServiceAdapter(bregistry))
    setBackends(
      bregistry,
      "/" -> HttpBackend(
        "appOne",
        Origins(normalBackend),
        responseTimeout = 5.seconds,
        connectionPoolConfig = ConnectionPoolSettings(maxConnectionsPerHost = 2)
      ))

    server
  }

  private def customTeardown(server: StyxServer) = {
    server.stopAsync().awaitTerminated()
  }

  private class TestException extends RuntimeException {

  }

}
