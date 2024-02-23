/*
  Copyright (C) 2013-2024 Expedia Inc.

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

import com.hotels.styx.api.extension.Origin
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.config.MeterFilter
import reactor.netty.Metrics.NAME

class ReactorNettyMeterFilter(private val origin: Origin) : MeterFilter {
    override fun map(id: Meter.Id): Meter.Id =
        if (id.name.startsWith(REACTOR_NETTY_PREFIX) &&
            id.getTag(NAME)?.endsWith("${origin.id()}") == true
        ) {
            id.withTags(
                Tags.of(
                    APP_ID,
                    origin.applicationId().toString(),
                    ORIGIN_ID,
                    origin.id().toString(),
                ),
            )
        } else {
            id
        }

    companion object {
        private const val REACTOR_NETTY_PREFIX = "reactor.netty"
        private const val APP_ID = "appId"
        private const val ORIGIN_ID = "originId"
    }
}
