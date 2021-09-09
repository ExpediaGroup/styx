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
package com.hotels.styx.startup

import com.hotels.styx.metrics.CentralisedMetrics
import com.hotels.styx.metrics.reporting.registerOperatingSystemMetrics
import io.micrometer.core.instrument.binder.MeterBinder
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.netty.buffer.PooledByteBufAllocator
import io.netty.buffer.UnpooledByteBufAllocator
import java.lang.management.ManagementFactory.getRuntimeMXBean

fun CentralisedMetrics.registerCoreMetrics() {
    registerJvmMetrics()
    registerOperatingSystemMetrics()
}

private fun CentralisedMetrics.registerJvmMetrics() {
    registerAll(JvmMemoryMetrics())
    registerAll(JvmThreadMetrics())
    registerAll(JvmGcMetrics())
    registerAll(ClassLoaderMetrics())

    jvm.jvmUptime.register(getRuntimeMXBean()) { it.uptime }

    val pooled = PooledByteBufAllocator.DEFAULT.metric()
    val unpooled = UnpooledByteBufAllocator.DEFAULT.metric()

    proxy.nettyMemory.run {
        pooledDirect.register(pooled) { it.usedDirectMemory() }
        pooledHeap.register(pooled) { it.usedHeapMemory() }
        unpooledDirect.register(unpooled) { it.usedDirectMemory() }
        unpooledHeap.register(unpooled) { it.usedHeapMemory() }
    }
}

private fun CentralisedMetrics.registerAll(binder: MeterBinder) = registry.micrometerRegistry()?.let {
    binder.bindTo(it)
} ?: throw IllegalStateException("Cannot use micrometer binder as no micrometer registry is present")
