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
package com.hotels.styx.server

import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.api.WebServiceHandler
import com.hotels.styx.common.http.handler.HttpAggregator

import com.hotels.styx.api.HttpResponse.response
import com.hotels.styx.api.HttpResponseStatus.NOT_FOUND

class AdminHttpRouter : HttpHandler {

    private val routes = PathTrie<HttpHandler>()

    override fun handle(
        request: LiveHttpRequest,
        context: HttpInterceptor.Context
    ): Eventual<LiveHttpResponse> {
        return routes.get(request.path())
            .orElse(NOT_FOUND_HANDLER)
            .handle(request, context)
    }

    fun aggregate(
        path: String,
        httpHandler: WebServiceHandler
    ): AdminHttpRouter {
        routes.put(path, HttpAggregator(MEGABYTE, httpHandler))
        return this
    }

    fun stream(
        path: String,
        httpHandler: HttpHandler
    ): AdminHttpRouter {
        routes.put(path, httpHandler)
        return this
    }

    companion object {
        private const val MEGABYTE = 1024 * 1024
        private val NOT_FOUND_HANDLER = HttpHandler {  _, _ -> Eventual.of(response(NOT_FOUND).build().stream()) }
    }
}
