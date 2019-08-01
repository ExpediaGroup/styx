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
package com.hotels.styx.services

import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.routing.RoutingObject
import com.hotels.styx.server.HttpInterceptorContext
import io.netty.util.IllegalReferenceCountException
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import java.time.Duration

sealed class ObjectHealth
data class ObjectActive(val failedProbes: Int) : ObjectHealth()
data class ObjectInactive(val successfulProbes: Int) : ObjectHealth()


typealias Probe = (RoutingObject) -> Publisher<Boolean>
typealias CheckState = (currentState: ObjectHealth, reachable: Boolean) -> ObjectHealth

fun urlProbe(probe: HttpRequest, timeout: Duration): Probe =
        { routingObject ->
            routingObject
                    .handle(probe.stream(), HttpInterceptorContext.create())
                    .map {
                        it.consumeInBackground()
                        it.status().code() < 400
                    }
                    .toMono()
                    .timeout(timeout)
                    .onErrorResume {
                        when(it) {
                            is IllegalReferenceCountException -> Mono.error<Boolean>(it)
                            else -> Mono.just(false)
                        }
                    }
        }

fun healthCheckFunction(activeThreshold: Int, inactiveThreshold: Int): CheckState =
        { state, reachable ->
            when (state) {
                is ObjectActive -> if (reachable) {
                    ObjectActive(0)
                } else if (state.failedProbes + 1 < inactiveThreshold) {
                    state.copy(state.failedProbes + 1)
                } else {
                    ObjectInactive(0)
                }
                is ObjectInactive -> if (!reachable) {
                    ObjectInactive(0)
                } else if (state.successfulProbes + 1 < activeThreshold) {
                    state.copy(state.successfulProbes + 1)
                } else {
                    ObjectActive(0)
                }
            }
        }

val logger : Logger = getLogger("HealthChecks.kt")

fun LiveHttpResponse.consumeInBackground() {
    consume(1_000_000).subscribe(object : Subscriber<LiveHttpResponse> {
        override fun onSubscribe(s: Subscription) {
            s.request(Long.MAX_VALUE)
        }

        override fun onNext(o: LiveHttpResponse) {
            // ignore
        }

        override fun onError(t: Throwable) {
            // this will get the implementation class
            logger.error("Unexpected error", t)
        }

        override fun onComplete() {
            // ignore
        }
    })
}