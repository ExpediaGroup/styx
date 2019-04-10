package com.hotels.styx.routing.db

import com.hotels.styx.routing.config.RoutingObjectFactory
import io.kotlintest.specs.StringSpec
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import io.mockk.mockk

class StyxRouteDatabaseTest : FeatureSpec() {

    init {
        feature("Insert") {
            scenario("") {
                val objectFactory = mockk<RoutingObjectFactory>()
                val db = StyxRouteDatabase(objectFactory)

                // Routing object definition as a JSON string
                db.insert()

                // Routing object definition as key and RoutingObjectDefinition
                db.insert(key, value)
            }
        }
    }

}