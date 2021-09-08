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
import io.micrometer.core.instrument.MeterRegistry.Config
import io.micrometer.core.instrument.search.RequiredSearch
import io.micrometer.core.instrument.search.Search
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.ToDoubleFunction
import java.util.function.ToLongFunction

interface MeterRegistry {
    val meters: List<Meter>

    fun counter(name: String, vararg tags: String): Counter
    fun counter(name: String, tags: Iterable<Tag>): Counter
    fun summary(name: String, vararg tags: String): DistributionSummary
    fun summary(name: String, tags: Iterable<Tag>): DistributionSummary
    fun get(name: String): RequiredSearch
    fun find(name: String): Search
    fun forEachMeter(consumer: Consumer<Meter>)
    fun config(): Config
    fun timer(name: String, tags: Iterable<Tag>): Timer
    fun timer(name: String, vararg tags: String): Timer
    fun more(): More
    fun <T> gauge(name: String, tags: Iterable<Tag>, stateObject: T, valueFunction: ToDoubleFunction<T>): T
    fun <T : Number> gauge(name: String, tags: Iterable<Tag>, number: T): T
    fun <T : Number> gauge(name: String, number: T): T
    fun <T> gauge(name: String, stateObject: T, valueFunction: ToDoubleFunction<T>): T
    fun <T : Collection<*>> gaugeCollectionSize(name: String, tags: Iterable<Tag>, collection: T): T
    fun <T : Map<*, *>> gaugeMapSize(name: String, tags: Iterable<Tag>, map: T): T
    fun remove(meter: Meter): Meter?
    fun remove(mappedId: Meter.Id): Meter?
    fun clear()
    fun close()
    fun isClosed(): Boolean


    interface More {
        fun longTaskTimer(name: String, vararg tags: String): LongTaskTimer
        fun longTaskTimer(name: String, tags: Iterable<Tag>): LongTaskTimer
        fun <T> counter(name: String, tags: Iterable<Tag>, obj: T, countFunction: ToDoubleFunction<T>): FunctionCounter
        fun <T : Number> counter(name: String, tags: Iterable<Tag>, number: T): FunctionCounter
        fun <T> timer(
            name: String,
            tags: Iterable<Tag>,
            obj: T,
            countFunction: ToLongFunction<T>,
            totalTimeFunction: ToDoubleFunction<T>,
            totalTimeFunctionUnit: TimeUnit
        ): FunctionTimer

        fun <T> timeGauge(name: String, tags: Iterable<Tag>, obj: T, timeFunctionUnit: TimeUnit, timeFunction: ToDoubleFunction<T>): TimeGauge
    }

    ///
    fun scope(scope: String): ScopedMeterRegistry = ScopedMeterRegistry(this, scope)
    fun startTimer(): Timer.Sample
    fun micrometerRegistry(): io.micrometer.core.instrument.MeterRegistry?
}

class ScopedMeterRegistry(private val registry: MeterRegistry, private val scope: String) : MeterRegistry {
    override val meters: List<Meter> get() = registry.meters

    override fun counter(name: String, vararg tags: String): Counter = registry.counter(name.scoped, *tags)
    override fun counter(name: String, tags: Iterable<Tag>): Counter = registry.counter(name.scoped, tags)
    override fun summary(name: String, vararg tags: String): DistributionSummary = registry.summary(name.scoped, *tags)
    override fun summary(name: String, tags: Iterable<Tag>): DistributionSummary = registry.summary(name.scoped, tags)
    override fun get(name: String): RequiredSearch = registry.get(name.scoped)
    override fun find(name: String): Search = registry.find(name.scoped)
    override fun forEachMeter(consumer: Consumer<Meter>) = registry.forEachMeter(consumer)
    override fun config(): Config = registry.config()
    override fun timer(name: String, tags: Iterable<Tag>): Timer = registry.timer(name.scoped, tags)
    override fun timer(name: String, vararg tags: String): Timer = registry.timer(name.scoped, *tags)

    override fun more(): MeterRegistry.More = object : MeterRegistry.More {
        val more = registry.more()

        override fun longTaskTimer(name: String, vararg tags: String): LongTaskTimer = more.longTaskTimer(name.scoped, *tags)
        override fun longTaskTimer(name: String, tags: Iterable<Tag>): LongTaskTimer = more.longTaskTimer(name.scoped, tags)

        override fun <T> counter(name: String, tags: Iterable<Tag>, obj: T, countFunction: ToDoubleFunction<T>): FunctionCounter =
            more.counter(name.scoped, tags, obj, countFunction)

        override fun <T : Number> counter(name: String, tags: Iterable<Tag>, number: T): FunctionCounter =
            more.counter(name.scoped, tags, number)

        override fun <T> timer(
            name: String,
            tags: Iterable<Tag>,
            obj: T,
            countFunction: ToLongFunction<T>,
            totalTimeFunction: ToDoubleFunction<T>,
            totalTimeFunctionUnit: TimeUnit
        ): FunctionTimer = more.timer(name.scoped, tags, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit)

        override fun <T> timeGauge(
            name: String,
            tags: Iterable<Tag>,
            obj: T,
            timeFunctionUnit: TimeUnit,
            timeFunction: ToDoubleFunction<T>
        ): TimeGauge = more.timeGauge(name.scoped, tags, obj, timeFunctionUnit, timeFunction)
    }

    override fun <T : Number> gauge(name: String, number: T): T = registry.gauge(name.scoped, number)
    override fun <T> gauge(name: String, stateObject: T, valueFunction: ToDoubleFunction<T>): T =
        registry.gauge(name.scoped, stateObject, valueFunction)

    override fun <T> gauge(name: String, tags: Iterable<Tag>, stateObject: T, valueFunction: ToDoubleFunction<T>): T =
        registry.gauge(name.scoped, tags, stateObject, valueFunction)

    override fun <T : Number> gauge(name: String, tags: Iterable<Tag>, number: T): T = registry.gauge(name.scoped, tags, number)

    override fun <T : Collection<*>> gaugeCollectionSize(name: String, tags: Iterable<Tag>, collection: T): T =
        registry.gaugeCollectionSize(name.scoped, tags, collection)

    override fun <T : Map<*, *>> gaugeMapSize(name: String, tags: Iterable<Tag>, map: T): T = registry.gaugeMapSize(name.scoped, tags, map)

    override fun remove(meter: Meter): Meter? = registry.remove(meter)
    override fun remove(mappedId: Meter.Id): Meter? = registry.remove(mappedId.scoped)
    override fun clear() = registry.clear()
    override fun close() = registry.close()
    override fun isClosed() = registry.isClosed()


    ////
    override fun startTimer(): Timer.Sample = registry.startTimer()
    override fun micrometerRegistry(): io.micrometer.core.instrument.MeterRegistry? = registry.micrometerRegistry()

    private val String.scoped: String get() = "$scope.$this"
    private val Meter.Id.scoped: Meter.Id get() = withName(name.scoped)
}

class MicrometerRegistry(private val registry: io.micrometer.core.instrument.MeterRegistry) : MeterRegistry {
    override val meters: List<Meter> get() = registry.meters

    override fun counter(name: String, vararg tags: String): Counter = registry.counter(name, *tags)
    override fun counter(name: String, tags: Iterable<Tag>): Counter = registry.counter(name, tags)
    override fun summary(name: String, vararg tags: String): DistributionSummary = registry.summary(name, *tags)
    override fun summary(name: String, tags: Iterable<Tag>): DistributionSummary = registry.summary(name, tags)
    override fun get(name: String): RequiredSearch = registry.get(name)
    override fun find(name: String): Search = registry.find(name)
    override fun forEachMeter(consumer: Consumer<Meter>) = registry.forEachMeter(consumer)
    override fun config(): Config = registry.config()
    override fun timer(name: String, tags: Iterable<Tag>): Timer = registry.timer(name, tags)
    override fun timer(name: String, vararg tags: String): Timer = registry.timer(name, *tags)
    override fun more(): MeterRegistry.More = object : MeterRegistry.More {
        val more = registry.more()

        override fun longTaskTimer(name: String, vararg tags: String): LongTaskTimer = more.longTaskTimer(name, *tags)
        override fun longTaskTimer(name: String, tags: Iterable<Tag>): LongTaskTimer = more.longTaskTimer(name, tags)

        override fun <T> counter(name: String, tags: Iterable<Tag>, obj: T, countFunction: ToDoubleFunction<T>): FunctionCounter =
            more.counter(name, tags, obj, countFunction)

        override fun <T : Number> counter(name: String, tags: Iterable<Tag>, number: T): FunctionCounter =
            more.counter(name, tags, number)

        override fun <T> timer(
            name: String,
            tags: Iterable<Tag>,
            obj: T,
            countFunction: ToLongFunction<T>,
            totalTimeFunction: ToDoubleFunction<T>,
            totalTimeFunctionUnit: TimeUnit
        ): FunctionTimer = more.timer(name, tags, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit)

        override fun <T> timeGauge(
            name: String,
            tags: Iterable<Tag>,
            obj: T,
            timeFunctionUnit: TimeUnit,
            timeFunction: ToDoubleFunction<T>
        ): TimeGauge = more.timeGauge(name, tags, obj, timeFunctionUnit, timeFunction)
    }

    override fun <T> gauge(name: String, tags: Iterable<Tag>, stateObject: T, valueFunction: ToDoubleFunction<T>): T =
        registry.gauge(name, tags, stateObject, valueFunction)

    override fun <T : Number> gauge(name: String, tags: Iterable<Tag>, number: T): T = registry.gauge(name, tags, number)

    override fun <T : Number> gauge(name: String, number: T): T = registry.gauge(name, number)
    override fun <T> gauge(name: String, stateObject: T, valueFunction: ToDoubleFunction<T>): T = registry.gauge(name, stateObject, valueFunction)

    override fun <T : Collection<*>> gaugeCollectionSize(name: String, tags: Iterable<Tag>, collection: T): T =
        registry.gaugeCollectionSize(name, tags, collection)

    override fun <T : Map<*, *>> gaugeMapSize(name: String, tags: Iterable<Tag>, map: T): T = registry.gaugeMapSize(name, tags, map)

    override fun remove(meter: Meter): Meter? = registry.remove(meter)
    override fun remove(mappedId: Meter.Id): Meter? = registry.remove(mappedId)
    override fun clear() = registry.clear()
    override fun close() = registry.close()
    override fun isClosed() = registry.isClosed

    ////

    override fun startTimer(): Timer.Sample = Timer.start(registry)

    override fun micrometerRegistry(): io.micrometer.core.instrument.MeterRegistry = registry
}