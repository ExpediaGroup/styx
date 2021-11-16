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
package com.hotels.styx.api.extension.service.spi

import com.hotels.styx.api.Id
import com.hotels.styx.api.Identifiable
import com.hotels.styx.api.extension.service.spi.Registry.Changes

/**
 * Determines changes between two iterables of resources.
 */
fun <T : Identifiable> changes(newResources: Iterable<T>, currentResources: Iterable<T>): Changes<T> {
    val newIdsToResource = mapById(newResources)
    val currentIdsToResource = mapById(currentResources)

    val added = newIdsToResource.asSequence().filter { (key, _) ->
        currentIdsToResource[key] == null
    }.map { it.value }.toList()

    val removed = currentIdsToResource.asSequence().filter { (key, _) ->
        newIdsToResource[key] == null
    }.map { it.value }.toList()

    val updated: Collection<T> = newIdsToResource.asSequence().filter { (key, value) ->
        val currentValue = currentIdsToResource[key]

        currentValue != null && value != currentValue
    }.map { it.value }.toList()

    return Changes.Builder<T>()
        .added(added)
        .removed(removed)
        .updated(updated)
        .build()
}

private fun <T : Identifiable> mapById(resources: Iterable<T>): Map<Id, T> = resources.associateBy { it.id() }
