package com.hotels.styx;

import com.hotels.styx.api.extension.service.spi.AbstractStyxService
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.handlers.ProviderObjectRecord
import org.slf4j.LoggerFactory.getLogger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference


internal class ServiceProviderMonitor(name: String, val serviceProviderDatabase: StyxObjectStore<ProviderObjectRecord>) : AbstractStyxService(name) {

    companion object {
        private val LOG = getLogger(ServiceProviderMonitor::class.java)
    }

    private val services = AtomicReference<Map<String, ProviderObjectRecord>>()

    override fun startService(): CompletableFuture<Void> = CompletableFuture.runAsync {
                services.set(serviceProviderDatabase
                        .entrySet()
                        .map { it.key to it.value }
                        .toMap())

                services.get()
                        .forEach { name, record ->
                            val service = record.styxService
                            service.start()
                                    .thenAccept({ ignore -> LOG.debug("Service '{}/{}' started", record.type, name) })
                        }
            }

    override fun stopService(): CompletableFuture<Void> = CompletableFuture.runAsync {
                services.get()
                        .forEach { name, record ->
                            val service = record.styxService
                            service.stop()
                                    .thenAccept({ ignore -> LOG.debug("Service '{}/{}' stopped", record.type, name) })
                        }
            }
}
