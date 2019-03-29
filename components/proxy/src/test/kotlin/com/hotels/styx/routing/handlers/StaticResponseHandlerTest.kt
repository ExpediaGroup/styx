package com.hotels.styx.routing.handlers

import com.hotels.styx.api.HttpResponseStatus.CREATED
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.routing.configBlock
import com.hotels.styx.server.HttpInterceptorContext
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import reactor.core.publisher.Mono


class StaticResponseHandlerTest: StringSpec({
    val config = configBlock("""
          config:
              name: proxy-and-log-to-https
              type: StaticResponseHandler
              config:
                  status: 201
                  content: "secure"

          """.trimIndent())

    "builds static response handler" {
        val handler = StaticResponseHandler.ConfigFactory().build(listOf(), null, config)
        val response = Mono.from(handler.handle(LiveHttpRequest.get("/foo").build(), HttpInterceptorContext.create())).block()

        response?.status() shouldBe (CREATED)
    }

})