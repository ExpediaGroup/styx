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
package com.hotels.styx.routing.handlers

import com.hotels.styx.api.HttpResponse.response
import com.hotels.styx.api.{HttpHandler, HttpRequest, StyxObservable}
import com.hotels.styx.common.StyxFutures
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig
import com.hotels.styx.routing.HttpHandlerAdapter
import com.hotels.styx.routing.config.{HttpHandlerFactory, RouteHandlerConfig, RouteHandlerDefinition, RouteHandlerFactory}
import com.hotels.styx.server.HttpInterceptorContext
import com.hotels.styx.api.HttpResponseStatus.{BAD_GATEWAY, OK}
import org.mockito.Matchers.{any, eq => meq}
import org.mockito.Mockito.{verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSpec, ShouldMatchers}

import scala.collection.JavaConversions._

class ConditionRouterConfigSpec extends FunSpec with ShouldMatchers with MockitoSugar {

  private val httpsRequest = HttpRequest.get("/foo").secure(true).build
  private val httpRequest = HttpRequest.get("/foo").secure(false).build
  private val routeHandlerFactory = new RouteHandlerFactory(Map("StaticResponseHandler" -> new StaticResponseHandler.ConfigFactory), Map[String, HttpHandler]())

  private val config = configBlock(
    """
      |config:
      |    name: main-router
      |    type: ConditionRouter
      |    config:
      |      routes:
      |        - condition: protocol() == "https"
      |          destination:
      |            name: proxy-and-log-to-https
      |            type: StaticResponseHandler
      |            config:
      |              status: 200
      |              content: "secure"
      |      fallback:
      |        name: proxy-to-http
      |        type: StaticResponseHandler
      |        config:
      |          status: 301
      |          content: "insecure"
      |""".stripMargin)

  val configWithReferences = configBlock(
    """
      |config:
      |    name: main-router
      |    type: ConditionRouter
      |    config:
      |      routes:
      |        - condition: protocol() == "https"
      |          destination: secureHandler
      |      fallback: fallbackHandler
      |""".stripMargin)


  it("Builds an instance with fallback handler") {
    val router = new ConditionRouter.ConfigFactory().build(List(), routeHandlerFactory, config)
    val response = StyxFutures.await(router.handle(httpsRequest, HttpInterceptorContext.create)
    .asCompletableFuture())

    response.status() should be(OK)
  }

  it("Builds condition router instance routes") {
    val router = new ConditionRouter.ConfigFactory().build(List(), routeHandlerFactory, config)
    val response = StyxFutures.await(router.handle(httpRequest, HttpInterceptorContext.create).asCompletableFuture())

    response.status().code() should be(301)
  }

  it("Fallback handler can be specified as a handler reference") {
    val routeHandlerFactory = new RouteHandlerFactory(
      Map[String, HttpHandlerFactory](),
      Map(
      "secureHandler" -> new HttpHandlerAdapter((_, _) => StyxObservable.of(response(OK).header("source", "secure").build())),
      "fallbackHandler" -> new HttpHandlerAdapter((_, _) => StyxObservable.of(response(OK).header("source", "fallback").build()))
    ))

    val router = new ConditionRouter.ConfigFactory().build(List(), routeHandlerFactory, configWithReferences)

    val resp = StyxFutures.await(router.handle(httpRequest, HttpInterceptorContext.create).asCompletableFuture())

    resp.header("source").get() should be("fallback")
  }

  it("Route destination can be specified as a handler reference") {
    val routeHandlerFactory = new RouteHandlerFactory(
      Map[String, HttpHandlerFactory](),
      Map(
        "secureHandler" -> new HttpHandlerAdapter((_, _) => StyxObservable.of(response(OK).header("source", "secure").build())),
        "fallbackHandler" -> new HttpHandlerAdapter((_, _) => StyxObservable.of(response(OK).header("source", "fallback").build()))
      ))

    val router = new ConditionRouter.ConfigFactory().build(
      List(),
      routeHandlerFactory,
      configWithReferences
      )

    val resp = StyxFutures.await(router.handle(httpsRequest, HttpInterceptorContext.create).asCompletableFuture())

    resp.header("source").get() should be("secure")
  }


  it("Throws exception when routes attribute is missing") {
    val config = configBlock(
      """
        |config:
        |    name: main-router
        |    type: ConditionRouter
        |    config:
        |      fallback:
        |        name: proxy-to-http
        |        type: StaticResponseHandler
        |        config:
        |          status: 301
        |          content: "insecure"
        |""".stripMargin)

    val e = intercept[IllegalArgumentException] {
      val router = new ConditionRouter.ConfigFactory().build(List("config", "config"), routeHandlerFactory, config)
    }
    e.getMessage should be("Routing object definition of type 'ConditionRouter', attribute='config.config', is missing a mandatory 'routes' attribute.")
  }

  it("Responds with 502 Bad Gateway when fallback attribute is not specified.") {
    val config = configBlock(
      """
        |config:
        |    name: main-router
        |    type: ConditionRouter
        |    config:
        |      routes:
        |        - condition: protocol() == "https"
        |          destination:
        |            name: proxy-and-log-to-https
        |            type: StaticResponseHandler
        |            config:
        |              status: 200
        |              content: "secure"
        |""".stripMargin)

    val router = new ConditionRouter.ConfigFactory().build(List(), routeHandlerFactory, config)
    val resp = StyxFutures.await(router.handle(httpRequest, HttpInterceptorContext.create).asCompletableFuture())

    resp.status() should be(BAD_GATEWAY)
  }

  it("Indicates the condition when fails to compile an DSL expression due to Syntax Error") {
    val config = configBlock(
      """
        |config:
        |    name: main-router
        |    type: ConditionRouter
        |    config:
        |      routes:
        |        - condition: )() == "https"
        |          destination:
        |            name: proxy-and-log-to-https
        |            type: StaticResponseHandler
        |            config:
        |              status: 200
        |              content: "secure"
        |""".stripMargin)

    val e = intercept[IllegalArgumentException] {
      val router = new ConditionRouter.ConfigFactory().build(List("config", "config"), routeHandlerFactory, config)
    }
    e.getMessage should be("Routing object definition of type 'ConditionRouter', attribute='config.config.routes.condition[0]', failed to compile routing expression condition=')() == \"https\"'")
  }

  it("Indicates the condition when fails to compile an DSL expression due to unrecognised DSL function name") {
    val config = configBlock(
      """
        |config:
        |    name: main-router
        |    type: ConditionRouter
        |    config:
        |      routes:
        |        - condition: nonexistant() == "https"
        |          destination:
        |            name: proxy-and-log-to-https
        |            type: StaticResponseHandler
        |            config:
        |              status: 200
        |              content: "secure"
        |""".stripMargin)

    val e = intercept[IllegalArgumentException] {
      val router = new ConditionRouter.ConfigFactory().build(List("config", "config"), routeHandlerFactory, config)
    }
    e.getMessage should be("Routing object definition of type 'ConditionRouter', attribute='config.config.routes.condition[0]', failed to compile routing expression condition='nonexistant() == \"https\"'")
  }

  it("Passes parentage attribute path to the builtins factory") {
    val config = configBlock(
      """
        |config:
        |    name: main-router
        |    type: ConditionRouter
        |    config:
        |      routes:
        |        - condition: protocol() == "https"
        |          destination:
        |            name: proxy-and-log-to-https
        |            type: StaticResponseHandler
        |            config:
        |              status: 200
        |              content: "secure"
        |        - condition: path() == "bar"
        |          destination:
        |            name: proxy-and-log-to-https
        |            type: StaticResponseHandler
        |            config:
        |              status: 200
        |              content: "secure"
        |      fallback:
        |        name: proxy-and-log-to-https
        |        type: StaticResponseHandler
        |        config:
        |          status: 200
        |          content: "secure"
        |""".stripMargin)

    val builtinsFactory = mock[RouteHandlerFactory]
    when(builtinsFactory.build(any[java.util.List[String]], any[RouteHandlerConfig]))
      .thenReturn(new HttpHandlerAdapter((_, _) => StyxObservable.of(response(OK).build())))

    val router = new ConditionRouter.ConfigFactory().build(List("config", "config"), builtinsFactory, config)

    verify(builtinsFactory).build(meq(List("config", "config", "routes", "destination[0]")), any[RouteHandlerConfig])
    verify(builtinsFactory).build(meq(List("config", "config", "routes", "destination[1]")), any[RouteHandlerConfig])
    verify(builtinsFactory).build(meq(List("config", "config", "fallback")), any[RouteHandlerConfig])
  }

  private def configBlock(text: String) = new YamlConfig(text).get("config", classOf[RouteHandlerDefinition]).get()

}
