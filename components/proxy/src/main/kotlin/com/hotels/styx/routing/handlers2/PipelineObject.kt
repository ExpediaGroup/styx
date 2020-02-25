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
import com.hotels.styx.config.schema.SchemaDsl.`object`
import com.hotels.styx.config.schema.SchemaDsl.field
import com.hotels.styx.config.schema.SchemaDsl.list
import com.hotels.styx.config.schema.SchemaDsl.optional
import com.hotels.styx.config.schema.SchemaDsl.or
import com.hotels.styx.config.schema.SchemaDsl.routingObject
import com.hotels.styx.config.schema.SchemaDsl.string
import com.hotels.styx.proxy.plugin.NamedPlugin
import com.hotels.styx.routing.RoutingObject
import com.hotels.styx.routing.config.Builtins
import com.hotels.styx.routing.config.StyxObjectReference
import com.hotels.styx.routing.config2.StyxObject
import com.hotels.styx.routing.handlers.StandardHttpPipeline
import com.hotels.styx.routing.handlers.StandardHttpPipeline.HttpInterceptorChain
import com.hotels.styx.server.track.CurrentRequestTracker
import com.hotels.styx.server.track.RequestTracker

private class PipelineObject(val interceptors: List<NamedPlugin>, private val handler: RoutingObject, trackRequests: Boolean) : RoutingObject {
    var requestTracker = if (trackRequests) CurrentRequestTracker.INSTANCE else RequestTracker.NO_OP
    val pipeline = StandardHttpPipeline(interceptors, handler, requestTracker)

    override fun handle(request: LiveHttpRequest, context: HttpInterceptor.Context): Eventual<LiveHttpResponse> {
        val interceptorsChain = HttpInterceptorChain(interceptors, 0, handler, context, requestTracker)
        return interceptorsChain.proceed(request)
    }
}

val PipelineDescriptor = Builtins.StyxObjectDescriptor<StyxObject<RoutingObject>>(
        "Pipeline",
        `object`(
                optional("pipeline", or(string(), list(string()))),
                field("handler", routingObject())
        ),
        Pipeline::class.java)

// TODO:
//   Custom deserialiser:
//     - if `pipeline` is a String, then convert to List<String>
//     - else serialise it as it is.

data class Pipeline(
        @JsonProperty val pipeline: List<String>,
        @JsonProperty val handler: StyxObject<RoutingObject>) : StyxObject<RoutingObject> {
    override fun type() = PipelineDescriptor.type()

    override fun build(context: StyxObject.Context): RoutingObject {
        // Support named references only. For now.

        val plugins = context.plugins().toList()
                .map { it.name() to it }
                .toMap()

        val interceptors: List<NamedPlugin> = pipeline.map { StyxObjectReference(it) }
                .onEach {
                    if (!plugins.containsKey(it.name())) {
                        throw IllegalArgumentException("No such plugin or interceptor exists, name='${it.name()}'")
                    }
                }
                .map { plugins.get(it.name())!! }

        return PipelineObject(interceptors, handler.build(context), true)
    }

}
