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

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_DATE_TIME

/**
 * A routing object and its associated configuration metadata.
 */
internal data class RoutingObjectRecord(
        val type: String,
        val tags: Set<String>,
        val config: JsonNode,
        val routingObject: RoutingMetadataDecorator) {
    companion object {
        fun create(type: String, tags: Set<String>, config: JsonNode, routingObject: RoutingObject) = RoutingObjectRecord(
                type,
                tags + "created:${timestamp()}",
                config,
                RoutingMetadataDecorator(routingObject))
    }

    fun creationTime() = tags
            .filter { it.startsWith("created:") }
            .first()
}

private fun timestamp(): String {
    return LocalDateTime.now().format(ISO_DATE_TIME)
}
