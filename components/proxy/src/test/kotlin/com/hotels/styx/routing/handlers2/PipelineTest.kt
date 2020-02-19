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

import com.hotels.styx.RoutingObjectFactoryContext2
import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponse
import com.hotels.styx.api.HttpResponseStatus
import com.hotels.styx.routing.RoutingObject
import com.hotels.styx.routing.config2.StyxObject
import com.hotels.styx.routing.handlers.RouteRefLookup
import com.hotels.styx.support.Support.requestContext
import com.hotels.styx.wait
import io.kotlintest.specs.FunSpec

class PipelineTest : FunSpec({

    val descriptors = mapOf(
            RefLookupDescriptor.type() to RefLookupDescriptor,
            PipelineDescriptor.type() to PipelineDescriptor
    )

    // Check this: https://www.baeldung.com/jackson-call-default-serializer-from-custom-serializer
    test("serialises nested objects") {
        val pipeline = Pipeline(Pipeline(RefLookup("abc")))

        val myPipeline = pipeline.build(RoutingObjectFactoryContext2().get())

        val response = myPipeline.handle(get("/").build().stream(), requestContext())
                .wait(1024)!!

        println("response: " + response)
        println("my configuration: " + objectMmapper(descriptors).writeValueAsString(pipeline))
    }

    test("deserialises nested objects") {
        val pipeline: StyxObject = objectMmapper(descriptors).readValue("""
            type: Pipeline
            config: 
              handler:
                type: Pipeline
                config:
                  handler:
                    type: RefLookup
                    config:
                      name: abc
            """.trimIndent(), StyxObject::class.java) as StyxObject

        val routeDb = mapOf("abc" to RoutingObject { request, context -> Eventual.of(HttpResponse.response(HttpResponseStatus.OK).build().stream()) })

        val context = RoutingObjectFactoryContext2(routeRefLookup = RouteRefLookup { ref -> routeDb[ref.name()] })

        val pipelineObject = pipeline.build(context.get())

        val response = pipelineObject
                .handle(get("/").build().stream(), requestContext())
                .wait(1024)!!

        println("response: " + response)
    }
})
