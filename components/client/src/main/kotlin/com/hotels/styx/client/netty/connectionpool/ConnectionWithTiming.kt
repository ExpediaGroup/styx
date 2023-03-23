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
package com.hotels.styx.client.netty.connectionpool

import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.client.Connection
import com.hotels.styx.metrics.ContextualTimers
import com.hotels.styx.server.HttpInterceptorContext
import reactor.core.publisher.Flux

/**
 * This only exists to limit how much we need to change code in other classes.
 *
 * If refactoring code, feel free to find a solution that doesn't feature this interface.
 */
interface ConnectionWithTiming : Connection {
    fun write(request: LiveHttpRequest, timers: ContextualTimers): Flux<LiveHttpResponse>
}

fun Connection.write(request: LiveHttpRequest, context: HttpInterceptor.Context): Flux<LiveHttpResponse> {
    return if (this is ConnectionWithTiming && context is HttpInterceptorContext && context.timers != null) {
        write(request, context.timers!!)
    } else {
        write(request)
    }
}