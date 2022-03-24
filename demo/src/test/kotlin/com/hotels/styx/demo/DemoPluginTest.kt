/*
  Copyright (C) 2013-2021 Expedia Inc.

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
package com.hotels.styx.demo

import com.hotels.styx.api.*
import com.hotels.styx.api.HttpResponseStatus.OK
import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.FeatureSpec
import io.mockk.mockk
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets.UTF_8
import kotlin.test.assertFailsWith

class DemoPluginTest : FeatureSpec({
    feature("Pass-through") {
        val plugin = DemoPluginFactory().create(mockk())

        val response = plugin.intercept(requestGet("/"), DummyChain).await()

        response.status() shouldBe OK
        response.bodyAs(UTF_8) shouldBe "Expected response"
    }

    feature("Exception on request") {
        val plugin = DemoPluginFactory().create(mockk())

        assertFailsWith<DemoPluginException> {
            plugin.intercept(requestGet("/?exception=onRequest"), DummyChain).await()
        }
    }

    feature("Exception on response") {
        val plugin = DemoPluginFactory().create(mockk())

        assertFailsWith<DemoPluginException> {
            plugin.intercept(requestGet("/?exception=onResponse"), DummyChain).await()
        }
    }

    feature("Admin Interface") {
        val plugin = DemoPluginFactory().create(mockk())

        val adminResponse = plugin.adminInterfaceHandlers()["hits"]!!.handle(requestGet("/"), mockk()).await()
        adminResponse.bodyAs(UTF_8) shouldBe "Hits: 0"

        plugin.intercept(requestGet("/"), DummyChain).await()

        val newAdminResponse = plugin.adminInterfaceHandlers()["hits"]!!.handle(requestGet("/"), mockk()).await()
        newAdminResponse.bodyAs(UTF_8) shouldBe "Hits: 1"
    }
})

object DummyChain : HttpInterceptor.Chain {
    override fun proceed(request: LiveHttpRequest?): Eventual<LiveHttpResponse> =
        Eventual.of(response {
            body("Expected response", UTF_8)
        })
}

private fun requestGet(path: String) = HttpRequest.get(path).build().stream()

private fun response(block: HttpResponse.Builder.() -> Unit): LiveHttpResponse {
    val response = HttpResponse.response()
    block(response)
    return response.build().stream()
}

private fun Eventual<LiveHttpResponse>.await(): HttpResponse {
    try {
        return Mono.from(flatMap {
            it.aggregate(100000)
        }).block()!!
    } catch (e: Exception) {
        throw if (e.cause is DemoPluginException)
            e.cause!!
        else
            e
    }
}

