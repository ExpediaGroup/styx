/*
  Copyright (C) 2013-2023 Expedia Inc.

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

import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.HttpResponse
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.HttpResponseStatus.SERVICE_UNAVAILABLE
import com.hotels.styx.requestContext
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.kotlin.core.publisher.toMono
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.text.Charsets.UTF_8

class ReadinessHandlerTest : StringSpec() {
    init {
        "Handler sends correct body when not ready yet" {
            val readinessHandler = ReadinessHandler { false }

            readinessHandler.handle(HttpRequest.get("/admin/readiness").build(), requestContext()).await().apply {
                status() shouldBe SERVICE_UNAVAILABLE
                bodyAs(UTF_8) shouldBe "{\"ready\":false}\n"
            }
        }

        "Handler sends correct body when ready" {
            val readinessHandler = ReadinessHandler { true }

            readinessHandler.handle(HttpRequest.get("/admin/readiness").build(), requestContext()).await().apply {
                status() shouldBe OK
                bodyAs(UTF_8) shouldBe "{\"ready\":true}\n"
            }
        }

        "Handler changes content when readiness changes" {
            val ready = AtomicBoolean(false)
            val readinessHandler = ReadinessHandler { ready.get() }

            readinessHandler.handle(HttpRequest.get("/admin/readiness").build(), requestContext()).await().apply {
                status() shouldBe SERVICE_UNAVAILABLE
                bodyAs(UTF_8) shouldBe "{\"ready\":false}\n"
            }
            ready.set(true)

            readinessHandler.handle(HttpRequest.get("/admin/readiness").build(), requestContext()).await().apply {
                status() shouldBe OK
                bodyAs(UTF_8) shouldBe "{\"ready\":true}\n"
            }
        }
    }

    private fun Eventual<HttpResponse>.await(): HttpResponse = toMono().block()!!
}
