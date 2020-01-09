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
package com.hotels.styx.routing

import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.support.StyxServerProvider
import com.hotels.styx.support.proxyHttpHostHeader
import com.hotels.styx.support.wait
import io.kotlintest.Spec
import io.kotlintest.matchers.collections.shouldContainInOrder
import io.kotlintest.specs.FeatureSpec
import org.slf4j.LoggerFactory
import java.lang.ClassLoader.getSystemClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


class PluginsPipelineSpec : FeatureSpec() {
    private val LOGGER = LoggerFactory.getLogger(PluginsPipelineSpec::class.java)

    init {
        val tempPluginsDir = createTempDir(suffix = "-${this.javaClass.simpleName}")
        tempPluginsDir.deleteOnExit()

        val plugin = jarLocation("styx-test-plugin")
        val dependency = jarLocation("styx-test-plugin-dependencies")

        Files.copy(plugin, tempPluginsDir.resolve(plugin.fileName.toString()).toPath())
        Files.copy(dependency, tempPluginsDir.resolve(dependency.fileName.toString()).toPath())

        feature("Plugin selection") {
            scenario("Loads plugins for interceptor pipeline object") {

                styxServer.restart("""
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
                          port: 0

                    plugins:
                      all:
                        plugin-a:
                          factory:
                            class: testgrp.TestPluginModule
                            classPath: "$tempPluginsDir"
                          config: 
                            id: a
                        plugin-b:
                          factory:
                            class: testgrp.TestPluginModule
                            classPath: "$tempPluginsDir"
                          config: 
                            id: b
                        plugin-c:
                          factory:
                            class: testgrp.TestPluginModule
                            classPath: "$tempPluginsDir"
                          config: 
                            id: c
                             
                    httpPipeline:
                      type: InterceptorPipeline
                      config:
                          pipeline: plugin-a, plugin-c
                          handler:
                            type: StaticResponseHandler
                            config:
                              status: 200
                              content: "Hello, world!"
                        """.trimIndent())

                LOGGER.info("Proxy http address: ${styxServer().proxyHttpHostHeader()}")

                val response = client.send(get("/")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .build())
                        .wait()!!

                response.headers("X-Plugin-Identifier").shouldContainInOrder("c", "a")
            }
        }
    }

    val client: StyxHttpClient = StyxHttpClient.Builder().build()

    val styxServer = StyxServerProvider()

    fun jarLocation(module: String): Path {
        val parent = modulesDirectory()
                .resolve(module)
                .resolve("target");

        LOGGER.info("jarLocation($module): $parent")

        return Files.list(parent)
                .filter({ file -> file.toString().endsWith(".jar") })
                .filter({ file -> !file.toString().contains("-sources") })
                .findFirst()
                .orElseThrow { IllegalStateException("Cannot find any JAR at the specified location") }

    }

    fun modulesDirectory(): Path {
        return classPathRoot().getParent().getParent().getParent();
    }

    fun classPathRoot(): Path {
        return Paths.get(getSystemClassLoader().getResource("")!!.getFile());
    }

    override fun afterSpec(spec: Spec) {
        styxServer.stop()
        super.afterSpec(spec)
    }
}
