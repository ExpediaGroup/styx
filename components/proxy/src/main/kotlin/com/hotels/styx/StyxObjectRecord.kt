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
package com.hotels.styx

import com.fasterxml.jackson.databind.JsonNode
import com.hotels.styx.api.extension.service.spi.StyxService

/**
 * A routing object and its associated configuration metadata.
 */
data class StyxObjectRecord<T: StyxService>(
        val type: String,
        val tags: Set<String>,
        val config: JsonNode,
        val styxService: T)

typealias ProviderObjectRecord = StyxObjectRecord<StyxService>

typealias ServerObjectRecord = StyxObjectRecord<InetServer>
