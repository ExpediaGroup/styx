package com.hotels.styx.plugins

import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.HttpResponse
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.api.plugins.spi.Plugin
import reactor.core.publisher.BaseSubscriber
import reactor.core.publisher.Mono
import java.time.Duration

class DelayPlugin(private val requestProcessingDelay: Duration, private val responseProcessingDelay: Duration) : Plugin {
    override fun intercept(request: LiveHttpRequest, chain: HttpInterceptor.Chain): Eventual<LiveHttpResponse> {
        return requestProcessingDelay.delayedEventual().flatMap {
            chain.proceed(request).flatMap { response ->
                responseProcessingDelay.delayedEventual().map { response }
            }
        }
    }

    private fun Duration.delayedEventual() = Eventual(Mono.delay(this))
}

fun main() {
    println("Begin")
    val chain = HttpInterceptor.Chain {
        println("reached")
        Eventual.of(HttpResponse.response().build().stream())
    }

    val plug = DelayPlugin(Duration.ofMillis(1000), Duration.ofMillis(2000))

    plug.intercept(HttpRequest.get("/").build().stream(), chain).subscribe(
        object : BaseSubscriber<LiveHttpResponse>() {
            override fun hookOnNext(value: LiveHttpResponse) {
                println("response received = ${value.status()}")
            }
        }
    )

    Thread.sleep(5000)
}