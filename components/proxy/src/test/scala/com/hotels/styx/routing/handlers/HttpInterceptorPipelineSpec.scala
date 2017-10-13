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

package com.hotels.styx.routing.handlers

import java.lang
import java.util.function.Supplier

import com.google.common.collect.ImmutableList.{of => list}
import io.netty.handler.codec.http.HttpResponseStatus.OK
import com.hotels.styx.api._
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig
import com.hotels.styx.proxy.plugin.NamedPlugin
import com.hotels.styx.proxy.plugin.NamedPlugin.namedPlugin
import com.hotels.styx.routing.config._
import com.hotels.styx.routing.interceptors.RewriteInterceptor
import com.hotels.styx.routing.{PluginAdapter, HttpHandlerAdapter}
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSpec, ShouldMatchers}
import rx.lang.scala.Observable
import org.mockito.Mockito.verify
import org.mockito.Matchers.{eq => meq}

import scala.collection.JavaConverters._

class HttpInterceptorPipelineSpec extends FunSpec with ShouldMatchers with MockitoSugar {

  val hwaRequest = HttpRequest.Builder.get("/x").build()

  it("errors when there is a reference to non-existing pipeline") {
    val config = configBlock(
      """
        |config:
        |  type: InterceptorPipeline
        |  config:
        |    pipeline:
        |      - non-existing
        |    handler:
        |      name: MyHandler
        |      type: Foo
        |      config:
        |        bar
      """.stripMargin)

    val e = intercept[IllegalArgumentException] {
      new HttpInterceptorPipeline.ConfigFactory(
        interceptors(
          namedPlugin("interceptor1", new PluginAdapter((request, chain) => chain.proceed(request).map(response => response.newBuilder().addHeader("X-Test-Header", "A").build()))),
          namedPlugin("interceptor2", new PluginAdapter((request, chain) => chain.proceed(request).map(response => response.newBuilder().addHeader("X-Test-Header", "A").build())))
        ),
        new BuiltinInterceptorsFactory(Map.empty[String, HttpInterceptorFactory].asJava)
      ).build(list("config"), null, config)
    }

    e.getMessage should be("No such plugin or interceptor exists, attribute='config.pipeline', name='non-existing'")
  }


  it("errors when handler configuration is missing") {
    val config = configBlock(
      """
        |config:
        |  type: InterceptorPipeline
        |  config:
        |    pipeline:
        |      - interceptor2
      """.stripMargin)

    val e = intercept[IllegalArgumentException] {
      new HttpInterceptorPipeline.ConfigFactory(
        interceptors(
          namedPlugin("interceptor1", new PluginAdapter((request, chain) => chain.proceed(request).map(response => response.newBuilder().addHeader("X-Test-Header", "A").build()))),
          namedPlugin("interceptor2", new PluginAdapter((request, chain) => chain.proceed(request).map(response => response.newBuilder().addHeader("X-Test-Header", "A").build())))
        ),
        new BuiltinInterceptorsFactory(Map.empty[String, HttpInterceptorFactory].asJava)
      ).build(list("config"), null, config)
    }

    e.getMessage should be("Routing object definition of type 'InterceptorPipeline', attribute='config', is missing a mandatory 'handler' attribute.")
  }


  it("builds an interceptor pipeline from the configuration") {
    val config = configBlock(
      """
        |config:
        |  type: InterceptorPipeline
        |  config:
        |    pipeline:
        |      - interceptor1
        |      - interceptor2
        |    handler:
        |      name: MyHandler
        |      type: BackendServiceProxy
        |      config:
        |        backendProvider: backendProvider
      """.stripMargin)

    val handler = new HttpInterceptorPipeline.ConfigFactory(
      interceptors(
        namedPlugin("interceptor1", new PluginAdapter((request, chain) => chain.proceed(request).map(response => response.newBuilder().addHeader("X-Test-Header", "A").build()))),
        namedPlugin("interceptor2", new PluginAdapter((request, chain) => chain.proceed(request).map(response => response.newBuilder().addHeader("X-Test-Header", "B").build())))
      ),
      new BuiltinInterceptorsFactory(Map.empty[String, HttpInterceptorFactory].asJava)
    ).build(list("config"), routingObjectFactory(), config)

    val response = handler.handle(hwaRequest, null).toBlocking.first()
    response.headers("X-Test-Header").asScala should be(Seq("B", "A"))
  }

  it("Treats absent 'pipeline' attribute as empty pipeline") {
    val config = configBlock(
      """
        |config:
        |  type: InterceptorPipeline
        |  config:
        |    handler:
        |      name: MyHandler
        |      type: BackendServiceProxy
        |      config:
        |        backendProvider: backendProvider
      """.stripMargin)

    val handler = new HttpInterceptorPipeline.ConfigFactory(interceptors(), new BuiltinInterceptorsFactory(Map.empty[String, HttpInterceptorFactory].asJava)).build(list("config"), routingObjectFactory(), config)

    val response = handler.handle(hwaRequest, null).toBlocking.first()
    response.status() should be (OK)
  }


  it("Supports inline interceptor definitions") {
    val config = configBlock(
      """
        |config:
        |  type: InterceptorPipeline
        |  config:
        |    pipeline:
        |      - interceptor1
        |      - name: rewrite
        |        type: Rewrite
        |        config:
        |        - urlPattern:  /(.*)
        |          replacement: /app/$1
        |      - interceptor2
        |    handler:
        |      name: MyHandler
        |      type: BackendServiceProxy
        |      config:
        |        backendProvider: backendProvider
      """.stripMargin)

    val handler = new HttpInterceptorPipeline.ConfigFactory(
      interceptors(
        namedPlugin("interceptor1", new PluginAdapter((request, chain) => chain.proceed(request).map(response => response.newBuilder().addHeader("X-Test-Header", "A").build()))),
        namedPlugin("interceptor2", new PluginAdapter((request, chain) => chain.proceed(request).map(response => response.newBuilder().addHeader("X-Test-Header", "B").build())))
      ),
      new BuiltinInterceptorsFactory(
        Map[String, HttpInterceptorFactory]("Rewrite" -> new RewriteInterceptor.ConfigFactory()).asJava
      )
    ).build(list("config"), routingObjectFactory(), config)

    val response = handler.handle(hwaRequest, null).toBlocking.first()
    response.headers("X-Test-Header").asScala should be(Seq("B", "A"))
  }


  it("passes full configuration attribute path (config.config.handler) to the builtins factory") {
    val config = configBlock(
      """
        |config:
        |  type: InterceptorPipeline
        |  config:
        |    handler:
        |      name: MyHandler
        |      type: BackendServiceProxy
        |      config:
        |        backendProvider: backendProvider
      """.stripMargin)

    val builtinsFactory = mock[BuiltinHandlersFactory]
    when(builtinsFactory.build(any[java.util.List[String]], any[RoutingConfigNode]))
      .thenReturn(new HttpHandlerAdapter(_ => Observable.just(HttpResponse.Builder.response(OK).build())))

    val handler = new HttpInterceptorPipeline.ConfigFactory(interceptors(), null)
      .build(list("config", "config"), builtinsFactory, config)

    verify(builtinsFactory).build(meq(List("config", "config", "handler").asJava), any[RoutingConfigNode])
  }


  private def configBlock(text: String) = new YamlConfig(text).get("config", classOf[RoutingConfigDefinition]).get()

  def interceptors(plugins: NamedPlugin*): Supplier[java.lang.Iterable[NamedPlugin]] = new Supplier[lang.Iterable[NamedPlugin]] {
    override def get(): lang.Iterable[NamedPlugin] = plugins.toList.asJava
  }

  def mockHandlerFactory(): HttpHandlerFactory = {
    val handlerFactory = mock[HttpHandlerFactory]
    when(handlerFactory.build(any[java.util.List[String]], any[BuiltinHandlersFactory], any[RoutingConfigDefinition]))
      .thenReturn(new HttpHandlerAdapter(_ => Observable.just(HttpResponse.Builder.response(OK).build())))
    handlerFactory
  }

  def routingObjectFactory(): BuiltinHandlersFactory = {
    new BuiltinHandlersFactory(Map("BackendServiceProxy" -> mockHandlerFactory()).asJava)
  }
}
