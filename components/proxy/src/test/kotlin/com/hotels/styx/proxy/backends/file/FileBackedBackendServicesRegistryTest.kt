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
package com.hotels.styx.proxy.backends.file

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.api.extension.Origin.newOriginBuilder
import com.hotels.styx.api.extension.service.BackendService
import com.hotels.styx.api.extension.service.ConnectionPoolSettings
import com.hotels.styx.api.extension.service.HealthCheckConfig.newHealthCheckConfigBuilder
import com.hotels.styx.api.extension.service.StickySessionConfig
import com.hotels.styx.api.extension.service.spi.Registry
import com.hotels.styx.applications.BackendServices.newBackendServices
import com.hotels.styx.common.StyxFutures
import com.hotels.styx.infrastructure.configuration.json.ObjectMappers.addStyxMixins
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.collections.shouldContainAll
import io.kotlintest.specs.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS


class FileBackedBackendServicesRegistryKotlinTest: DescribeSpec() {
    val Mapper = addStyxMixins(ObjectMapper(YAMLFactory()))

    val backendServiceOne = BackendService.Builder()
            .id("webapp")
            .origins(origin("webapp", "webapp-02", "localhost", 9091), origin("webapp", "webapp-01", "localhost", 9090))
            .connectionPoolConfig(ConnectionPoolSettings.Builder()
                    .connectTimeout(8000, MILLISECONDS)
                    .maxConnectionsPerHost(300)
                    .maxPendingConnectionsPerHost(50)
                    .build())
            .healthCheckConfig(newHealthCheckConfigBuilder()
                    .uri("/version.txt")
                    .interval(23456)
                    .timeout(2000)
                    .healthyThreshold(3)
                    .unhealthyThreshold(5)
                    .build())
            .stickySessionConfig(StickySessionConfig.Builder()
                    .enabled(true)
                    .timeout(86400, SECONDS)
                    .build())
            .build()


    fun origin(appId: String = "", id: String, host: String, port: Int): Origin {
        return newOriginBuilder(host, port)
                .applicationId(appId)
                .id(id)
                .build()
    }

    fun writeToFile(applications: List<BackendService>, path: String): File {
        val output = File(createOnMissing(path))
        Mapper.writer().writeValue(output, applications)
        return output
    }

    fun createOnMissing(path: String): String {
        val absolutePath = Paths.get(javaClass.getResource("/").toURI()).resolve(path).toFile()
        if (!absolutePath.getParentFile().exists()) {
            absolutePath.getParentFile().mkdirs()
        }
        return absolutePath.toString()
    }


    init {
        describe("A file backed backend services registry") {
            context("reading yaml content") {
                it("should read backend services configured in the yaml file") {
                    val registry = FileBackedBackendServicesRegistry.create("classpath:conf/origins/origins-for-jsontest.yml")

                    StyxFutures.await(registry.start())
                    val backendServices = registry.get()

                    assert(backendServices.toList().size == 2)
                    assert(backendServices.first() == backendServiceOne)

                    StyxFutures.await(registry.stop())
                }
            }

            context("Reloading backend services") {
                it("should reload the backend services when the registry is asked to do so") {
                    val genBackendOne = BackendService.Builder()
                            .id("shopping")
                            .path("/shop/")
                            .origins(origin("shopping", "shopping-01", "localhost", 9094))
                            .build()

                    var generatedBackendServices = writeToFile(listOf(genBackendOne), "backends/generated/single.yaml")

                    val registry = FileBackedBackendServicesRegistry.create(generatedBackendServices.toString())
                    StyxFutures.await(registry.start())

                    registry.get().toList().shouldContain(genBackendOne)

                    val genBackendTwo = BackendService.Builder()
                            .id("landing")
                            .path("/landing/")
                            .origins(origin("landing", "landing-01", "localhost", 9091))
                            .build()

                    writeToFile(listOf(genBackendOne, genBackendTwo), "backends/generated/single.yaml")

                    StyxFutures.await(registry.reload())

                    registry.get().toList().shouldContainAll(genBackendOne, genBackendTwo)

                    StyxFutures.await(registry.stop())
                }

                it("should reload multiple times if content changes") {
                    val genBackendOne = BackendService.Builder()
                            .id("shopping")
                            .path("/shop/")
                            .origins(origin("shopping", "shop-01", "localhost", 9094))
                            .build()


                    var generatedBackedServices = writeToFile(listOf(genBackendOne), "backends/generated/single.yaml")

                    val registry = FileBackedBackendServicesRegistry.create(generatedBackedServices.toString())
                    StyxFutures.await(registry.start())

                    registry.get().toList().shouldContain(genBackendOne)

                    val genBackendTwo = BackendService.Builder()
                            .id("landing")
                            .path("/landing/")
                            .origins(origin("landing", "landing-01", "localhost", 9091))
                            .build()

                    writeToFile(listOf(genBackendOne, genBackendTwo), "backends/generated/single.yaml")

                    StyxFutures.await(registry.reload())

                    registry.get().toList().shouldContainAll(genBackendOne, genBackendTwo)

                    writeToFile(listOf(genBackendOne), "backends/generated/single.yaml")
                    StyxFutures.await(registry.reload())

                    registry.get().toList().shouldContain(genBackendOne)

                    StyxFutures.await(registry.stop())
                }


                it("should not reload the backend services if content remains the same") {
                    val genBackendOne = BackendService.Builder()
                            .id("shopping")
                            .path("/shop/")
                            .origins(origin("shopping", "shop-01", "localhost", 9094))
                            .build()

                    var generatedBackedServices = writeToFile(listOf(genBackendOne), "backends/generated/single.yaml")

                    val listener = mockk<Registry.ChangeListener<BackendService>>()
                    every {
                        listener.onChange(any())
                    } returns Unit

                    val registry = FileBackedBackendServicesRegistry.create(generatedBackedServices.toString())

                    registry.addListener(listener)

                    StyxFutures.await(registry.start())

                    val changes = Registry.Changes.Builder<BackendService>()
                            .added(newBackendServices(genBackendOne))
                            .build()


                    verify(exactly = 1) { listener.onChange(changes) }

                    registry.get().toList().shouldContain(genBackendOne)

                    // update "last modified time" but don't change actual data
                    writeToFile(listOf(genBackendOne), "backends/generated/single.yaml")

                    registry.reload()

                    registry.get().toList().shouldContain(genBackendOne)

                    verify(exactly = 1) { listener.onChange(changes) }

                    StyxFutures.await(registry.stop())
                }

            }

        }

    }
}
