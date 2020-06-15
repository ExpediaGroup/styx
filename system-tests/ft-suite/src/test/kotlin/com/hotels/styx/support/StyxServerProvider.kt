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
package com.hotels.styx.support

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.hotels.styx.StyxConfig
import com.hotels.styx.StyxServer
import com.hotels.styx.api.HttpHeaderNames
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.HttpResponse
import com.hotels.styx.api.HttpResponseStatus
import com.hotels.styx.api.HttpResponseStatus.CREATED
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.metrics.codahale.NoopMetricRegistry
import com.hotels.styx.api.plugins.spi.Plugin
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.routing.config.RoutingObjectFactory
import com.hotels.styx.startup.StyxServerComponents
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.slf4j.LoggerFactory
import reactor.core.publisher.toMono
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.util.HashMap
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

val defaultServerConfig = """                    
    proxy:
      connectors:
        http:
          port: 0

        https:
          port: 0
          sslProvider: JDK
          sessionTimeoutMillis: 300000
          sessionCacheSize: 20000

    admin:
      connectors:
        http:
          port: 0""".trimIndent()

val LOGGER = LoggerFactory.getLogger(StyxServerProvider::class.java)

private val logConfigPath = ResourcePaths.fixturesHome(StyxServerProvider::class.java, "/logback.xml")

class StyxServerProvider(
        val defaultConfig: String = defaultServerConfig,
        val defaultAdditionalRoutingObjects: Map<String, RoutingObjectFactory> = mapOf(),
        val defaultAdditionalPlugins: Map<String, Plugin> = mapOf(),
        val defaultLoggingConfig: Path? = logConfigPath,
        val validateConfig: Boolean = true) {
    val serverRef: AtomicReference<StyxServer?> = AtomicReference()
    val meterRegistryRef: AtomicReference<MeterRegistry?> = AtomicReference()

    operator fun invoke() = get()

    fun get(): StyxServer {
        return serverRef.get()!!
    }

    fun meterRegistry(): MeterRegistry {
        return meterRegistryRef.get()!!
    }

    fun started() = (serverRef.get() == null) || serverRef.get()!!.isRunning

    fun restart(
            configuration: String = this.defaultConfig,
            additionalRoutingObjects: Map<String, RoutingObjectFactory> = this.defaultAdditionalRoutingObjects,
            additionalPlugins: Map<String, Plugin> = this.defaultAdditionalPlugins,
            loggingConfig: Path? = this.defaultLoggingConfig,
            validateConfig: Boolean = this.validateConfig): StyxServerProvider {
        restartAsync(configuration, additionalRoutingObjects, additionalPlugins, loggingConfig, validateConfig)
        serverRef.get()?.awaitRunning()
        return this
    }

    fun restartAsync(
            configuration: String = this.defaultConfig,
            additionalRoutingObjects: Map<String, RoutingObjectFactory> = this.defaultAdditionalRoutingObjects,
            additionalPlugins: Map<String, Plugin> = this.defaultAdditionalPlugins,
            loggingConfig: Path? = this.defaultLoggingConfig,
            validateConfig: Boolean = this.validateConfig): StyxServerProvider {
        if (started()) {
            stop()
        }

        val meterRegistry = SimpleMeterRegistry()
        var components = StyxServerComponents.Builder()
                .registry(meterRegistry)
                .styxConfig(StyxConfig.fromYaml(configuration, validateConfig))
                .additionalRoutingObjects(additionalRoutingObjects)
                .plugins(additionalPlugins)

        LOGGER.info("restarted with logging config: $loggingConfig")
        components = if (loggingConfig != null) components.loggingSetUp(loggingConfig.toString()) else components

        val newServer = StyxServer(components.build())
        newServer.startAsync()
        meterRegistryRef.set(meterRegistry)
        serverRef.set(newServer)

        return this
    }


    fun stop() {
        serverRef.getAndSet(null)
                ?.let {
                    if (it.isRunning()) {
                        it.stopAsync().awaitTerminated()
                    }
                }
    }
}

val testClient: StyxHttpClient = StyxHttpClient.Builder().build()

fun StyxServerProvider.adminRequest(endpoint: String, debug: Boolean = false): HttpResponse = testClient
        .send(HttpRequest.get(endpoint)
                .header(HttpHeaderNames.HOST, this().adminHostHeader())
                .build())
        .wait(debug = debug)

fun CompletableFuture<HttpResponse>.wait(debug: Boolean = false) = this.toMono()
        .doOnNext {
            if (debug) {
                LOGGER.info("${it.status()} - ${it.headers()} - ${it.bodyAs(UTF_8)}")
            }
        }
        .block()

fun StyxServer.adminHostHeader() = "${this.adminHttpAddress().hostName}:${this.adminHttpAddress().port}"
fun StyxServer.proxyHttpHostHeader() = "localhost:${this.proxyHttpAddress().port}"
fun StyxServer.proxyHttpsHostHeader() = "localhost:${this.proxyHttpsAddress().port}"

//@Throws(IOException::class)
private fun decodeToMap(body: String): Map<String, Any> {
    val factory = JsonFactory()
    val mapper = ObjectMapper(factory)
    val typeRef = object : TypeReference<HashMap<String, Any>>() {

    }
    return mapper.readValue(body, typeRef)
}

fun flattenMetricsMap(metricsText: String) = decodeToMap(metricsText)
        .filter { it.value is Map<*, *> }
        .flatMap { (it.value as Map<*, *>).entries }
        .map { it.key to it.value }
        .filter { it.second != null }
        .toMap()

fun StyxServer.metrics(): Map<String, Map<String, Any>> {
    val metricsText = StyxHttpClient.Builder().build()
            .send(HttpRequest.get("/admin/metrics")
                    .header(HOST, this.adminHostHeader())
                    .build())
            .wait()!!
            .bodyAs(UTF_8)

    return flattenMetricsMap(metricsText) as Map<String, Map<String, Any>>
}

fun StyxServer.newRoutingObject(name: String, routingObject: String): HttpResponseStatus {
    val response = StyxHttpClient.Builder().build()
            .send(HttpRequest.put("/admin/routing/objects/$name")
                    .header(HOST, this.adminHostHeader())
                    .body(routingObject, UTF_8)
                    .build())
            .wait()

    if (response?.status() != CREATED) {
        LOGGER.debug("Object $name was not created. Response from server: ${response?.status()} - '${response?.bodyAs(UTF_8)}'")
    }

    return response?.status() ?: HttpResponseStatus.statusWithCode(666)
}

fun StyxServer.removeRoutingObject(name: String): HttpResponseStatus {
    val response = StyxHttpClient.Builder().build()
            .send(HttpRequest.delete("/admin/routing/objects/$name")
                    .header(HOST, this.adminHostHeader())
                    .build())
            .wait()

    if (response?.status() != OK) {
        LOGGER.debug("Object $name was not removed. Response from server: ${response?.status()} - '${response?.bodyAs(UTF_8)}'")
    }

    return response?.status() ?: HttpResponseStatus.statusWithCode(666)
}

fun StyxServer.routingObject(name: String, debug: Boolean = false): Optional<String> = StyxHttpClient.Builder().build()
        .send(HttpRequest.get("/admin/routing/objects/$name")
                .header(HOST, this.adminHostHeader())
                .build())
        .wait(debug)!!
        .let {
            if (it.status() == OK) {
                Optional.of(it.bodyAs(UTF_8))
            } else {
                Optional.empty()
            }
        }

fun StyxServer.routingObjects(debug: Boolean = false): Optional<String> = StyxHttpClient.Builder().build()
        .send(HttpRequest.get("/admin/routing/objects")
                .header(HOST, this.adminHostHeader())
                .build())
        .wait(debug)!!
        .let {
            if (it.status() == OK) {
                Optional.of(it.bodyAs(UTF_8))
            } else {
                Optional.empty()
            }
        }

fun StyxServer.serverPort(name: String, debug: Boolean = false) = testClient
        .send(HttpRequest.get("/admin/servers/$name/port")
                .header(HOST, this.adminHostHeader()).build())
        .wait()
        .bodyAs(UTF_8)
        .toInt()

fun threadNames() = Thread.getAllStackTraces().keys
        .map { it.name }

fun threadCount(namePattern: String) = threadNames()
        .filter { it.contains(namePattern) }
        .count()
