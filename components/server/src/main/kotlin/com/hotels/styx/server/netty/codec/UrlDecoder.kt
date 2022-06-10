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
package com.hotels.styx.server.netty.codec

import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.Url
import com.hotels.styx.api.Url.Builder.url
import io.netty.handler.codec.http.HttpRequest
import okhttp3.HttpUrl
import java.net.URI

object UrlDecoder {
    @JvmStatic
    fun decodeUrl(unwiseCharEncoder: UnwiseCharsEncoder, request: HttpRequest): Url {
        val host = request.headers()[HOST]

        return if (request.uri().startsWith("/") && host != null) {
            val encodedUrl = "http://$host${unwiseCharEncoder.encode(request.uri())}"
            val uri = try {
                URI.create(encodedUrl)
            } catch (e: IllegalArgumentException) {
                HttpUrl.parse(encodedUrl)!!.uri()
            }
            Url.Builder()
                .path(uri.rawPath)
                .rawQuery(uri.rawQuery)
                .fragment(uri.fragment)
                .build()
        } else {
            url(unwiseCharEncoder.encode(request.uri())).build()
        }
    }
}

