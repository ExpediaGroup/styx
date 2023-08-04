/*
  Copyright (C) 2013-2023 Expedia Inc.

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
package com.hotels.styx.client

import com.google.common.eventbus.EventBus
import com.google.common.eventbus.Subscribe
import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.Id
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.extension.ActiveOrigins
import com.hotels.styx.api.extension.Announcer
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.api.extension.OriginsChangeListener
import com.hotels.styx.api.extension.OriginsSnapshot
import com.hotels.styx.api.extension.RemoteHost
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor
import com.hotels.styx.client.healthcheck.monitors.NoOriginHealthStatusMonitor
import com.hotels.styx.client.origincommands.DisableOrigin
import com.hotels.styx.client.origincommands.EnableOrigin
import com.hotels.styx.client.origincommands.GetOriginsInventorySnapshot
import com.hotels.styx.common.EventProcessor
import com.hotels.styx.common.QueueDrainingEventProcessor
import com.hotels.styx.common.StateMachine
import com.hotels.styx.metrics.CentralisedMetrics
import com.hotels.styx.metrics.Deleter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import javax.annotation.concurrent.ThreadSafe

@ThreadSafe
abstract class OriginsInventory(
    protected val eventBus: EventBus,
    protected val originHealthStatusMonitor: OriginHealthStatusMonitor,
    private val appId: Id,
    private val metrics: CentralisedMetrics,
) : OriginHealthStatusMonitor.Listener, OriginsCommandsListener, ActiveOrigins,
    OriginsChangeListener.Announcer, Closeable, EventProcessor {
    protected abstract val eventQueue: QueueDrainingEventProcessor
    private val inventoryListeners = Announcer.to(OriginsChangeListener::class.java)
    private val closed = AtomicBoolean(false)
    private var monitoredOrigins: Map<Id, MonitoredOrigin> = emptyMap()

    init {
        register()
    }

    abstract fun registerEvent()

    abstract fun addOriginStatusListener()

    abstract fun Origin.toMonitoredOrigin(): MonitoredOrigin

    override fun getApplicationId(): String = appId.toString()

    override fun close() {
        eventQueue.submit(CloseEvent())
    }

    override fun originHealthy(origin: Origin) {
        eventQueue.submit(OriginHealthEvent(origin, HEALTHY))
    }

    override fun originUnhealthy(origin: Origin) {
        eventQueue.submit(OriginHealthEvent(origin, UNHEALTHY))
    }

    @Subscribe
    override fun onCommand(enableOrigin: EnableOrigin) {
        eventQueue.submit(EnableOriginCommand(enableOrigin))
    }

    @Subscribe
    override fun onCommand(disableOrigin: DisableOrigin) {
        eventQueue.submit(DisableOriginCommand(disableOrigin))
    }

    @Subscribe
    override fun onCommand(getOriginsInventorySnapshot: GetOriginsInventorySnapshot) {
        notifyStateChange()
    }

    override fun addOriginsChangeListener(listener: OriginsChangeListener) {
        inventoryListeners.addListener(listener)
    }

    override fun submit(event: Any) {
        when (event) {
            is SetOriginsEvent -> handleSetOriginsEvent(event)
            is OriginHealthEvent -> handleOriginHealthEvent(event)
            is EnableOriginCommand -> handleEnableOriginCommand(event)
            is DisableOriginCommand -> handleDisableOriginCommand(event)
            is CloseEvent -> handleCloseEvent()
        }
    }

    override fun snapshot(): Iterable<RemoteHost> = pools(OriginState.ACTIVE)

    override fun monitoringEnded(origin: Origin) {
        // Do Nothing
    }

    override fun origins(): List<Origin> =
        monitoredOrigins.values
            .map { monitoredOrigin -> monitoredOrigin.origin }
            .toList()

    fun register() {
        registerEvent()
        addOriginStatusListener()
    }

    /**
     * Registers origins with this inventory. Connection pools will be created for them and added to the "active" set,
     * they will begin being monitored, and event bus subscribers will be informed that the inventory state has changed.
     *
     * @param newOrigins origins to add
     */
    fun setOrigins(newOrigins: Set<Origin>) {
        check(newOrigins.isNotEmpty()) { "origins list is empty" }
        eventQueue.submit(SetOriginsEvent(newOrigins))
    }

    fun setOrigins(vararg newOrigins: Origin) {
        setOrigins(newOrigins.toSet())
    }

    fun originCount(state: OriginState): Int =
        monitoredOrigins.values
            .map { monitoredOrigin -> monitoredOrigin.state() }
            .count { other: OriginState -> state == other }

    fun closed(): Boolean = closed.get()

    fun notifyStateChange() {
        val event =
            OriginsSnapshot(appId, pools(OriginState.ACTIVE), pools(OriginState.INACTIVE), pools(
                OriginState.DISABLED
            ))
        inventoryListeners.announce().originsChanged(event)
        eventBus.post(event)
    }

    private fun handleSetOriginsEvent(event: SetOriginsEvent) {
        val newOriginsMap = event.newOrigins.associateBy { origin: Origin -> origin.id() }
        val originChanges = OriginChanges()
        (monitoredOrigins.keys + newOriginsMap.keys).toSet().forEach { originId: Id ->
            val newOrigin = newOriginsMap[originId]
            if (isNewOrigin(originId, newOrigin)) {
                val monitoredOrigin = addMonitoredEndpoint(newOrigin!!)
                originChanges.addOrReplaceOrigin(originId, monitoredOrigin)
            } else if (isUpdatedOrigin(originId, newOrigin)) {
                val monitoredOrigin = changeMonitoredEndpoint(newOrigin!!)
                originChanges.addOrReplaceOrigin(originId, monitoredOrigin)
            } else if (isUnchangedOrigin(originId, newOrigin)) {
                LOG.info("Existing origin has been left unchanged. Origin={}:{}", appId, newOrigin)
                originChanges.keepExistingOrigin(originId, monitoredOrigins[originId]!!)
            } else if (isRemovedOrigin(originId, newOrigin)) {
                removeMonitoredEndpoint(originId)
                originChanges.noteRemovedOrigin()
            }
        }
        monitoredOrigins = originChanges.updatedOrigins()
        if (originChanges.changed()) {
            notifyStateChange()
        }
    }

    private fun handleCloseEvent() {
        if (closed.compareAndSet(false, true)) {
            monitoredOrigins.values.forEach { monitoredOrigin ->
                removeMonitoredEndpoint(
                    monitoredOrigin.origin.id()
                )
            }
            monitoredOrigins = hashMapOf()
            notifyStateChange()
            eventBus.unregister(this)
        }
    }

    private fun handleDisableOriginCommand(event: DisableOriginCommand) {
        if (event.disableOrigin.forApp(appId)) {
            onEvent(event.disableOrigin.originId(), event.disableOrigin)
        }
    }

    private fun handleEnableOriginCommand(event: EnableOriginCommand) {
        if (event.enableOrigin.forApp(appId)) {
            onEvent(event.enableOrigin.originId(), event.enableOrigin)
        }
    }

    private fun handleOriginHealthEvent(event: OriginHealthEvent) {
        if (event.healthEvent === HEALTHY) {
            if (originHealthStatusMonitor !is NoOriginHealthStatusMonitor) {
                onEvent(event.origin, HEALTHY)
            }
        } else if (event.healthEvent === UNHEALTHY) {
            if (originHealthStatusMonitor !is NoOriginHealthStatusMonitor) {
                onEvent(event.origin, UNHEALTHY)
            }
        }
    }

    private fun addMonitoredEndpoint(origin: Origin): MonitoredOrigin {
        val monitoredOrigin = origin.toMonitoredOrigin()
        monitoredOrigin.startMonitoring()
        LOG.info("New origin added and activated. Origin={}:{}", appId, monitoredOrigin.origin.id())
        return monitoredOrigin
    }

    private fun changeMonitoredEndpoint(origin: Origin): MonitoredOrigin {
        val oldHost = monitoredOrigins[origin.id()]
        oldHost?.close()
        val newHost = origin.toMonitoredOrigin()
        newHost.startMonitoring()
        LOG.info("Existing origin has been updated. Origin={}:{}", appId, newHost.origin)
        return newHost
    }

    private fun removeMonitoredEndpoint(originId: Id) {
        val host = monitoredOrigins[originId]
        host?.close()
        LOG.info("Existing origin has been removed. Origin={}:{}", appId, host?.origin?.id())
    }

    private fun isNewOrigin(originId: Id, newOrigin: Origin?): Boolean =
        newOrigin != null && !monitoredOrigins.containsKey(originId)

    private fun isUnchangedOrigin(originId: Id, newOrigin: Origin?): Boolean {
        val oldOrigin = monitoredOrigins[originId]
        return oldOrigin != null && newOrigin != null && oldOrigin.origin == newOrigin
    }

    private fun isUpdatedOrigin(originId: Id, newOrigin: Origin?): Boolean {
        val oldOrigin = monitoredOrigins[originId]
        return oldOrigin != null && newOrigin != null && oldOrigin.origin != newOrigin
    }

    private fun isRemovedOrigin(originId: Id, newOrigin: Origin?): Boolean {
        val oldOrigin = monitoredOrigins[originId]
        return oldOrigin != null && newOrigin == null
    }

    private fun onEvent(origin: Origin, event: Any) {
        onEvent(origin.id(), event)
    }

    private fun onEvent(originId: Id, event: Any) {
        val monitoredOrigin = monitoredOrigins[originId]
        monitoredOrigin?.onEvent(event)
    }

    private fun pools(state: OriginState): Collection<RemoteHost> =
        monitoredOrigins.values
            .filter { monitoredOrigin -> monitoredOrigin.state() == state }
            .map { monitoredOrigin ->
                val hostClient = HttpHandler { request: LiveHttpRequest, context: HttpInterceptor.Context ->
                    Eventual(monitoredOrigin.hostClient.sendRequest(request, context))
                }
                RemoteHost.remoteHost(monitoredOrigin.origin, hostClient, monitoredOrigin.hostClient)
            }
            .toList()

    enum class OriginState(val gaugeValue: Int) {
        ACTIVE(1),
        INACTIVE(0),
        DISABLED(-1)
    }

    abstract inner class MonitoredOrigin(val origin: Origin) {
        abstract val hostClient: HostHttpClient
        private val machine: StateMachine<OriginState>
        private val statusGaugeDeleter: Deleter

        init {
            machine = StateMachine.Builder<OriginState>()
                .initialState(OriginState.ACTIVE)
                .onInappropriateEvent<Any> { state, _ -> state }
                .onStateChange { oldState: OriginState, newState: OriginState, _ ->
                    onStateChange(oldState, newState)
                }
                .transition(OriginState.ACTIVE, UnhealthyEvent::class.java) { OriginState.INACTIVE }
                .transition(OriginState.INACTIVE, HealthyEvent::class.java) { OriginState.ACTIVE }
                .transition(OriginState.ACTIVE, DisableOrigin::class.java) { OriginState.DISABLED }
                .transition(OriginState.INACTIVE, DisableOrigin::class.java) { OriginState.DISABLED }
                .transition(OriginState.DISABLED, EnableOrigin::class.java) { OriginState.INACTIVE }
                .build()
            statusGaugeDeleter = metrics.proxy.client.originHealthStatus(origin)
                .register { state().gaugeValue }
        }

        open fun close() {
            stopMonitoring()
            deregisterMeters()
            hostClient.close()
        }

        fun startMonitoring() {
            originHealthStatusMonitor.monitor(setOf(origin))
        }

        @Synchronized
        fun onEvent(event: Any) {
            machine.handle(event)
        }

        fun state(): OriginState = machine.currentState

        private fun stopMonitoring() {
            originHealthStatusMonitor.stopMonitoring(setOf(origin))
        }

        private fun onStateChange(oldState: OriginState, newState: OriginState) {
            if (oldState != newState) {
                LOG.info(
                    "Origin state change: origin=\"{}={}\", change=\"{}->{}\"",
                    appId,
                    origin.id(),
                    oldState,
                    newState
                )
                if (newState == OriginState.DISABLED) {
                    stopMonitoring()
                } else if (oldState == OriginState.DISABLED) {
                    startMonitoring()
                }
                notifyStateChange()
            }
        }

        private fun deregisterMeters() {
            statusGaugeDeleter.delete()
        }
    }

    private class SetOriginsEvent(val newOrigins: Set<Origin>)
    private class OriginHealthEvent(val origin: Origin, val healthEvent: Any)
    private class EnableOriginCommand(val enableOrigin: EnableOrigin)
    private class DisableOriginCommand(val disableOrigin: DisableOrigin)
    private class CloseEvent
    private class UnhealthyEvent
    private class HealthyEvent

    private inner class OriginChanges {
        var monitoredOrigins: MutableMap<Id, MonitoredOrigin> = hashMapOf()
        var changed = AtomicBoolean(false)
        fun addOrReplaceOrigin(originId: Id, origin: MonitoredOrigin) {
            monitoredOrigins[originId] = origin
            changed.set(true)
        }

        fun keepExistingOrigin(originId: Id, origin: MonitoredOrigin) {
            monitoredOrigins[originId] = origin
        }

        fun noteRemovedOrigin() {
            changed.set(true)
        }

        fun changed(): Boolean = changed.get()

        fun updatedOrigins(): Map<Id, MonitoredOrigin> = monitoredOrigins.toMap()
    }

    companion object {
        protected val LOG: Logger = LoggerFactory.getLogger(OriginsInventory::class.java)
        private val HEALTHY = HealthyEvent()
        private val UNHEALTHY = UnhealthyEvent()
    }
}
