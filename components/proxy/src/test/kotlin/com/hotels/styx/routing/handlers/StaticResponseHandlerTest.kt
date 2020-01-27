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
package com.hotels.styx.routing.handlers

import com.hotels.styx.api.HttpResponseStatus.CREATED
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.RoutingObjectFactoryContext
import com.hotels.styx.requestContext
import com.hotels.styx.routingObjectDef
import com.hotels.styx.wait
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import reactor.core.publisher.toMono
import java.nio.charset.StandardCharsets.UTF_8


class StaticResponseHandlerTest : StringSpec({
    val context = RoutingObjectFactoryContext().get()

    val config = routingObjectDef("""
              name: proxy-and-log-to-https
              type: StaticResponseHandler
              config:
                  status: 201
                  content: "secure"
          """.trimIndent())

    "Builds static response handler." {
        val handler = StaticResponseHandler.Factory().build(listOf(), context, config)

        handler.handle(LiveHttpRequest.get("/foo").build(), requestContext())
                .toMono()
                .block()!!
                .status() shouldBe (CREATED)
    }

    "Content defaults to an empty string." {
        val handler = StaticResponseHandler(200, null, null)

        val response = handler
                .handle(LiveHttpRequest.get("/foo").build(), requestContext())
                .wait()!!

        response.status() shouldBe (OK)
        response.bodyAs(UTF_8) shouldBe ""
    }

})