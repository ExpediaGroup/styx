package com.hotels.styx

import com.hotels.styx.api.extension.service.spi.StyxService
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.handlers.ProviderObjectRecord
import com.hotels.styx.services.record
import io.kotlintest.specs.FeatureSpec
import io.mockk.mockk
import io.mockk.verify

class ServiceProviderMonitorTest : FeatureSpec({

    feature("Service provider lifecycle management") {

        val serviceAaa = mockk<StyxService>(relaxed = true)
        val serviceBbb = mockk<StyxService>(relaxed = true)
        val serviceCcc = mockk<StyxService>(relaxed = true)


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

            verify {
                serviceAaa.start()
                serviceBbb.start()
                serviceCcc.start()
            }
        }

        scenario("Shuts services when Styx server shuts down") {
            monitor.stop().get()

            verify {
                serviceAaa.stop()
                serviceBbb.stop()
                serviceCcc.stop()
            }
        }
    }

})