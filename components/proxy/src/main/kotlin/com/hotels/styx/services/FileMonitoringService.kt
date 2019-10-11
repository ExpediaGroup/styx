/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx.services

import com.hotels.styx.api.extension.service.spi.AbstractStyxService
import com.hotels.styx.proxy.backends.file.FileChangeMonitor
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.CompletableFuture

private val oneSecond = Duration.ofSeconds(1)

class FileMonitoringService(
        name: String,
        val path: String,
        pollInterval: Duration = oneSecond,
        val action: (String) -> Unit
        ) : AbstractStyxService(name) {
    val LOGGER = LoggerFactory.getLogger(FileMonitoringService::class.java)

    // NOTE: FileChangeMonitor rejects any non-existing paths:
    val monitor = FileChangeMonitor(path, Duration.ofSeconds(0), pollInterval)

    override fun startService() = CompletableFuture.runAsync {
        monitor.start {
            reload()
        }
    }

    override fun stopService() = CompletableFuture.runAsync {
        monitor.stop()
    }

    private fun reload() {
        runCatching {
            Files.readAllBytes(Paths.get(path))
        }.mapCatching {
            val string = String(it, UTF_8)
            action.invoke(string)
        }.onFailure {
            LOGGER.warn("Unable to read file {}. Cause={}", path, it.localizedMessage)
        }
    }
}
