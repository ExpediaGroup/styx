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
package com.hotels.styx.metrics.reporting

import com.hotels.styx.metrics.CentralisedMetrics
import com.sun.management.OperatingSystemMXBean
import com.sun.management.UnixOperatingSystemMXBean
import java.lang.management.ManagementFactory.getOperatingSystemMXBean

fun CentralisedMetrics.registerOperatingSystemMetrics() {
    os.run {
        val bean = getOperatingSystemMXBean() as OperatingSystemMXBean

        bean.ifUnix {
            maxFileDescriptorCount.register(it, UnixOperatingSystemMXBean::getMaxFileDescriptorCount)
            openFileDescriptorCount.register(it, UnixOperatingSystemMXBean::getOpenFileDescriptorCount)
        }

        processCpuLoad.register(bean, OperatingSystemMXBean::getProcessCpuLoad)
        processCpuTime.register(bean, OperatingSystemMXBean::getProcessCpuTime)
        systemCpuLoad.register(bean, OperatingSystemMXBean::getSystemCpuLoad)

        freePhysicalMemorySize.register(bean, OperatingSystemMXBean::getFreePhysicalMemorySize)
        totalPhysicalMemorySize.register(bean, OperatingSystemMXBean::getTotalPhysicalMemorySize)
        committedVirtualMemorySize.register(bean, OperatingSystemMXBean::getCommittedVirtualMemorySize)

        freeSwapSpaceSize.register(bean, OperatingSystemMXBean::getFreeSwapSpaceSize)
        totalSwapSpaceSize.register(bean, OperatingSystemMXBean::getTotalSwapSpaceSize)
    }
}

private fun OperatingSystemMXBean.ifUnix(block: (UnixOperatingSystemMXBean) -> Unit) {
    if (this is UnixOperatingSystemMXBean) {
        block(this)
    }
}
