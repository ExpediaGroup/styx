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