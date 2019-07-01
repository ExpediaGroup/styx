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
package com.hotels.styx.support

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.hotels.styx.StyxConfig
import com.hotels.styx.StyxServer
import com.hotels.styx.api.HttpHeaderNames
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.HttpResponse
import com.hotels.styx.api.HttpResponseStatus
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.startup.StyxServerComponents
import reactor.core.publisher.toMono
import java.nio.charset.StandardCharsets
import java.util.HashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

class StyxServerProvider(val defaultConfig: String) {
    val serverRef: AtomicReference<StyxServer?> = AtomicReference()

    operator fun invoke() = get()

    fun get(): StyxServer {
        if (!started()) {
            restart()
        }

        return serverRef.get()!!
    }

    fun started() = (serverRef.get() == null) || serverRef.get()!!.isRunning

    fun restart(configuration: String = defaultConfig): StyxServerProvider {
        if (started()) {
            stop()
        }

        val newServer = StyxServer(StyxServerComponents.Builder()
                .styxConfig(StyxConfig.fromYaml(configuration))
                .build())
        newServer.startAsync()?.awaitRunning()

        serverRef.set(newServer)
        return this
    }

    fun stop(): Unit {
        val oldServer = serverRef.get()
        if (oldServer?.isRunning ?: false) {
            oldServer!!.stopAsync().awaitTerminated()
        }
    }
}

fun CompletableFuture<HttpResponse>.wait(debug: Boolean = true) = this.toMono()
        .doOnNext {
            if (debug) {
                println("${it.status()} - ${it.headers()} - ${it.bodyAs(StandardCharsets.UTF_8)}")
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
                    .header(HttpHeaderNames.HOST, this.adminHostHeader())
                    .build())
            .wait()!!
            .bodyAs(StandardCharsets.UTF_8)

    return flattenMetricsMap(metricsText) as Map<String, Map<String, Any>>
}

fun StyxServer.newRoutingObject(name: String, routingObject: String): HttpResponseStatus {
    val response = StyxHttpClient.Builder().build()
            .send(HttpRequest.put("/admin/routing/objects/$name")
                    .header(HttpHeaderNames.HOST, this.adminHostHeader())
                    .body(routingObject, StandardCharsets.UTF_8)
                    .build())
            .wait()

    if (response?.status() != HttpResponseStatus.CREATED) {
        println("Object $name was not created. Response from server: ${response?.status()} - '${response?.bodyAs(StandardCharsets.UTF_8)}'")
    }

    return response?.status() ?: HttpResponseStatus.statusWithCode(666)
}

fun StyxServer.removeRoutingObject(name: String): HttpResponseStatus {
    val response = StyxHttpClient.Builder().build()
            .send(HttpRequest.delete("/admin/routing/objects/$name")
                    .header(HttpHeaderNames.HOST, this.adminHostHeader())
                    .build())
            .wait()

    if (response?.status() != HttpResponseStatus.OK) {
        println("Object $name was not removed. Response from server: ${response?.status()} - '${response?.bodyAs(StandardCharsets.UTF_8)}'")
    }

    return response?.status() ?: HttpResponseStatus.statusWithCode(666)
}

fun threadCount(namePattern: String) = Thread.getAllStackTraces().keys
        .map { it.name }
        .filter { it.contains(namePattern) }
        .count()
