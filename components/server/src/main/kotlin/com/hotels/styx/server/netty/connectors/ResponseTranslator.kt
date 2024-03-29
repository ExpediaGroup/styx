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

import com.hotels.styx.api.LiveHttpResponse
import io.netty.handler.codec.http.HttpResponse

/**
 * Converts object of a type [LiveHttpResponse]
 * to [io.netty.handler.codec.http.HttpResponse].
 */
fun interface ResponseTranslator {
    fun toNettyResponse(httpResponse: LiveHttpResponse): HttpResponse
}
