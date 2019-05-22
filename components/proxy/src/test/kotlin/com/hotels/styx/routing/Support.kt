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
package com.hotels.styx.routing

import com.hotels.styx.Environment
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig
import com.hotels.styx.proxy.plugin.NamedPlugin
import com.hotels.styx.routing.config.BuiltinInterceptorsFactory
import com.hotels.styx.routing.config.HttpHandlerFactory
import com.hotels.styx.routing.config.RoutingObjectDefinition
import com.hotels.styx.routing.config.RoutingObjectFactory
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.server.HttpInterceptorContext
import io.mockk.mockk

fun routingObjectDef(text: String) = YamlConfig(text).`as`((RoutingObjectDefinition::class.java))

data class RoutingContext(
        val environment: Environment = mockk(),
        val routeDb: StyxObjectStore<RoutingObjectRecord> = mockk(),
        val routingObjectFactory: RoutingObjectFactory = mockk(),
        val plugins: Iterable<NamedPlugin> = mockk(),
        val interceptorsFactory: BuiltinInterceptorsFactory = mockk(),
        val requestTracking: Boolean = false) {
    fun get() = HttpHandlerFactory.Context(environment, routeDb, routingObjectFactory, plugins, interceptorsFactory, requestTracking)
}

fun HttpHandler.handle(request: HttpRequest, count: Int = 10000) = this.handle(request.stream(), HttpInterceptorContext.create())
        .flatMap { it.aggregate(count) }
