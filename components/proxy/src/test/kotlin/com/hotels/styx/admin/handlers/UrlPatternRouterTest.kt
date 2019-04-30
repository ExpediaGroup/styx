package com.hotels.styx.admin.handlers

import ch.qos.logback.classic.Level
import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.HttpResponseStatus
import com.hotels.styx.api.HttpResponseStatus.ACCEPTED
import com.hotels.styx.api.HttpResponseStatus.CREATED
import com.hotels.styx.api.HttpResponseStatus.NO_CONTENT
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpRequest.post
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.api.LiveHttpResponse.response
import com.hotels.styx.server.HttpInterceptorContext
import com.hotels.styx.support.matchers.LoggingTestSupport
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import reactor.core.publisher.Mono
import java.util.Optional
import java.util.concurrent.atomic.AtomicReference

class UrlPatternRouterTest : FeatureSpec({
    val LOGGER = LoggingTestSupport(UrlPatternRouter::class.java);

    val router = UrlPatternRouter.Builder()
            .get("/admin/apps/:appId") { request, context -> Eventual.of(
                    response(OK)
                            .header("appId", UrlPatternRouter.placeholders(context)["appId"])
                            .build())
            }
            .get("/admin/apps/:appId/origin/:originId") { request, context -> Eventual.of(
                    response(OK)
                            .header("appId", UrlPatternRouter.placeholders(context)["appId"])
                            .header("originId", UrlPatternRouter.placeholders(context)["originId"])
                            .build())
            }
            .post("/admin/apps/:appId") { request, context -> Eventual.of(
                    response(CREATED)
                            .header("appId", UrlPatternRouter.placeholders(context)["appId"])
                            .build()
            )}
            .post("/admin/apps/:appId/origin/:originId") { request, context -> Eventual.of(
                    response(CREATED)
                            .header("appId", UrlPatternRouter.placeholders(context)["appId"])
                            .header("originId", UrlPatternRouter.placeholders(context)["originId"])
                            .build()
            )}
            .put("/admin/apps/:appId") { request, context -> Eventual.of(
                    response(NO_CONTENT)
                            .header("appId", UrlPatternRouter.placeholders(context)["appId"])
                            .build()
            )}
            .put("/admin/apps/:appId/origin/:originId") { request, context -> Eventual.of(
                    response(NO_CONTENT)
                            .header("appId", UrlPatternRouter.placeholders(context)["appId"])
                            .header("originId", UrlPatternRouter.placeholders(context)["originId"])
                            .build()
            )}
            .delete("/admin/apps/:appId") { request, context -> Eventual.of(
                    response(ACCEPTED)
                            .header("appId", UrlPatternRouter.placeholders(context)["appId"])
                            .build()
            )}
            .delete("/admin/apps/:appId/origin/:originId") { request, context -> Eventual.of(
                    response(ACCEPTED)
                            .header("appId", UrlPatternRouter.placeholders(context)["appId"])
                            .header("originId", UrlPatternRouter.placeholders(context)["originId"])
                            .build()
            )}
            .build()


    feature("Request routing") {
        scenario("GET requests") {
            val response1 = Mono.from(
                    router.handle(LiveHttpRequest.get("/admin/apps/234").build(), HttpInterceptorContext.create())
                            .flatMap { it.aggregate(10000) })
                    .block()

            response1!!.status() shouldBe OK
            response1.header("appId") shouldBe Optional.of("234")
            response1.header("originId") shouldBe Optional.empty()

            val response2 = Mono.from(
                    router.handle(LiveHttpRequest.get("/admin/apps/234/origin/123").build(), HttpInterceptorContext.create())
                            .flatMap { it.aggregate(10000) })
                    .block()

            response2!!.status() shouldBe OK
            response2.header("appId") shouldBe Optional.of("234")
            response2.header("originId") shouldBe Optional.of("123")
        }

        scenario("POST requests") {
            val response1 = Mono.from(
                    router.handle(LiveHttpRequest.post("/admin/apps/234").build(), HttpInterceptorContext.create())
                            .flatMap { it.aggregate(10000) })
                    .block()

            response1!!.status() shouldBe CREATED
            response1.header("appId") shouldBe Optional.of("234")
            response1.header("originId") shouldBe Optional.empty()

            val response2 = Mono.from(
                    router.handle(LiveHttpRequest.post("/admin/apps/234/origin/123").build(), HttpInterceptorContext.create())
                            .flatMap { it.aggregate(10000) })
                    .block()

            response2!!.status() shouldBe CREATED
            response2.header("appId") shouldBe Optional.of("234")
            response2.header("originId") shouldBe Optional.of("123")
        }

        scenario("PUT requests") {
            val response1 = Mono.from(
                    router.handle(LiveHttpRequest.put("/admin/apps/234").build(), HttpInterceptorContext.create())
                            .flatMap { it.aggregate(10000) })
                    .block()

            response1!!.status() shouldBe NO_CONTENT
            response1.header("appId") shouldBe Optional.of("234")
            response1.header("originId") shouldBe Optional.empty()

            val response2 = Mono.from(
                    router.handle(LiveHttpRequest.put("/admin/apps/234/origin/123").build(), HttpInterceptorContext.create())
                            .flatMap { it.aggregate(10000) })
                    .block()

            response2!!.status() shouldBe NO_CONTENT
            response2.header("appId") shouldBe Optional.of("234")
            response2.header("originId") shouldBe Optional.of("123")
        }

        scenario("DELETE requests") {
            val response1 = Mono.from(
                    router.handle(LiveHttpRequest.delete("/admin/apps/234").build(), HttpInterceptorContext.create())
                            .flatMap { it.aggregate(10000) })
                    .block()

            response1!!.status() shouldBe ACCEPTED
            response1.header("appId") shouldBe Optional.of("234")
            response1.header("originId") shouldBe Optional.empty()

            val response2 = Mono.from(
                    router.handle(LiveHttpRequest.delete("/admin/apps/234/origin/123").build(), HttpInterceptorContext.create())
                            .flatMap { it.aggregate(10000) })
                    .block()

            response2!!.status() shouldBe ACCEPTED
            response2.header("appId") shouldBe Optional.of("234")
            response2.header("originId") shouldBe Optional.of("123")
        }

    }


    feature("Route Callbacks Invocation") {
        scenario("Catches and logs user exceptions") {
            val contextCapture = AtomicReference<HttpInterceptor.Context>()

            val router = UrlPatternRouter.Builder()
                    .post("/admin/apps/:appId/:originId") { request, context -> throw RuntimeException("Something went wrong") }
                    .build()

            val response = Mono.from(
                    router.handle(post("/admin/apps/appx/appx-01").build(), HttpInterceptorContext.create())
                            .flatMap { it.aggregate(10000) })
                    .block()

            response!!.status() shouldBe HttpResponseStatus.INTERNAL_SERVER_ERROR

            LOGGER.lastMessage().level shouldBe Level.ERROR
            LOGGER.lastMessage().formattedMessage shouldBe "ERROR: POST /admin/apps/appx/appx-01"
        }
    }

    feature("Placeholders") {
        scenario("Are exposed in HTTP context") {
            val contextCapture = AtomicReference<HttpInterceptor.Context>()

            val router = UrlPatternRouter.Builder()
                    .post("/admin/apps/:appId/:originId") { request, context ->
                        contextCapture.set(context)
                        Eventual.of<LiveHttpResponse>(response(OK).build())
                    }
                    .build()

            val response = Mono.from(
                    router.handle(post("/admin/apps/appx/appx-01").build(), HttpInterceptorContext.create())
                            .flatMap { it.aggregate(10000) })
                    .block()


            response!!.status() shouldBe OK

            val placeholders = UrlPatternRouter.placeholders(contextCapture.get())
            placeholders["appId"] shouldBe "appx"
            placeholders["originId"] shouldBe "appx-01"
        }
    }
})