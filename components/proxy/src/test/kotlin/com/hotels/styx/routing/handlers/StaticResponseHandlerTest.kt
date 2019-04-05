/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx.routing.handlers

import com.hotels.styx.api.HttpResponseStatus.CREATED
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.routing.configBlock
import com.hotels.styx.server.HttpInterceptorContext
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import reactor.core.publisher.Mono


class StaticResponseHandlerTest: StringSpec({
    val config = configBlock("""
          config:
              name: proxy-and-log-to-https
              type: StaticResponseHandler
              config:
                  status: 201
                  content: "secure"

          """.trimIndent())

    "builds static response handler" {
        val handler = StaticResponseHandler.Factory().build(listOf(), null, config)
        val response = Mono.from(handler.handle(LiveHttpRequest.get("/foo").build(), HttpInterceptorContext.create())).block()

        response?.status() shouldBe (CREATED)
    }

})