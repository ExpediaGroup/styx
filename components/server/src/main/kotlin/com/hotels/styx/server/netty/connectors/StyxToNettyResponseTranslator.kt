/*
  Copyright (C) 2013-2022 Expedia Inc.

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
package com.hotels.styx.server.netty.connectors

import com.hotels.styx.api.HttpVersion
import com.hotels.styx.api.LiveHttpResponse
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus

internal class StyxToNettyResponseTranslator : ResponseTranslator {
    override fun toNettyResponse(httpResponse: LiveHttpResponse): HttpResponse {
        val version = httpResponse.version().toNettyVersion()
        val httpResponseStatus = HttpResponseStatus.valueOf(httpResponse.status().code())
        val nettyResponse = DefaultHttpResponse(version, httpResponseStatus, true)
        httpResponse.headers().forEach {
            nettyResponse.headers().add(it.name(), it.value())
        }
        return nettyResponse
    }

    private fun HttpVersion.toNettyVersion() = io.netty.handler.codec.http.HttpVersion.valueOf(toString())
}
