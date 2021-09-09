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
package com.hotels.styx.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry

fun <T : MeterRegistry> MeterRegistry.findRegistry(type: Class<T>): T? {
    if (type.isInstance(this)) {
        return this as T
    }

    if (this is CompositeMeterRegistry) {
        return registries.asSequence().map {
            it.findRegistry(type)
        }.filterNotNull().firstOrNull()
    }

    return null
}