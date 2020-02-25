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
package com.hotels.styx.routing.handlers2

import com.fasterxml.jackson.annotation.JsonProperty
import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpHeaders
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.HttpResponse
import com.hotels.styx.api.HttpResponseStatus.statusWithCode
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.config.schema.SchemaDsl.`object`
import com.hotels.styx.config.schema.SchemaDsl.field
import com.hotels.styx.config.schema.SchemaDsl.integer
import com.hotels.styx.config.schema.SchemaDsl.list
import com.hotels.styx.config.schema.SchemaDsl.optional
import com.hotels.styx.config.schema.SchemaDsl.string
import com.hotels.styx.routing.RoutingObject
import com.hotels.styx.routing.config.Builtins
import com.hotels.styx.routing.config2.StyxObject
import java.nio.charset.StandardCharsets.UTF_8

private class StaticResponseObject(val response: HttpResponse): RoutingObject {
    override fun handle(request: LiveHttpRequest, context: HttpInterceptor.Context) = Eventual.of(response.stream())
}

val staticResponseDescriptor = Builtins.StyxObjectDescriptor<StyxObject<RoutingObject>>(
        "StaticResponse",
        `object`(
                field("status", integer()),
                optional("content", string()),
                optional("headers", list(`object`(
                        field("name", string()),
                        field("value", string())
                )))
        ),
        StaticResponse::class.java)


internal data class StaticResponse(
        @JsonProperty val status: Int,
        @JsonProperty val content: String = "",
        @JsonProperty val headers: List<StaticResponseHeader> = listOf()) : StyxObject<RoutingObject> {

    override fun type() = staticResponseDescriptor.type()

    override fun build(context: StyxObject.Context): RoutingObject {
        val response = HttpResponse.response(statusWithCode(status))
                .body(content, UTF_8)
                .headers(httpHeaders(headers))
                .build()

        return StaticResponseObject(response)
    }

    private fun httpHeaders(headers: List<StaticResponseHeader>): HttpHeaders {
        val headersBuilder = HttpHeaders.Builder()

        headers.forEach { headersBuilder.add(it.name, it.value) }

        return headersBuilder.build()
    }
}


internal data class StaticResponseHeader(
        @JsonProperty val name: String,
        @JsonProperty val value: String)
