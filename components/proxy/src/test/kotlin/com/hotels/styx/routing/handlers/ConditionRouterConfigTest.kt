package com.hotels.styx.routing.handlers

import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse.response
import com.hotels.styx.routing.config.RouteHandlerFactory
import com.hotels.styx.routing.configBlock
import com.hotels.styx.server.HttpInterceptorContext
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import reactor.core.publisher.Mono

class ConditionRouterConfigTest : StringSpec({

    val request = LiveHttpRequest.get("/foo").build()
    val routeHandlerFactory = RouteHandlerFactory(
            mapOf("StaticResponseHandler" to StaticResponseHandler.ConfigFactory()),
            mapOf())

    val config = configBlock("""
          config:
              name: main-router
              type: ConditionRouter
              config:
                routes:
                  - condition: protocol() == "https"
                    destination:
                      name: proxy-and-log-to-https
                      type: StaticResponseHandler
                      config:
                        status: 200
                        content: "secure"
                fallback:
                  name: proxy-to-http
                  type: StaticResponseHandler
                  config:
                    status: 301
                    content: "insecure"
          """.trimIndent())

    val configWithReferences = configBlock("""
          config:
              name: main-router
              type: ConditionRouter
              config:
                routes:
                  - condition: protocol() == "https"
                    destination: secureHandler
                fallback: fallbackHandler
          """.trimIndent())


    "Builds an instance with fallback handler" {
        val router = ConditionRouter.ConfigFactory().build(listOf(), routeHandlerFactory, config)
        val response = Mono.from(router.handle(request, HttpInterceptorContext(true))).block()

        response.status() shouldBe (OK)
    }

    "Builds condition router instance routes" {
        val router = ConditionRouter.ConfigFactory().build(listOf(), routeHandlerFactory, config)
        val response = Mono.from(router.handle(request, HttpInterceptorContext())).block()

        response.status().code() shouldBe (301)
    }


    "Fallback handler can be specified as a handler reference" {
        val routeHandlerFactory = RouteHandlerFactory(
                mapOf(),
                mapOf("secureHandler" to HttpHandler { _, _ -> Eventual.of(response(OK).header("source", "secure").build()) },
                        "fallbackHandler" to HttpHandler { _, _ -> Eventual.of(response(OK).header("source", "fallback").build()) }))

        val router = ConditionRouter.ConfigFactory().build(listOf(), routeHandlerFactory, configWithReferences)

        val resp = Mono.from(router.handle(request, HttpInterceptorContext())).block()

        resp.header("source").get() shouldBe ("fallback")
    }

    "Route destination can be specified as a handler reference" {
        val routeHandlerFactory = RouteHandlerFactory(
                mapOf(),
                mapOf("secureHandler" to HttpHandler { _, _ -> Eventual.of(response(OK).header("source", "secure").build()) },
                        "fallbackHandler" to HttpHandler { _, _ -> Eventual.of(response(OK).header("source", "fallback").build()) }))

        val router = ConditionRouter.ConfigFactory().build(
                listOf(),
                routeHandlerFactory,
                configWithReferences
        )

        val resp = Mono.from(router.handle(request, HttpInterceptorContext(true))).block()
        resp.header("source").get() shouldBe ("secure")
    }


    "Throws exception when routes attribute is missing" {
        val config = configBlock("""
        config:
            name: main-router
            type: ConditionRouter
            config:
              fallback:
                name: proxy-to-http
                type: StaticResponseHandler
                config:
                  status: 301
                  content: "insecure"
        """.trimIndent())

        val e = shouldThrow<IllegalArgumentException> {
            ConditionRouter.ConfigFactory().build(listOf("config", "config"), routeHandlerFactory, config)
        }
        e.message shouldBe ("Routing object definition of type 'ConditionRouter', attribute='config.config', is missing a mandatory 'routes' attribute.")
    }

    "Responds with 502 Bad Gateway when fallback attribute is not specified." {
        val config = configBlock("""
            config:
                name: main-router
                type: ConditionRouter
                config:
                  routes:
                    - condition: protocol() == "https"
                      destination:
                        name: proxy-and-log-to-https
                        type: StaticResponseHandler
                        config:
                          status: 200
                          content: "secure"
        """.trimIndent())

        val router = ConditionRouter.ConfigFactory().build(listOf(), routeHandlerFactory, config)

        val resp = Mono.from(router.handle(request, HttpInterceptorContext())).block()

        resp.status() shouldBe (BAD_GATEWAY)
    }

    "Indicates the condition when fails to compile an DSL expression due to Syntax Error" {
        val config = configBlock("""
                config:
                    name: main-router
                    type: ConditionRouter
                    config:
                      routes:
                        - condition: )() == "https"
                          destination:
                            name: proxy-and-log-to-https
                            type: StaticResponseHandler
                            config:
                              status: 200
                              content: "secure"
        """.trimIndent())

        val e = shouldThrow<IllegalArgumentException> {
            ConditionRouter.ConfigFactory().build(listOf("config", "config"), routeHandlerFactory, config)
        }
        e.message shouldBe ("Routing object definition of type 'ConditionRouter', attribute='config.config.routes.condition[0]', failed to compile routing expression condition=')() == \"https\"'")
    }

    "Indicates the condition when fails to compile an DSL expression due to unrecognised DSL function name" {
        val config = configBlock("""
            config:
                name: main-router
                type: ConditionRouter
                config:
                  routes:
                    - condition: nonexistant() == "https"
                      destination:
                        name: proxy-and-log-to-https
                        type: StaticResponseHandler
                        config:
                          status: 200
                          content: "secure"
            """.trimIndent())

        val e = shouldThrow<IllegalArgumentException> {
            ConditionRouter.ConfigFactory().build(listOf("config", "config"), routeHandlerFactory, config)
        }
        e.message shouldBe("Routing object definition of type 'ConditionRouter', attribute='config.config.routes.condition[0]', failed to compile routing expression condition='nonexistant() == \"https\"'")
    }

    "Passes parentage attribute path to the builtins factory" {
        val config = configBlock("""
            config:
                name: main-router
                type: ConditionRouter
                config:
                  routes:
                    - condition: protocol() == "https"
                      destination:
                        name: proxy-and-log-to-https
                        type: StaticResponseHandler
                        config:
                          status: 200
                          content: "secure"
                    - condition: path() == "bar"
                      destination:
                        name: proxy-and-log-to-https
                        type: StaticResponseHandler
                        config:
                          status: 200
                          content: "secure"
                  fallback:
                    name: proxy-and-log-to-https
                    type: StaticResponseHandler
                    config:
                      status: 200
                      content: "secure"
            """.trimIndent())

        val builtinsFactory = mockk<RouteHandlerFactory>()
        every {
            builtinsFactory.build(any(), any())
        } returns HttpHandler {_, _ -> Eventual.of(response(OK).build())}

        val router = ConditionRouter.ConfigFactory().build(listOf("config", "config"), builtinsFactory, config)

        verify {
            builtinsFactory.build(listOf("config", "config", "routes", "destination[0]"), any())
            builtinsFactory.build(listOf("config", "config", "routes", "destination[1]"), any())
            builtinsFactory.build(listOf("config", "config", "fallback"), any())
        }
    }

})
