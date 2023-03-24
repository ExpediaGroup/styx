package com.hotels.styx

import com.hotels.styx.api.MeterRegistry
import com.hotels.styx.metrics.CentralisedMetrics
import com.hotels.styx.metrics.TimerMetric
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import java.util.concurrent.TimeUnit.MILLISECONDS
import io.micrometer.core.instrument.MeterRegistry as RawMeterRegistry


fun <T : Meter> List<T>.sortById(): List<T> = sortedBy { it.id.name }

@JvmOverloads
fun dumpMeters(meters: List<Meter>, title: String = "Meters") {
    println("$title: {")
    meters.sortById().forEach { meter ->
        val extra = if(meter is Counter) {
            " count=${meter.count()}"
        } else {
            ""
        }
        println("  id=${meter.id}$extra")
    }
    println("}")
}

@JvmOverloads
fun dumpTimers(meters: List<Meter>, title: String = "Timers") {
    println("$title: {")
    meters.filterIsInstance<Timer>().sortById().forEach { timer ->
        println("  id=${timer.id} count=${timer.count()} time(ms)=${timer.totalTime(MILLISECONDS)}")
    }
    println("}")
}

fun timerExperiment(title: String, rawMeterRegistry: RawMeterRegistry) {
    val myTimer = rawMeterRegistry.timer(title.timerName())
    val timing = Timer.start(rawMeterRegistry)
    timing.stop(myTimer)
    dumpTimers(rawMeterRegistry.meters, title)
    rawMeterRegistry.counter("foo_bar").increment()
    dumpMeters(rawMeterRegistry.meters, "$title: all meters")
}

@JvmOverloads
fun timerExperiment(title: String, registry: MeterRegistry, styxDefaults: Boolean = false) {
    val myTimer = if(styxDefaults) {
        registry.timerWithStyxDefaults(title.timerName() + "_StyxDefaults", Tags.empty())
    } else {
        registry.timer(title.timerName(), Tags.empty())
    }
    val timing = registry.startTimer()
    timing.stop(myTimer)
    dumpTimers(registry.meters, title)
}

fun timerExperiment(metrics: CentralisedMetrics) {
    val timing = metrics.proxy.requestProcessingLatency.startTiming()
    timing.stop()
    dumpTimers(metrics.registry.meters, "CentralisedMetrics (${metrics.proxy.requestProcessingLatency})")
}

private fun String.timerName() = "Timer_" + replace(Regex("[^a-zA-Z]+"), "")