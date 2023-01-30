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
import com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE
import com.hotels.styx.api.HttpHeaderValues.APPLICATION_JSON
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.HttpResponse
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.HttpResponseStatus.SERVICE_UNAVAILABLE
import com.hotels.styx.api.WebServiceHandler
import kotlin.text.Charsets.UTF_8

/**
 * Used to indicate that Styx is ready to accept traffic. How readiness is determined is configurable -
 * the handler is only responsible for providing a JSON response that indicates the readiness state.
 */
class ReadinessHandler(val readiness: () -> Boolean) : WebServiceHandler {
    override fun handle(request: HttpRequest, context: HttpInterceptor.Context): Eventual<HttpResponse> {
        val ready = readiness()

        return response {
            status(if (ready) OK else SERVICE_UNAVAILABLE)
            disableCaching()
            header(CONTENT_TYPE, APPLICATION_JSON)
            body(jsonObject("ready", ready), UTF_8)
        }
    }

    private fun response(lambda: HttpResponse.Builder.() -> Unit) = Eventual.of(HttpResponse.response().apply(lambda).build())

    private fun jsonObject(key: String, value: Any) = "{\"$key\":\"$value\"}\n"
}
