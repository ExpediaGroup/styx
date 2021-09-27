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
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.ToDoubleFunction
import java.util.function.ToLongFunction
import java.util.function.UnaryOperator

/**
 * An interface to represent any implementation of a meter registry.
 */
interface MeterRegistry {
    val meters: List<Meter>

    fun counter(name: String, vararg tags: String): Counter
    fun counter(name: String, tags: Iterable<Tag>): Counter
    fun summary(name: String, vararg tags: String): DistributionSummary
    fun summary(name: String, tags: Iterable<Tag>): DistributionSummary
    fun summary(name: String, build: UnaryOperator<DistributionSummary.Builder>): DistributionSummary
    fun get(name: String): RequiredSearch
    fun find(name: String): Search
    fun forEachMeter(consumer: Consumer<Meter>)
    fun config(): Config
    fun timer(name: String, tags: Iterable<Tag>): Timer
    fun timer(name: String, vararg tags: String): Timer
    fun timer(name: String, build: UnaryOperator<Timer.Builder>): Timer
    fun more(): More
    fun gaugeWithStrongReference(name: String, number: Number): Gauge
    fun gaugeWithStrongReference(name: String, tags: Iterable<Tag>, number: Number): Gauge
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

    // Functions below here are not for general metric handling, but needed by Styx internally.

    /**
     * Creates a new scoped registry based on this registry. If this registry is already scoped, the new scope will be prefixed to the existing scope.
     *
     * So, if `fooRegistry` has scope "foo", and we call `val barRegistry = fooRegistry.scope("bar")`, then `barRegistry` will have the scope "bar.foo".
     */
    fun scope(scope: String): ScopedMeterRegistry = ScopedMeterRegistry(this, scope)
    fun startTimer(): Timer.Sample
    fun micrometerRegistry(): io.micrometer.core.instrument.MeterRegistry

    fun timerWithStyxDefaults(name: String, tags: Iterable<Tag>): Timer
}

/**
 * An implementation of MeterRegistry that prefixes all metric names with the given "scope" when they are passed as parameters.
 *
 * This affects both the creation of metrics with methods like counter(..) and timer(..) as well as retrieval through find(...) and get(..).
 */
class ScopedMeterRegistry(private val parent: MeterRegistry, private val scope: String) : MeterRegistry {
    override val meters: List<Meter> get() = parent.meters

    override fun counter(name: String, vararg tags: String): Counter = parent.counter(name.scoped, *tags)
    override fun counter(name: String, tags: Iterable<Tag>): Counter = parent.counter(name.scoped, tags)
    override fun summary(name: String, vararg tags: String): DistributionSummary = parent.summary(name.scoped, *tags)
    override fun summary(name: String, tags: Iterable<Tag>): DistributionSummary = parent.summary(name.scoped, tags)
    override fun summary(name: String, build: UnaryOperator<DistributionSummary.Builder>) = parent.summary(name.scoped, build)

    override fun get(name: String): RequiredSearch = parent.get(name.scoped)
    override fun find(name: String): Search = parent.find(name.scoped)
    override fun forEachMeter(consumer: Consumer<Meter>) = parent.forEachMeter(consumer)
    override fun config(): Config = parent.config()
    override fun timer(name: String, tags: Iterable<Tag>): Timer = parent.timer(name.scoped, tags)
    override fun timer(name: String, vararg tags: String): Timer = parent.timer(name.scoped, *tags)
    override fun timer(name: String, build: UnaryOperator<Timer.Builder>): Timer = parent.timer(name.scoped, build)

    override fun more(): MeterRegistry.More = object : MeterRegistry.More {
        val more = parent.more()

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

    override fun gaugeWithStrongReference(name: String, number: Number): Gauge =
        parent.gaugeWithStrongReference(name.scoped, number)
    override fun gaugeWithStrongReference(name: String, tags: Iterable<Tag>, number: Number): Gauge =
        parent.gaugeWithStrongReference(name.scoped, tags, number)
    override fun <T : Number> gauge(name: String, number: T): T = parent.gauge(name.scoped, number)
    override fun <T> gauge(name: String, stateObject: T, valueFunction: ToDoubleFunction<T>): T =
        parent.gauge(name.scoped, stateObject, valueFunction)

    override fun <T> gauge(name: String, tags: Iterable<Tag>, stateObject: T, valueFunction: ToDoubleFunction<T>): T =
        parent.gauge(name.scoped, tags, stateObject, valueFunction)

    override fun <T : Number> gauge(name: String, tags: Iterable<Tag>, number: T): T = parent.gauge(name.scoped, tags, number)

    override fun <T : Collection<*>> gaugeCollectionSize(name: String, tags: Iterable<Tag>, collection: T): T =
        parent.gaugeCollectionSize(name.scoped, tags, collection)

    override fun <T : Map<*, *>> gaugeMapSize(name: String, tags: Iterable<Tag>, map: T): T = parent.gaugeMapSize(name.scoped, tags, map)

    override fun remove(meter: Meter): Meter? = parent.remove(meter)
    override fun remove(mappedId: Meter.Id): Meter? = parent.remove(mappedId.scoped)
    override fun clear() = parent.clear()
    override fun close() = parent.close()
    override fun isClosed() = parent.isClosed()

    // Functions below here are not for general metric handling, but needed by Styx internally.

    override fun startTimer(): Timer.Sample = parent.startTimer()
    override fun micrometerRegistry(): io.micrometer.core.instrument.MeterRegistry = parent.micrometerRegistry()
    override fun timerWithStyxDefaults(name: String, tags: Iterable<Tag>): Timer = parent.timerWithStyxDefaults(name.scoped, tags)

    private val String.scoped: String get() = "$scope.$this"
    private val Meter.Id.scoped: Meter.Id get() = withName(name.scoped)
}

class MicrometerRegistry(private val registry: io.micrometer.core.instrument.MeterRegistry) : MeterRegistry {
    override val meters: List<Meter> get() = registry.meters

    override fun counter(name: String, vararg tags: String): Counter = registry.counter(name, *tags)
    override fun counter(name: String, tags: Iterable<Tag>): Counter = registry.counter(name, tags)
    override fun summary(name: String, vararg tags: String): DistributionSummary = registry.summary(name, *tags)
    override fun summary(name: String, tags: Iterable<Tag>): DistributionSummary = registry.summary(name, tags)
    override fun summary(name: String, build: UnaryOperator<DistributionSummary.Builder>): DistributionSummary =
        build.apply(DistributionSummary.builder(name)).register(registry)

    override fun get(name: String): RequiredSearch = registry.get(name)
    override fun find(name: String): Search = registry.find(name)
    override fun forEachMeter(consumer: Consumer<Meter>) = registry.forEachMeter(consumer)
    override fun config(): Config = registry.config()
    override fun timer(name: String, tags: Iterable<Tag>): Timer = registry.timer(name, tags)
    override fun timer(name: String, vararg tags: String): Timer = registry.timer(name, *tags)
    override fun timer(name: String, build: UnaryOperator<Timer.Builder>): Timer = build.apply(Timer.builder(name)).register(registry)

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

    override fun gaugeWithStrongReference(name: String, number: Number): Gauge {
        return Gauge.builder(name, number, ToDoubleFunction(Number::toDouble))
            .strongReference(true)
            .register(registry)
    }

    override fun gaugeWithStrongReference(name: String, tags: Iterable<Tag>, number: Number): Gauge {
        return Gauge.builder(name, number, ToDoubleFunction(Number::toDouble))
            .tags(tags)
            .strongReference(true)
            .register(registry)
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

    // Functions below here are not for general metric handling, but needed by Styx internally.

    override fun startTimer(): Timer.Sample = Timer.start(registry)
    override fun micrometerRegistry(): io.micrometer.core.instrument.MeterRegistry = registry
    override fun timerWithStyxDefaults(name: String, tags: Iterable<Tag>): Timer = MeterFactory.timer(registry, name, tags)
}

private object MeterFactory {
    private val DEFAULT_MIN_HISTOGRAM_BUCKET = Duration.of(1, ChronoUnit.MILLIS)
    private val DEFAULT_MAX_HISTOGRAM_BUCKET = Duration.of(1, ChronoUnit.MINUTES)
    private const val MIN_ENV_VAR_NAME = "STYX_TIMER_HISTO_MIN"
    private const val MAX_ENV_VAR_NAME = "STYX_TIMER_HISTO_MIN"

    private val MIN_HISTOGRAM_BUCKET = System.getenv(MIN_ENV_VAR_NAME)?.toLong()?.let {
        Duration.of(it, ChronoUnit.MILLIS)
    } ?: DEFAULT_MIN_HISTOGRAM_BUCKET

    private val MAX_HISTOGRAM_BUCKET = System.getenv(MAX_ENV_VAR_NAME)?.toLong()?.let {
        Duration.of(it, ChronoUnit.MILLIS)
    } ?: DEFAULT_MAX_HISTOGRAM_BUCKET

    @JvmOverloads
    fun timer(registry: io.micrometer.core.instrument.MeterRegistry, name: String, tags: Iterable<Tag> = Tags.empty()): Timer =
        Timer.builder(name)
            .tags(tags)
            .publishPercentileHistogram()
            .minimumExpectedValue(MIN_HISTOGRAM_BUCKET)
            .maximumExpectedValue(MAX_HISTOGRAM_BUCKET)
            .register(registry)
}
