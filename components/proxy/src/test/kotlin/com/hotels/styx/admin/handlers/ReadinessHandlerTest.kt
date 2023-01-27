package com.hotels.styx.admin.handlers

import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.HttpResponse
import com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.HttpResponseStatus.SERVICE_UNAVAILABLE
import com.hotels.styx.requestContext
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import reactor.core.publisher.toMono
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.text.Charsets.UTF_8

class ReadinessHandlerTest : StringSpec() {
    init {
        "indicates readiness" {
            val ready = AtomicBoolean(false)
            val readinessHandler = ReadinessHandler { ready.get() }

            readinessHandler.handle(HttpRequest.get("/admin/readiness").build(), requestContext()).await().apply {
                status() shouldBe SERVICE_UNAVAILABLE
                bodyAs(UTF_8) shouldBe "{\"ready\":\"false\"}\n"
            }
            ready.set(true)

            readinessHandler.handle(HttpRequest.get("/admin/readiness").build(), requestContext()).await().apply {
                status() shouldBe OK
                bodyAs(UTF_8) shouldBe "{\"ready\":\"true\"}\n"
            }
        }
    }

    private fun Eventual<HttpResponse>.await(): HttpResponse = toMono().block()!!
}
