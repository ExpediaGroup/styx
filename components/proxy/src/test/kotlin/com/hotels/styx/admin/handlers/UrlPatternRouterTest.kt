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
package com.hotels.styx.admin.handlers

import ch.qos.logback.classic.Level
import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.HttpRequest.post
import com.hotels.styx.api.HttpResponse
import com.hotels.styx.api.HttpResponse.response
import com.hotels.styx.api.HttpResponseStatus.ACCEPTED
import com.hotels.styx.api.HttpResponseStatus.CREATED
import com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR
import com.hotels.styx.api.HttpResponseStatus.NO_CONTENT
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.requestContext
import com.hotels.styx.support.matchers.LoggingTestSupport
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import reactor.core.publisher.toMono
import java.util.Optional
import java.util.concurrent.atomic.AtomicReference

class UrlPatternRouterTest : FeatureSpec({
    val LOGGER = LoggingTestSupport(UrlPatternRouter::class.java);

    val router = UrlPatternRouter.Builder()
            .get("/admin/apps/:appId") { request, context ->
                Eventual.of(
                        response(OK)
                                .header("appId", UrlPatternRouter.placeholders(context)["appId"])
                                .build())
            }
            .get("/admin/apps/:appId/origin/:originId") { request, context ->
                Eventual.of(
                        response(OK)
                                .header("appId", UrlPatternRouter.placeholders(context)["appId"])
                                .header("originId", UrlPatternRouter.placeholders(context)["originId"])
                                .build())
            }
            .post("/admin/apps/:appId") { request, context ->
                Eventual.of(
                        response(CREATED)
                                .header("appId", UrlPatternRouter.placeholders(context)["appId"])
                                .build()
                )
            }
            .post("/admin/apps/:appId/origin/:originId") { request, context ->
                Eventual.of(
                        response(CREATED)
                                .header("appId", UrlPatternRouter.placeholders(context)["appId"])
                                .header("originId", UrlPatternRouter.placeholders(context)["originId"])
                                .build()
                )
            }
            .put("/admin/apps/:appId") { request, context ->
                Eventual.of(
                        response(NO_CONTENT)
                                .header("appId", UrlPatternRouter.placeholders(context)["appId"])
                                .build()
                )
            }
            .put("/admin/apps/:appId/origin/:originId") { request, context ->
                Eventual.of(
                        response(NO_CONTENT)
                                .header("appId", UrlPatternRouter.placeholders(context)["appId"])
                                .header("originId", UrlPatternRouter.placeholders(context)["originId"])
                                .build()
                )
            }
            .delete("/admin/apps/:appId") { request, context ->
                Eventual.of(
                        response(ACCEPTED)
                                .header("appId", UrlPatternRouter.placeholders(context)["appId"])
                                .build()
                )
            }
            .delete("/admin/apps/:appId/origin/:originId") { request, context ->
                Eventual.of(
                        response(ACCEPTED)
                                .header("appId", UrlPatternRouter.placeholders(context)["appId"])
                                .header("originId", UrlPatternRouter.placeholders(context)["originId"])
                                .build()
                )
            }
            .build()


    feature("Request routing") {
        scenario("GET requests") {
            val response1 = router.handle(HttpRequest.get("/admin/apps/234").build(), requestContext())
                    .toMono()
                    .block()

            response1!!.status() shouldBe OK
            response1.header("appId") shouldBe Optional.of("234")
            response1.header("originId") shouldBe Optional.empty()

            val response2 = router.handle(HttpRequest.get("/admin/apps/234/origin/123").build(), requestContext())
                    .toMono()
                    .block()

            response2!!.status() shouldBe OK
            response2.header("appId") shouldBe Optional.of("234")
            response2.header("originId") shouldBe Optional.of("123")
        }

        scenario("POST requests") {
            val response1 = router.handle(HttpRequest.post("/admin/apps/234").build(), requestContext())
                    .toMono()
                    .block()

            response1!!.status() shouldBe CREATED
            response1.header("appId") shouldBe Optional.of("234")
            response1.header("originId") shouldBe Optional.empty()

            val response2 = router.handle(HttpRequest.post("/admin/apps/234/origin/123").build(), requestContext())
                    .toMono()
                    .block()

            response2!!.status() shouldBe CREATED
            response2.header("appId") shouldBe Optional.of("234")
            response2.header("originId") shouldBe Optional.of("123")
        }

        scenario("PUT requests") {
            val response1 = router.handle(HttpRequest.put("/admin/apps/234").build(), requestContext())
                    .toMono()
                    .block()

            response1!!.status() shouldBe NO_CONTENT
            response1.header("appId") shouldBe Optional.of("234")
            response1.header("originId") shouldBe Optional.empty()

            val response2 = router.handle(HttpRequest.put("/admin/apps/234/origin/123").build(), requestContext())
                    .toMono()
                    .block()

            response2!!.status() shouldBe NO_CONTENT
            response2.header("appId") shouldBe Optional.of("234")
            response2.header("originId") shouldBe Optional.of("123")
        }

        scenario("DELETE requests") {
            val response1 = router.handle(HttpRequest.delete("/admin/apps/234").build(), requestContext())
                    .toMono()
                    .block()

            response1!!.status() shouldBe ACCEPTED
            response1.header("appId") shouldBe Optional.of("234")
            response1.header("originId") shouldBe Optional.empty()

            val response2 = router.handle(HttpRequest.delete("/admin/apps/234/origin/123").build(), requestContext())
                    .toMono()
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

            val response = router.handle(post("/admin/apps/appx/appx-01").build(), requestContext())
                    .toMono()
                    .block()

            response!!.status() shouldBe INTERNAL_SERVER_ERROR

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
                        Eventual.of<HttpResponse>(response(OK).build())
                    }
                    .build()

            val response = router.handle(post("/admin/apps/appx/appx-01").build(), requestContext())
                    .toMono()
                    .block()

            response!!.status() shouldBe OK

            val placeholders = UrlPatternRouter.placeholders(contextCapture.get())
            placeholders["appId"] shouldBe "appx"
            placeholders["originId"] shouldBe "appx-01"
        }
    }
})