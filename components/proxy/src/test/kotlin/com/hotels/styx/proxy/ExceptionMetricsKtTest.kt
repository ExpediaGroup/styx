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
package com.hotels.styx.proxy

import com.hotels.styx.api.Id.id
import com.hotels.styx.api.MicrometerRegistry
import com.hotels.styx.api.exceptions.NoAvailableHostsException
import com.hotels.styx.metrics.CentralisedMetrics
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test

internal class ExceptionMetricsKtTest {
    @Test
    fun registerNoHostsLiveForApplication() {
        val metrics = MicrometerRegistry(SimpleMeterRegistry())
        val centralisedMetrics = CentralisedMetrics(metrics)
        countBackendFault(centralisedMetrics, NoAvailableHostsException(id("app-id")))

        val meter: Meter? = metrics.meters.firstOrNull { it.id.name == "proxy.client.backends.fault" }
        meter shouldNotBe null
        meter!!.id.name shouldBe "proxy.client.backends.fault"
        meter.id.tags.forEach { println(it) }
        meter.id.tags shouldContain Tag.of("application", "app-id")
        meter.id.tags shouldContain Tag.of("faultType", "noHostsLiveForApplication")
        // Not specifying an origin causes an error in prometheus because the tag names must match when the name matches
        meter.id.tags shouldContain Tag.of("origin", " ")
    }
}

private class TestingException : Exception("Not a real exception, just testing")
