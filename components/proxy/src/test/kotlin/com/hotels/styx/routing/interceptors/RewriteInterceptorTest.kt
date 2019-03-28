package com.hotels.styx.routing.interceptors

import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.api.LiveHttpResponse.response
import com.hotels.styx.routing.configBlock
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import reactor.core.publisher.Mono

class RewriteInterceptorTest : StringSpec({

    "performs replacement" {
        val config = configBlock("""
            config:
                name: rewrite
                type: Rewrite
                config:
                    - urlPattern:  /prefix/(.*)
                      replacement: /app/$1
                    - urlPattern:  /(.*)
                      replacement: /app/$1
        """.trimIndent())

        val interceptor = RewriteInterceptor.ConfigFactory().build(config)
        val capturingChain = CapturingChain()

        val response = Mono.from(interceptor.intercept(LiveHttpRequest.get("/foo").build(), capturingChain)).block()
        capturingChain.request()?.path() shouldBe ("/app/foo")
    }


    "Empty config block does nothing" {
        val config = configBlock("""
            config:
                name: rewrite
                type: Rewrite
                config:
        """.trimIndent())

        val interceptor = RewriteInterceptor.ConfigFactory().build(config)
        val capturingChain = CapturingChain()

        val response = Mono.from(interceptor.intercept(LiveHttpRequest.get("/foo").build(), capturingChain)).block()
        capturingChain.request()?.path() shouldBe ("/foo")
    }

})


class CapturingChain : HttpInterceptor.Chain {
    var storedRequest: LiveHttpRequest? = null

    override fun proceed(request: LiveHttpRequest): Eventual<LiveHttpResponse> {
        storedRequest = request
        return Eventual.of(response(OK).build())
    }

    fun request() = storedRequest
}
