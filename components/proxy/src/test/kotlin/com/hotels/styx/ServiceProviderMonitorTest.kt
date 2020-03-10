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

import com.hotels.styx.api.extension.service.spi.AbstractStyxService
import com.hotels.styx.api.extension.service.spi.StyxServiceStatus.RUNNING
import com.hotels.styx.api.extension.service.spi.StyxServiceStatus.STOPPED
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.services.record
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import io.mockk.mockk

class ServiceProviderMonitorTest : FeatureSpec({

    feature("Service provider lifecycle management") {

        val serviceAaa = MockService("aaa")
        val serviceBbb = MockService("bbb")
        val serviceCcc = MockService("ccc")

        val monitor = ServiceProviderMonitor(
                "Styx-Service-Monitor",
                StyxObjectStore<ProviderObjectRecord>()
                        .apply {
                            record("healthMonitor-aaa", "HealthMonitorService", setOf(), mockk(), serviceAaa)
                            record("healthMonitor-bbb", "HealthMonitorService", setOf(), mockk(), serviceBbb)
                            record("healthMonitor-ccc", "HealthMonitorService", setOf(), mockk(), serviceCcc)
                        })

        scenario("Starts configured services when styx starts up") {
            monitor.start().get()

            serviceAaa.status().shouldBe(RUNNING)
            serviceBbb.status().shouldBe(RUNNING)
            serviceCcc.status().shouldBe(RUNNING)
        }

        scenario("Shuts services when Styx server shuts down") {
            monitor.stop().get()

            serviceAaa.status().shouldBe(STOPPED)
            serviceBbb.status().shouldBe(STOPPED)
            serviceCcc.status().shouldBe(STOPPED)

        }
    }

})

private class MockService(val name: String): AbstractStyxService(name)
