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
package com.hotels.styx.routing.config

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.function.Supplier
import java.{lang, util}

import _root_.io.netty.handler.codec.http.HttpResponseStatus.OK
import com.codahale.metrics.health.HealthCheckRegistry
import com.google.common.collect.ImmutableMap
import com.google.common.eventbus.AsyncEventBus
import com.hotels.styx.api.HttpRequest.Builder.get
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry
import com.hotels.styx.api.service.spi.StyxService
import com.hotels.styx.api.{HttpHandler2, HttpResponse}
import com.hotels.styx.client.applications.BackendService
import com.hotels.styx.client.applications.BackendService.newBackendServiceBuilder
import com.hotels.styx.infrastructure.AbstractRegistry
import com.hotels.styx.infrastructure.Registry.{Changes, ReloadResult}
import com.hotels.styx.proxy.plugin.NamedPlugin
import com.hotels.styx.proxy.plugin.NamedPlugin.namedPlugin
import com.hotels.styx.routing.{HttpHandlerAdapter, PluginAdapter, UserConfiguredPipelineFactory}
import com.hotels.styx.{AggregatedConfiguration, Environment, StyxConfig, Version}
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSpec, ShouldMatchers}
import rx.lang.scala.Observable

import scala.collection.JavaConverters._

class UserConfiguredPipelineFactorySpec extends FunSpec with ShouldMatchers with MockitoSugar {

  val registries: util.Map[String, StyxService] = ImmutableMap.of("registry1", backendRegistry(newBackendServiceBuilder.path("/foo").build))

  it ("Configures plugins in a given order") {
    val configuration = new StyxConfig(
        """
          |httpPipeline:
          |  name: Main Pipeline
          |  type: InterceptorPipeline
          |  config:
          |    pipeline:
          |      - plugA
          |      - plugB
          |    handler:
          |      type: StaticResponseHandler
          |      config:
          |        status: 201
          |        response: "secure"
        """.stripMargin)

    val interceptors: Supplier[lang.Iterable[NamedPlugin]] = pluginsSupplier(
      namedPlugin("plugA", new PluginAdapter((request, chain) => chain.proceed(request).map(response => response.newBuilder().addHeader("X-Test-Header", "A").build()))),
      namedPlugin("plugB", new PluginAdapter((request, chain) => chain.proceed(request).map(response => response.newBuilder().addHeader("X-Test-Header", "B").build())))
    )

    val handler: HttpHandler2 = new UserConfiguredPipelineFactory(buildEnvironment(configuration), configuration, interceptors, registries).build

    val response: HttpResponse = handler.handle(get("/foo").build, null).toBlocking.first
    response.status.code should be(201)
    response.headers("X-Test-Header").asScala should be(Seq("B", "A"))
  }


  it ("Builds HTTP pipeline with handler only when 'pipeline' attribute is missing") {
    val configuration = new StyxConfig(
        """
          |httpPipeline:
          |  name: Main Pipeline
          |  type: InterceptorPipeline
          |  config:
          |    handler:
          |      type: StaticResponseHandler
          |      config:
          |        status: 201
          |        response: "secure"
        """.stripMargin)

    val interceptors: Supplier[lang.Iterable[NamedPlugin]] = pluginsSupplier()

    val handler: HttpHandler2 = new UserConfiguredPipelineFactory(buildEnvironment(configuration), configuration, interceptors, registries).build

    val response: HttpResponse = handler.handle(get("/foo").build, null).toBlocking.first
    response.status.code should be(201)
  }


  it ("Builds StaticResponseHandler configurations.") {
    val configuration = new StyxConfig(
        """
          |httpPipeline:
          |  name: Main Pipeline
          |  type: InterceptorPipeline
          |  config:
          |    handler:
          |      type: StaticResponseHandler
          |      config:
          |        status: 201
          |        response: "secure"
        """.stripMargin)

    val interceptors: Supplier[lang.Iterable[NamedPlugin]] = pluginsSupplier(
      namedPlugin("plugA", new PluginAdapter((request, chain) => chain.proceed(request).map(response => response.newBuilder().addHeader("X-Test-Header", "A").build()))),
      namedPlugin("plugB", new PluginAdapter((request, chain) => chain.proceed(request).map(response => response.newBuilder().addHeader("X-Test-Header", "A").build())))
    )

    val handler: HttpHandler2 = new UserConfiguredPipelineFactory(buildEnvironment(configuration), configuration, interceptors, registries).build

    val response: HttpResponse = handler.handle(get("/foo").build, null).toBlocking.first
    response.status.code should be(201)
  }


  it ("Builds ConditionRouterConfig configurations.") {
    val configuration = new StyxConfig(
        """
          |httpPipeline:
          |  name: MainPipeline
          |  type: InterceptorPipeline
          |  config:
          |    handler:
          |      type: ConditionRouter
          |      config:
          |        routes:
          |          - condition: protocol() == "https"
          |            destination:
          |              name: proxy-and-log-to-https
          |              type: StaticResponseHandler
          |              config:
          |                status: 200
          |                response: secure
          |        fallback:
          |          name: proxy-to-http
          |          type: StaticResponseHandler
          |          config:
          |            status: 301
          |            response: insecure
        """.stripMargin)

    val handler: HttpHandler2 = new UserConfiguredPipelineFactory(buildEnvironment(configuration), configuration, pluginsSupplier(), registries).build

    val httpsResponse: HttpResponse = handler.handle(get("/foo").secure(true).build, null).toBlocking.first
    httpsResponse.status.code should be(200)

    val httpResponse: HttpResponse = handler.handle(get("/foo").secure(false).build, null).toBlocking.first
    httpResponse.status.code should be(301)
  }

  // TODO: Needs to be integration tested:
  ignore ("Builds BackendServiceProxy configurations") {
    val configuration = new StyxConfig(
        """
          |httpPipeline:
          |  name: MainPipeline
          |  type: InterceptorPipeline
          |  config:
          |    handler:
          |      type: BackendServiceProxy
          |      config:
          |        backendProvider: backend_apps
        """.stripMargin)

    val registries: util.Map[String, StyxService] = ImmutableMap.of("backend_apps", backendRegistry(newBackendServiceBuilder.path("/").build))

    val handler: HttpHandler2 = new UserConfiguredPipelineFactory(buildEnvironment(configuration), configuration, pluginsSupplier(), registries).build

    val httpsResponse: HttpResponse = handler.handle(get("/").secure(true).build, null).toBlocking.first
    httpsResponse.status.code should be(200)
  }

  it ("Builds HttpInterceptorPipeline configurations") {
    val configuration = new StyxConfig(
        """
          |httpPipeline:
          |  name: MainPipeline
          |  type: InterceptorPipeline
          |  config:
          |    pipeline:
          |      - plugA
          |    handler:
          |      type: InterceptorPipeline
          |      config:
          |        pipeline:
          |          - plugB
          |        handler:
          |          type: StaticResponseHandler
          |          config:
          |            status: 201
          |            response: "secure"
        """.stripMargin)


    val interceptors: Supplier[lang.Iterable[NamedPlugin]] = pluginsSupplier(
      namedPlugin("plugA", new PluginAdapter((request, chain) => chain.proceed(request).map(response => response.newBuilder().addHeader("X-Test-Header", "A").build()))),
      namedPlugin("plugB", new PluginAdapter((request, chain) => chain.proceed(request).map(response => response.newBuilder().addHeader("X-Test-Header", "B").build())))
    )

    val handler: HttpHandler2 = new UserConfiguredPipelineFactory(buildEnvironment(configuration), configuration, interceptors, ImmutableMap.of()).build

    val response: HttpResponse = handler.handle(get("/").secure(true).build, null).toBlocking.first
    response.status.code should be(201)
    response.headers("X-Test-Header").asScala should be(Seq("B", "A"))
  }


  def pluginsSupplier(plugins: NamedPlugin*): Supplier[java.lang.Iterable[NamedPlugin]] = new Supplier[lang.Iterable[NamedPlugin]] {
    override def get(): lang.Iterable[NamedPlugin] = plugins.toList.asJava
  }

  def routingObjectFactory(): BuiltinHandlersFactory = {
    val handlerFactory = mock[HttpHandlerFactory]
    when(handlerFactory.build(any[java.util.List[String]], any[BuiltinHandlersFactory], any[RoutingConfigDefinition]))
      .thenReturn(new HttpHandlerAdapter(_ => Observable.just(HttpResponse.Builder.response(OK).build())))

    new BuiltinHandlersFactory(Map("BackendServiceProxy" -> handlerFactory).asJava)
  }

  def buildEnvironment(styxConfig: StyxConfig): Environment = {
    new Environment.Builder()
      .aggregatedConfiguration(new AggregatedConfiguration(styxConfig))
      .metricsRegistry(new CodaHaleMetricRegistry)
      .healthChecksRegistry(new HealthCheckRegistry)
      .buildInfo(Version.newVersion())
      .eventBus(new AsyncEventBus("styx", newSingleThreadExecutor)).build
  }

  def backendRegistry(backends: BackendService*) = new AbstractRegistry[BackendService]("backend-registry") {
    snapshot.set(backends.asJava)
    override def reload(): CompletableFuture[ReloadResult] = {
      notifyListeners(
        new Changes.Builder[BackendService]()
          .added(backends:_*)
          .build())
      completedFuture(ReloadResult.reloaded("ok"))
    }
  }

}
