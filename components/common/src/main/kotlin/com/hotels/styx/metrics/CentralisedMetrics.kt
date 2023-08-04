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
package com.hotels.styx.metrics

import com.hotels.styx.api.HttpResponseStatus
import com.hotels.styx.api.MeterRegistry
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.common.SimpleCache
import io.micrometer.core.instrument.*

/**
 * All the metrics used in Styx are defined here. Please note that there may be additional metrics defined by any plugins used.
 *
 * ## General information
 *
 * Metrics that do not require tags are registered here, and accessible as values. For convenience when accessing from Java code,
 * the annotation @get:JvmName(...) is used, which defines a method name by which the value may be accessed.
 * For consistency, the name of the value and method must be the same.
 *
 * Metrics that do require tags are not registered here, but instead provide methods for registering them, with appropriate parameters.
 * If these parameters are not dependent upon configuration, the metrics may be cached here.
 * * Examples of dependent on configuration: app ID, origin ID, plugin name.
 * * Example of not-dependent on configuration (a.k.a. internal behaviour): response status code, request cancellation cause.
 *
 * ## Gauges
 * Gauges cannot be pre-registered here, as their registration requires a callback. Instead we implement an interface called "GaugeId",
 * which provides the ability to register the gauge, but not to change the name or tags after they have been defined.
 *
 * ## Counters
 * As counters are quite straightforward, we define them using the micrometer Counter class directly.
 *
 * ## Timers
 * For timers we use our own interface "TimerMetric" instead of the Timer interface from micrometer. This is to make the API more
 * intuitive to work with, and improve encapsulation.
 *
 * ## Metrics hierarchy
 *
 * |- OS
 * |- JVM
 * |- Proxy
 *   |- Server
 *   |- Client
 *   |- Plugin handling (not to be confused with metrics created by plugins themselves)
 *
 */
class CentralisedMetrics(val registry: MeterRegistry) {
    @get:JvmName("os")
    val os = OS()

    @get:JvmName("jvm")
    val jvm = JVM()

    @get:JvmName("proxy")
    val proxy = Proxy()

    inner class OS {
        /**
         * Maximum file descriptors (Unix-based system only).
         */
        @get:JvmName("maxFileDescriptorCount")
        val maxFileDescriptorCount: GaugeId = InnerGaugeId("os.fileDescriptors.max")

        /**
         * Open file descriptors (Unix-based system only).
         */
        @get:JvmName("openFileDescriptorCount")
        val openFileDescriptorCount: GaugeId = InnerGaugeId("os.fileDescriptors.open")

        /**
         * CPU usage by the JVM process running Styx - proportion of CPUs used.
         */
        @get:JvmName("processCpuLoad")
        val processCpuLoad: GaugeId = InnerGaugeId("os.process.cpu.load")

        /**
         * CPU time used by the JVM process running Styx.
         */
        @get:JvmName("processCpuTime")
        val processCpuTime: GaugeId = InnerGaugeId("os.process.cpu.time")

        /**
         * CPU usage as a whole on the system running the JVM process running Styx - proportion of CPUs used.
         */
        @get:JvmName("systemCpuLoad")
        val systemCpuLoad: GaugeId = InnerGaugeId("os.system.cpu.load")

        /**
         * Amount of free physical memory in bytes.
         */
        @get:JvmName("freePhysicalMemorySize")
        val freePhysicalMemorySize: GaugeId = InnerGaugeId("os.memory.physical.free")

        /**
         * Total amount of physical memory in bytes.
         */
        @get:JvmName("totalPhysicalMemorySize")
        val totalPhysicalMemorySize: GaugeId = InnerGaugeId("os.memory.physical.total")

        /**
         * The amount of virtual memory that is guaranteed to be available to the running process in bytes.
         */
        @get:JvmName("committedVirtualMemorySize")
        val committedVirtualMemorySize: GaugeId = InnerGaugeId("os.memory.virtual.committed")

        /**
         * The amount of free swap space in bytes.
         */
        @get:JvmName("freeSwapSpaceSize")
        val freeSwapSpaceSize: GaugeId = InnerGaugeId("os.swapSpace.free")

        /**
         * The total amount of swap space in bytes.
         */
        @get:JvmName("totalSwapSpaceSize")
        val totalSwapSpaceSize: GaugeId = InnerGaugeId("os.swapSpace.total")

    }

    inner class JVM {
        /**
         * The uptime of the Java virtual machine in milliseconds.
         */
        @get:JvmName("jvmUptime")
        val jvmUptime: GaugeId = InnerGaugeId("jvm.uptime")
    }

    inner class Proxy {
        @get:JvmName("server")
        val server = Server()

        @get:JvmName("client")
        val client = Client()

        @get:JvmName("plugins")
        val plugins = Plugins()

        /**
         * Measures the number of requests that have been received, but not yet fully responded to.
         */
        @get:JvmName("requestsInProgress")
        val requestsInProgress: GaugeId = InnerGaugeId("proxy.requestsInProgress")

        /**
         * Counts events in which Styx had to return an 'HTTP 500 Internal Server Error' due to an unidentified proxying failure.
         * Does not count responses from origins, or failures that occur inside plugins.
         */
        @get:JvmName("styxErrors")
        val styxErrors: Counter = registry.counter("proxy.unexpectedError")

        /**
         * Measures the latency of the proxying process, including plugins and communication with origins.
         *
         * Since this metric is being measured *inside* Styx itself, it cannot truly include everything that happens,
         * but the most time-consuming parts should be included.
         */
        @get:JvmName("requestLatency")
        val endToEndRequestLatency: TimerMetric = InnerTimer("proxy.latency")

        @get:JvmName("requestProcessingLatency")
        val requestProcessingLatency : TimerMetric = InnerTimer("proxy.request.latency")

        @get:JvmName("responseProcessingLatency")
        val responseProcessingLatency : TimerMetric = InnerTimer("proxy.response.latency")

        /**
         * Current amount of memory in use, divided by pooled/unpooled and direct/heap.
         */
        @get:JvmName("nettyMemory")
        val nettyMemory: NettyMemory = object : NettyMemory {
            /**
             * Bytes of direct memory used by pooled netty buffers.
             */
            override val pooledDirect: GaugeId = InnerGaugeId("proxy.netty.buffers.memory", Tags.of("allocator", "pooled", "memoryType", "direct"))

            /**
             * Bytes of heap memory used by pooled netty buffers.
             */
            override val pooledHeap: GaugeId = InnerGaugeId("proxy.netty.buffers.memory", Tags.of("allocator", "pooled", "memoryType", "heap"))

            /**
             * Bytes of direct memory used by unpooled netty buffers.
             */
            override val unpooledDirect: GaugeId =
                InnerGaugeId("proxy.netty.buffers.memory", Tags.of("allocator", "unpooled", "memoryType", "direct"))

            /**
             * Bytes of heap memory used by unpooled netty buffers.
             */
            override val unpooledHeap: GaugeId = InnerGaugeId("proxy.netty.buffers.memory", Tags.of("allocator", "unpooled", "memoryType", "heap"))
        }

        inner class Server {
            @get:JvmName("openssl")
            val openssl = OpenSSL()

            /**
             * Number of active TCP connections.
             */
            @get:JvmName("totalConnections")
            val totalConnections: GaugeId = InnerGaugeId("proxy.server.totalConnections")

            /**
             * Total number of requests received since Styx started running.
             */
            @get:JvmName("requestsReceived")
            val requestsReceived: Counter = registry.counter("proxy.server.requestsReceived")

            @get:JvmName("bytesReceived")
            val bytesReceived: Counter = registry.counter("proxy.server.bytesReceived")

            @get:JvmName("bytesSent")
            val bytesSent: Counter = registry.counter("proxy.server.bytesSent")

            /**
             * Number of request using the HTTP (not HTTPS) protocol.
             */
            @get:JvmName("httpRequests")
            val httpRequests: Counter = registry.counter("proxy.server.requests", "protocol", "http")

            /**
             * Number of request using the HTTPS (not HTTP) protocol.
             */
            @get:JvmName("httpsRequests")
            val httpsRequests: Counter = registry.counter("proxy.server.requests", "protocol", "https")

            /**
             * Number of responses using the HTTP (not HTTPS) protocol.
             */
            @get:JvmName("httpResponses")
            val httpResponses: SimpleCache<Int, Counter> = SimpleCache {
                registry.counter("proxy.server.responseProtocol", "protocol", "http", "statusCode", it.toString())
            }

            /**
             * Number of responses using the HTTPS (not HTTP) protocol.
             */
            @get:JvmName("httpsResponses")
            val httpsResponses: SimpleCache<Int, Counter> = SimpleCache {
                registry.counter("proxy.server.responseProtocol", "protocol", "https", "statusCode", it.toString())
            }

            private val requestsCancelledOnServer: SimpleCache<String, Counter> = SimpleCache {
                registry.counter("proxy.server.requests.cancelled", "cause", it)
            }

            /**
             * Counts request cancellations that happen at the server, i.e. coming in to Styx.
             *
             * They are tagged by cause.
             */
            fun requestsCancelled(cause: String): Counter = requestsCancelledOnServer[cause]

            private val responsesByStatus: SimpleCache<Int, Counter> = SimpleCache {
                registry.counter("proxy.server.responses", statusCodeTags(it))
            }

            /**
             * Counts all responses sent by Styx, tagged by status code.
             */
            fun responsesByStatus(statusCode: Int): Counter = responsesByStatus[statusCode]

            /**
             * Number of channels registered by the Styx proxy server on a given thread.
             */
            fun registeredChannelCount(thread: Thread): Counter = registry.counter("proxy.server.registeredChannelCount", thread.tags)

            /**
             * Distribution of connections that were closed by Styx because they were idle.
             */
            @get:JvmName("idleConnectionClosed")
            val idleConnectionClosed: DistributionSummary = registry.summary("proxy.server.connection.idleClosed")

            fun channelCount(thread: Thread): DistributionSummary = registry.summary("proxy.server.connection.channels", thread.tags)

            inner class OpenSSL {
                /**
                 * The current number of SSL sessions in the internal session cache.
                 */
                @get:JvmName("openSslSessionNumber")
                val openSslSessionNumber: GaugeId = InnerGaugeId("proxy.server.openssl.session.number")

                /**
                 * The number of started SSL/TLS handshakes in server mode.
                 */
                @get:JvmName("openSslSessionAccept")
                val openSslSessionAccept: GaugeId = InnerGaugeId("proxy.server.openssl.session.accept")

                /**
                 * The number of successfully established SSL/TLS sessions in server mode.
                 */
                @get:JvmName("openSslSessionAcceptGood")
                val openSslSessionAcceptGood: GaugeId = InnerGaugeId("proxy.server.openssl.session.acceptGood")

                /**
                 * The number of start renegotiations in server mode.
                 */
                @get:JvmName("openSslSessionAcceptRenegotiate")
                val openSslSessionAcceptRenegotiate: GaugeId = InnerGaugeId("proxy.server.openssl.session.acceptRenegotiate")

                /**
                 * The number of successfully reused sessions.
                 */
                @get:JvmName("openSslSessionHits")
                val openSslSessionHits: GaugeId = InnerGaugeId("proxy.server.openssl.session.hits")

                /**
                 * The number of sessions proposed by clients that were not found in the internal session cache in server mode.
                 */
                @get:JvmName("openSslSessionMisses")
                val openSslSessionMisses: GaugeId = InnerGaugeId("proxy.server.openssl.session.misses")

                /**
                 * The number of successfully retrieved sessions from the external session cache in server mode.
                 */
                @get:JvmName("openSslSessionCbHits")
                val openSslSessionCbHits: GaugeId = InnerGaugeId("proxy.server.openssl.session.cbHits")

                /**
                 * The number of sessions that were removed because the maximum session cache size was exceeded.
                 */
                @get:JvmName("openSslSessionCacheFull")
                val openSslSessionCacheFull: GaugeId = InnerGaugeId("proxy.server.openssl.session.cacheFull")

                /**
                 * The number of sessions proposed by clients and either found in the internal or external session cache in server mode, but that were invalid
                 * due to timeout.
                 */
                @get:JvmName("openSslSessionTimeouts")
                val openSslSessionTimeouts: GaugeId = InnerGaugeId("proxy.server.openssl.session.timeouts")

            }
        }

        inner class Client {
            /**
             * Number of client connections in use for a particular origin.
             */
            fun busyConnections(origin: Origin): GaugeId = InnerGaugeId("proxy.client.connectionpool.busyConnections", origin.tags)

            /**
             * Number of requests that are waiting for a client connection to a particular origin, but have not yet been connected because the pool is at
             * maximum size and all its connections are in use.
             */
            fun pendingConnections(origin: Origin): GaugeId = InnerGaugeId("proxy.client.connectionpool.pendingConnections", origin.tags)

            /**
             * Number of client connections that have been established to an origin, but are not in use. They are kept in a connection pool ready for use.
             */
            fun availableConnections(origin: Origin): GaugeId = InnerGaugeId("proxy.client.connectionpool.availableConnections", origin.tags)

            /**
             * Number of attempts to establish client connection to a particular origin.
             */
            fun connectionAttempts(origin: Origin): GaugeId = InnerGaugeId("proxy.client.connectionpool.connectionAttempts", origin.tags)

            /**
             * Number of failed attempts to establish client connection to a particular origin.
             */
            fun connectionFailures(origin: Origin): GaugeId = InnerGaugeId("proxy.client.connectionpool.connectionFailures", origin.tags)

            /**
             * Number of client connections to an origin closed by the proxy.client.
             */
            fun connectionsClosed(origin: Origin): GaugeId = InnerGaugeId("proxy.client.connectionpool.connectionsClosed", origin.tags)

            /**
             * Number of times that client connections have terminated, either because they were closed by styx, or by an origin, or otherwise disconnected.
             */
            fun connectionsTerminated(origin: Origin): GaugeId = InnerGaugeId("proxy.client.connectionpool.connectionsTerminated", origin.tags)

            /**
             * Number of connections being established at this moment. This means performing a TCP handshake or an SSL/TLS handshake procedure.
             */
            fun connectionsInEstablishment(origin: Origin): GaugeId =
                InnerGaugeId("proxy.client.connectionpool.connectionsInEstablishment", origin.tags)

            /**
             * The health status of a given origin. The values can be understood as follows:
             *
             * *  1 - ACTIVE,
             * *  0 - INACTIVE
             * * -1 - DISABLED
             *
             * Note that if health-checks are disabled (which is valid if there is only one origin and/or the origin is itself a balanced cluster),
             * all origins will be marked as "active" regardless of how they are performing.
             */
            fun originHealthStatus(origin: Origin): GaugeId = InnerGaugeId("proxy.client.originHealthStatus", origin.tags)

            /**
             * Number of health check failures per origin.
             */
            @get:JvmName("originHealthCheckFailures")
            val originHealthCheckFailures: SimpleCache<Origin, Counter> = SimpleCache {
                registry.counter("proxy.client.originHealthCheckFailures", it.tags)
            }

            /**
             * Counts request cancellations that happen at the client, i.e. sent out from Styx.
             *
             * They are tagged by origin.
             */
            fun requestsCancelled(origin: Origin): Counter = registry.counter("proxy.client.requests.cancelled", origin.tags)

            private val clientOriginErrorResponseByStatus: SimpleCache<Int, Counter> = SimpleCache {
                registry.counter("proxy.client.responseCode.errorStatus", "statusCode", it.toString())
            }

            /**
             * Responses that have an error code of 4xx or 5xx.
             */
            fun errorResponseFromOriginByStatus(statusCode: Int): Counter = clientOriginErrorResponseByStatus[statusCode]

            private val backendFaults: SimpleCache<BackendFaultKey, Counter> = SimpleCache {
                registry.counter("proxy.client.backends.fault", it.tags())
            }

            /**
             * Counts proxying failures caused by an external problem when trying to communicating with an application.
             *
             * Tagged only by application, because this function is for events that prevent a suitable origin from being chosen for this application.
             */
            fun backendFaults(applicationId: String, faultType: String): Counter = backendFaults(applicationId, null, faultType)

            /**
             * Counts proxying failures caused by an external problem when communicating with an origin.
             *
             * Tagged by origin.
             */
            fun backendFaults(applicationId: String, originId: String?, faultType: String): Counter =
                backendFaults[BackendFaultKey(applicationId, originId, faultType)]

            /**
             * Counts the number of requests to an origin that were responded to with non-server-error status (not code 5xx).
             */
            fun originResponseNot5xx(origin: Origin): Counter = registry.counter("proxy.client.responseCode.success", origin.tags)

            /**
             * Counts the number of requests to an origin that responded to with a server error status (code 5xx).
             */
            fun originResponse5xx(origin: Origin): Counter = registry.counter("proxy.client.responseCode.error", origin.tags)

            /**
             * Counts responses from an origin by status code.
             */
            fun responsesByStatus(origin: Origin): SimpleCache<Int, Counter> = SimpleCache {
                registry.counter("proxy.client.response.statuscode", statusCodeTags(it).and(origin.tags))
            }

            /**
             * Measures the latency of communicating with origins, excluding anything before or after that (like plugins).
             */
            fun originRequestLatency(origin: Origin): TimerMetric = InnerTimer("proxy.client.latency", origin.tags)

            /**
             * A time measurement starting when a request is first sent to an origin, and stopping when the first byte of content (the response body)
             * is returned from the origin.
             */
            fun timeToFirstByte(origin: Origin): TimerMetric = InnerTimer("proxy.client.timeToFirstByte", origin.tags)

            /**
             * Number of ongoing requests for a particular origin.
             */
            fun ongoingRequests(origin: Origin): GaugeId = InnerGaugeId("proxy.client.ongoingRequests", origin.tags)
        }

        inner class Plugins {
            /**
             * Counts events in which a plugin threw an exception or had to return a 'HTTP 500 Internal Server Error'.
             * Does not count responses from origins.
             */
            fun errors(plugin: String): Counter = registry.counter("proxy.plugins.errors", "plugin", plugin)

            /**
             * Counts exceptions thrown by a plugin. The dots in the name of the exception class will be replaced by underscores.
             */
            fun exceptions(plugin: String): SimpleCache<Class<out Throwable>, Counter> = SimpleCache { type ->
                registry.counter("proxy.plugins.exceptions", "plugin", plugin, "type", type.formattedName)
            }

            /**
             * Counts status codes emitted by a plugin if they are 400+ (a client or server error).
             *
             * Also counts a 'HTTP 500 Internal Server Error' when the plugin throws an exception.
             */
            fun errorStatus(plugin: String): SimpleCache<HttpResponseStatus, Counter> = SimpleCache { status ->
                registry.counter("proxy.plugins.errorResponses", "plugin", plugin, "statusCode", status.code().toString())
            }
        }
    }

    private inner class InnerGaugeId(val name: String, val tags: Tags = Tags.empty()) : GaugeId {
        override fun <T> register(stateObject: T, function: (T) -> Number) {
            registry.gauge(name, tags, stateObject) {
                function(it!!).toDouble()
            }
        }

        override fun register(supplier: () -> Int): Deleter = InnerDeleter(
            Gauge.builder(name, supplier).tags(tags).register(registry.micrometerRegistry())
        )

        override fun register(number: Number) {
            registry.gauge(name, number)
        }
    }

    private inner class InnerDeleter(val gauge: Gauge) : Deleter {
        override fun delete() {
            registry.remove(gauge)
        }
    }

    private inner class InnerTimer(name: String, tags: Tags = Tags.empty()) : TimerMetric {
        private val timer = registry.timerWithStyxDefaults(name, tags)

        override fun startTiming() = InnerStopper(registry.startTimer())

        inner class InnerStopper(private val startTime: Timer.Sample) : TimerMetric.Stopper {
            override fun stop() {
                startTime.stop(timer)
            }
        }
    }
}

private data class BackendFaultKey(val applicationId: String, val originId: String?, val faultType: String) {
    fun tags(): Tags {
        val originTag = originId?.let { Tags.of("origin", originId) } ?: Tags.empty()

        return Tags.of("application", applicationId).and("faultType", faultType).and(originTag)
    }
}

private val Origin.tags get() = Tags.of("appId", appId(), "originId", originId())
private fun Origin.appId() = applicationId().toString()
private fun Origin.originId() = id().toString()

private fun statusCodeTags(code: Int): Tags =
    if (code in 100..599) {
        Tags.of(
            "statusClass", (code / 100).toString() + "xx",
            "statusCode", code.toString()
        )
    } else {
        Tags.of(
            "statusClass", "unrecognised",
            "statusCode", "unrecognised"
        )
    }

private val Class<out Throwable>.formattedName get() = name.replace('.', '_')
private val Thread.tags get() = Tags.of("eventloop", name)

interface GaugeId {
    fun <T> register(stateObject: T, function: (T) -> Number)

    fun register(supplier: () -> Int): Deleter

    fun register(number: Number)
}

interface Deleter {
    fun delete()
}

interface TimerMetric {
    fun startTiming(): Stopper

    interface Stopper {
        fun stop()
    }
}

/**
 * Just used to group similar metrics together.
 */
interface NettyMemory {
    val pooledDirect: GaugeId
    val pooledHeap: GaugeId
    val unpooledDirect: GaugeId
    val unpooledHeap: GaugeId
}
