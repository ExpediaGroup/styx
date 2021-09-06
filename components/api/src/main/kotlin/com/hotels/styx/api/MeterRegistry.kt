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
package com.hotels.styx.api

import io.micrometer.core.instrument.*
import io.micrometer.core.instrument.search.RequiredSearch
import io.micrometer.core.instrument.search.Search

interface MeterRegistry {
    val meters : List<Meter>

    fun scope(scope : String) : ScopedMeterRegistry = ScopedMeterRegistry(this, scope)

    fun startTimer(): Timer.Sample

    fun micrometerRegistry(): io.micrometer.core.instrument.MeterRegistry?

    fun remove(meter: Meter): Meter?

    fun <T : Number> gauge(name: String, number: T): T?

    fun <T> gauge(name: String, tags: Iterable<Tag>, stateObject: T?, valueFunction: (T?) -> Double): T?

    fun counter(name: String): Counter

    fun counter(name: String, vararg tags: String): Counter

    fun counter(name: String, tags: Tags): Counter

    fun summary(name: String): DistributionSummary

    fun summary(name: String, tags: Tags): DistributionSummary

    fun get(name : String): RequiredSearch

    fun find(name : String) : Search
}

class ScopedMeterRegistry(private val registry: MeterRegistry, private val scope: String) : MeterRegistry {
    override val meters: List<Meter>
        get() = registry.meters

    override fun remove(meter: Meter): Meter? = registry.remove(meter)
    override fun startTimer(): Timer.Sample = registry.startTimer()
    override fun micrometerRegistry(): io.micrometer.core.instrument.MeterRegistry? = registry.micrometerRegistry()

    override fun <T : Number> gauge(name: String, number: T): T? = registry.gauge(name.scoped, number)

    override fun <T> gauge(name: String, tags: Iterable<Tag>, stateObject: T?, valueFunction: (T?) -> Double): T? {
        return registry.gauge(name.scoped, tags, stateObject, valueFunction)
    }

    override fun counter(name: String): Counter = registry.counter(name.scoped)
    override fun counter(name: String, vararg tags: String): Counter = registry.counter(name.scoped, *tags)
    override fun counter(name: String, tags: Tags): Counter = registry.counter(name.scoped, tags)
    override fun summary(name: String): DistributionSummary = registry.summary(name.scoped)
    override fun summary(name: String, tags: Tags): DistributionSummary = registry.summary(name.scoped, tags)
    override fun get(name: String): RequiredSearch = registry.get(name.scoped)
    override fun find(name: String): Search = registry.find(name.scoped)

    private val String.scoped: String get() = "$scope.$this"
}

class MicrometerRegistry(private val registry: io.micrometer.core.instrument.MeterRegistry) : MeterRegistry {
    override val meters: List<Meter>
        get() = registry.meters

    override fun remove(meter: Meter): Meter? = registry.remove(meter)

    override fun startTimer(): Timer.Sample = Timer.start(registry)

    override fun micrometerRegistry(): io.micrometer.core.instrument.MeterRegistry = registry

    override fun <T : Number> gauge(name: String, number: T): T? = registry.gauge(name, number)

    override fun <T> gauge(name: String, tags: Iterable<Tag>, stateObject: T?, valueFunction: (T?) -> Double): T? {
        return registry.gauge(name, tags, stateObject, valueFunction)
    }

    override fun counter(name: String): Counter = registry.counter(name)
    override fun counter(name: String, vararg tags: String): Counter = registry.counter(name, *tags)
    override fun counter(name: String, tags: Tags): Counter = registry.counter(name, tags)
    override fun summary(name: String): DistributionSummary = registry.summary(name)
    override fun summary(name: String, tags: Tags): DistributionSummary = registry.summary(name, tags)

    override fun get(name: String): RequiredSearch {
        return registry.get(name)
    }

    override fun find(name: String): Search = registry.find(name)



    fun findOutWhatMethodSigsAre() {


        registry.meters
    }
}