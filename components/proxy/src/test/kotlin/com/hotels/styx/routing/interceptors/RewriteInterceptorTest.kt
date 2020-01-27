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
package com.hotels.styx.routing.interceptors

import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.api.LiveHttpResponse.response
import com.hotels.styx.routingObjectDef
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import reactor.core.publisher.toMono

class RewriteInterceptorTest : StringSpec({

    "performs replacement" {
        val config = routingObjectDef("""
                name: rewrite
                type: Rewrite
                config:
                    - urlPattern:  /prefix/(.*)
                      replacement: /app/$1
                    - urlPattern:  /(.*)
                      replacement: /app/$1
        """.trimIndent())

        val interceptor = RewriteInterceptor.Factory().build(config)
        val capturingChain = CapturingChain()

        interceptor.intercept(LiveHttpRequest.get("/foo").build(), capturingChain).toMono().block()
        capturingChain.request()!!.path() shouldBe ("/app/foo")
    }


    "Empty config block does nothing" {
        val config = routingObjectDef("""
                name: rewrite
                type: Rewrite
                config:
        """.trimIndent())

        val interceptor = RewriteInterceptor.Factory().build(config)
        val capturingChain = CapturingChain()

        interceptor.intercept(LiveHttpRequest.get("/foo").build(), capturingChain).toMono().block()
        capturingChain.request()!!.path() shouldBe ("/foo")
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
