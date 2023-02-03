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
package com.hotels.styx.server

import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.HttpResponse
import com.hotels.styx.api.HttpResponseStatus.NOT_FOUND
import com.hotels.styx.api.WebServiceHandler

/**
 * Simple Http Router.
 */
class StandardHttpRouter : WebServiceHandler {
    private val routes = PathTrie<WebServiceHandler>()

    override fun handle(request: HttpRequest, context: HttpInterceptor.Context): Eventual<HttpResponse> =
        routes[request.path()].orElse(NOT_FOUND_HANDLER)
            .handle(request, context)

    fun add(path: String, httpHandler: WebServiceHandler): StandardHttpRouter = apply {
        routes.put(path, httpHandler)
    }

    companion object {
        private val NOT_FOUND_HANDLER = WebServiceHandler { _, _ -> Eventual.of(HttpResponse.response(NOT_FOUND).build()) }
    }
}
