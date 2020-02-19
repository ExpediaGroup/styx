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
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.routing.RoutingObject
import com.hotels.styx.routing.config.Builtins
import com.hotels.styx.routing.config2.StyxObject

internal class PipelineObject(private val handler: RoutingObject) : RoutingObject {
    override fun handle(request: LiveHttpRequest, context: HttpInterceptor.Context): Eventual<LiveHttpResponse> = handler
            .handle(request, context)
}

val PipelineDescriptor = Builtins.StyxObjectDescriptor<StyxObject>("Pipeline", null, null, Pipeline::class.java)

internal data class Pipeline(@JsonProperty val handler: StyxObject) : StyxObject {

    override fun type() = PipelineDescriptor.type()

    override fun build(context: StyxObject.Context): RoutingObject {
        return PipelineObject(handler.build(context))
    }
}

