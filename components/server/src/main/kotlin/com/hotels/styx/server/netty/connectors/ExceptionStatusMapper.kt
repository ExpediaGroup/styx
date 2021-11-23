/*
  Copyright (C) 2013-2021 Expedia Inc.

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
package com.hotels.styx.server.netty.connectors

import com.hotels.styx.api.HttpResponseStatus
import org.slf4j.LoggerFactory.getLogger
import java.util.Optional
import java.util.function.Consumer

private val LOG = getLogger(ExceptionStatusMapper::class.java)

class ExceptionStatusMapper(private val multimap: Map<HttpResponseStatus, Set<Class<out Exception>>>) {
    fun statusFor(throwable: Throwable): Optional<HttpResponseStatus> = Optional.ofNullable(kotlinStatusFor(throwable))

    private fun kotlinStatusFor(throwable: Throwable): HttpResponseStatus? {
        val matchingStatuses = multimap.asSequence().flatMap { (status, exceptionClasses) ->
            exceptionClasses.map {
                Pair(status, it)
            }
        }.filter { (_, exceptionClass) ->
            exceptionClass.isInstance(throwable)
        }.sortedBy { (status, _) ->
            status.code()
        }.map { (status, _) ->
            status
        }.toList()

        return if (matchingStatuses.size > 1) {
            LOG.error("Multiple matching statuses for throwable={} statuses={}", throwable, matchingStatuses)
            null
        } else {
            matchingStatuses.firstOrNull()
        }
    }

    class Builder {
        private val multimap: MutableMap<HttpResponseStatus, MutableSet<Class<out Exception>>> = HashMap()

        fun add(status: HttpResponseStatus, vararg classes: Class<out Exception>) : Builder = apply {
            val set = multimap.computeIfAbsent(status) {
                HashSet()
            }

            set.addAll(classes)
        }

        fun build(): ExceptionStatusMapper {
            return ExceptionStatusMapper(multimap)
        }
    }
}

fun buildExceptionStatusMapper(action: ExceptionStatusMapper.Builder.() -> Unit): ExceptionStatusMapper =
    ExceptionStatusMapper.Builder().apply(action).build()

fun buildExceptionStatusMapper(action: Consumer<ExceptionStatusMapper.Builder>): ExceptionStatusMapper =
    buildExceptionStatusMapper { action.accept(this) }
