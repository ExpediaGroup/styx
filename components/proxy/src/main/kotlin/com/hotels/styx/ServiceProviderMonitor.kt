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
package com.hotels.styx;

import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import com.google.common.util.concurrent.ServiceManager.Listener
import com.hotels.styx.StyxServers.toGuavaService
import com.hotels.styx.api.extension.service.spi.AbstractStyxService
import com.hotels.styx.api.extension.service.spi.StyxService
import com.hotels.styx.routing.db.StyxObjectStore
import org.slf4j.LoggerFactory.getLogger
import java.lang.IllegalStateException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

internal class ServiceProviderMonitor<T : StyxObjectRecord<out StyxService>>(name: String, val servicesDatabase: StyxObjectStore<T>)
    : AbstractStyxService(name) {

    companion object {
        private val LOG = getLogger(ServiceProviderMonitor::class.java)
    }

    private val services = AtomicReference<Map<String, T>>()
    private val manager = AtomicReference<ServiceManager>()

    override fun startService(): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()

        services.set(servicesDatabase.entrySet()
                .map { it.key to it.value }
                .toMap())

        manager.set(ServiceManager(services.get().values.map { toGuavaService(it.styxService) }))
        manager.get().addListener(object : Listener() {
            override fun healthy() {
                future.complete(null);
            }

            override fun failure(service: Service) {
                future.completeExceptionally(service.failureCause())
            }
        })

        manager.get().startAsync()

        return future;
    }

    override fun stopService(): CompletableFuture<Void> = CompletableFuture.runAsync {
        if (manager.get() == null) {
            throw IllegalStateException("ServiceProviderMonitor ${serviceName()} is not RUNNING.")
        }
        manager.get().stopAsync().awaitStopped()
    }
}
