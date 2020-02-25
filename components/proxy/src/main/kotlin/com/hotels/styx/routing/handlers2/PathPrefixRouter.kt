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
import com.hotels.styx.common.Pair
import com.hotels.styx.config.schema.SchemaDsl.`object`
import com.hotels.styx.config.schema.SchemaDsl.field
import com.hotels.styx.config.schema.SchemaDsl.list
import com.hotels.styx.config.schema.SchemaDsl.routingObject
import com.hotels.styx.config.schema.SchemaDsl.string
import com.hotels.styx.routing.RoutingObject
import com.hotels.styx.routing.config.Builtins
import com.hotels.styx.routing.config2.StyxObject
import com.hotels.styx.server.NoServiceConfiguredException
import java.util.*
import java.util.concurrent.ConcurrentSkipListMap

// TODO: Add missing lifecycle handlers for (stop)

private class PathPrefixRouterObject(val routeMap: ConcurrentSkipListMap<String, RoutingObject>) : RoutingObject {

    override fun handle(request: LiveHttpRequest, context: HttpInterceptor.Context): Eventual<LiveHttpResponse> {
        val path = request.path()!!

        return routeMap.entries.stream()
                .filter { path.startsWith(it.key) }
                .findFirst()
                .map { it.value }
                .orElse(object : RoutingObject {
                    override fun handle(request: LiveHttpRequest, context: HttpInterceptor.Context): Eventual<LiveHttpResponse> = Eventual.error(NoServiceConfiguredException(path))
                })
                .handle(request, context)
    }
}

val pathPrefixRouterDescriptor = Builtins.StyxObjectDescriptor<StyxObject<RoutingObject>>(
        "PathPrefixRouter",
        `object`(
                field("routes", list(`object`(
                        field("prefix", string()),
                        field("destination", routingObject())
                )))
        ),
        PathPrefixRouter::class.java)


data class PathPrefixRouter(@JsonProperty val routes: List<Route>) : StyxObject<RoutingObject> {

    override fun type() = pathPrefixRouterDescriptor.type()

    override fun build(context: StyxObject.Context): RoutingObject {

        val routeMap = ConcurrentSkipListMap<String, RoutingObject>(
                Comparator.comparingInt { obj: String -> obj.length }
                        .reversed()
                        .thenComparing(Comparator.naturalOrder())
        )

        routes.map { Pair.pair(it.prefix, it.destination.build(context)) }
                .forEach { routeMap.put(it.key(), it.value()) }

        return PathPrefixRouterObject(routeMap)
    }
}

data class Route(
        @JsonProperty val prefix: String,
        @JsonProperty val destination: StyxObject<RoutingObject>
)
