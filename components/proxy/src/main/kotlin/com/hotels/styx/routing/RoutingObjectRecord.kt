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
package com.hotels.styx.routing

import com.hotels.styx.routing.config2.StyxObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_DATE_TIME

/**
 * A routing object and its associated configuration metadata.
 */
internal data class RoutingObjectRecord<T>(
        val type: String,
        val tags: Set<String>,
        val config: StyxObject<T>,
        val routingObject: RoutingMetadataDecorator) {
    companion object {
        fun <T> create(type: String, tags: Set<String>, config: StyxObject<T>, routingObject: RoutingObject) = RoutingObjectRecord(
                type,
                tags + "created:${timestamp()}",
                config,
                RoutingMetadataDecorator(routingObject))
    }

    fun creationTime() = tags
            .filter { it.startsWith("created:") }
            .first()
}

internal data class RoutingObjectYamlRecord<T>(
        val type: String,
        val tags: Set<String>,
        val config: StyxObject<T>
)

private fun timestamp(): String {
    return LocalDateTime.now().format(ISO_DATE_TIME)
}
