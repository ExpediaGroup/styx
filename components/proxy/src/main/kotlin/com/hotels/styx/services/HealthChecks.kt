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
package com.hotels.styx.services

import com.hotels.styx.*
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.routing.RoutingObject
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import java.time.Duration

sealed class ObjectHealth {
    abstract fun state(): String
    abstract fun health(): Pair<String, Int>?
}

data class ObjectActive(val failedProbes: Int, val healthcheckActive: Boolean = true, val healthTagPresent: Boolean = true) : ObjectHealth() {
    override fun state() = STATE_ACTIVE
    override fun health() =
            if (!healthcheckActive) null
            else if (failedProbes > 0) Pair(HEALTHCHECK_FAILING, failedProbes)
            else Pair(HEALTHCHECK_ON, 0)
}

data class ObjectUnreachable(val successfulProbes: Int, val healthTagPresent: Boolean = true) : ObjectHealth() {
    override fun state() = STATE_UNREACHABLE
    override fun health() =
            if (successfulProbes > 0) Pair(HEALTHCHECK_PASSING, successfulProbes)
            else Pair(HEALTHCHECK_ON, 0)
}

data class ObjectOther(val state: String) : ObjectHealth() {
    override fun state() = state
    override fun health(): Pair<String, Int>? = null
}

typealias Probe = (RoutingObject) -> Publisher<Boolean>
typealias CheckState = (currentState: ObjectHealth, reachable: Boolean) -> ObjectHealth

fun urlProbe(probe: HttpRequest, timeout: Duration, context: HttpInterceptor.Context): Probe =
        { routingObject ->
            routingObject
                    .handle(probe.stream(), context)
                    .map {
                        it.consume()
                        it.status().code() < 400
                    }
                    .toMono()
                    .timeout(timeout)
                    .onErrorResume { Mono.just(false) }
        }

fun healthCheckFunction(activeThreshold: Int, inactiveThreshold: Int): CheckState =
        { state, reachable ->
            when (state) {
                is ObjectActive -> if (reachable) {
                    ObjectActive(0)
                } else if (state.failedProbes + 1 < inactiveThreshold) {
                    state.copy(state.failedProbes + 1)
                } else {
                    ObjectUnreachable(0)
                }
                is ObjectUnreachable -> if (!reachable) {
                    ObjectUnreachable(0)
                } else if (state.successfulProbes + 1 < activeThreshold) {
                    state.copy(state.successfulProbes + 1)
                } else {
                    ObjectActive(0)
                }
                is ObjectOther -> state
            }
        }
