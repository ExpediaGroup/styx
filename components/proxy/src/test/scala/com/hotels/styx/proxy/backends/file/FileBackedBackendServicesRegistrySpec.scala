/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.proxy.backends.file

import java.io.File
import java.nio.file.Paths
import java.util.Arrays.asList
import java.util.concurrent.TimeUnit.{MILLISECONDS, SECONDS}

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.hotels.styx.api.client.Origin
import com.hotels.styx.api.io.ResourceFactory.newResource
import com.hotels.styx.client.applications.BackendServices.newBackendServices
import com.hotels.styx.client.connectionpool.ConnectionPoolSettings
import com.hotels.styx.client.healthcheck.HealthCheckConfig.newHealthCheckConfigBuilder
import com.hotels.styx.client.stickysession.StickySessionConfig
import com.hotels.styx.common.StyxFutures
import com.hotels.styx.infrastructure.Registry
import com.hotels.styx.infrastructure.Registry.Changes
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.mockito.Mockito.{mock, times, verify, verifyNoMoreInteractions}
import org.scalatest.FunSpec
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
class FileBackedBackendServicesRegistrySpec extends FunSpec with Eventually {
  val LOG = LoggerFactory.getLogger("FileBackedRoutesSupplierSpec")

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(2, Seconds)), interval = scaled(Span(500, Millis)))

  val backendServiceOne = new com.hotels.styx.client.applications.BackendService.Builder()
    .id("webapp")
    .origins(origin("webapp", "webapp-02", "localhost", 9091), origin("webapp", "webapp-01", "localhost", 9090))
    .connectionPoolConfig(new ConnectionPoolSettings.Builder()
      .connectTimeout(8000, MILLISECONDS)
      .socketTimeout(120000, MILLISECONDS)
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
    .stickySessionConfig(new StickySessionConfig.Builder()
      .enabled(true)
      .timeout(86400, SECONDS)
      .build())
    .build()

  def origin(appId: String = "", id: String, host: String, port: Int): Origin = {
    Origin.newOriginBuilder(host, port)
      .applicationId(appId)
      .id(id)
      .build()
  }

  val Mapper = new ObjectMapper(new YAMLFactory())

  describe("A file backed backend services registry") {
    describe("reading yaml content") {
      it("should read backend services configured in the yaml file") {
        val registry = new FileBackedBackendServicesRegistry(newResource("classpath:conf/origins/origins-for-jsontest.yml"))

        StyxFutures.await(registry.start())
        val backendServices = registry.get().asScala

        assert(backendServices.size == 2)
        assert(backendServices.head == backendServiceOne)

        StyxFutures.await(registry.stop())
      }
    }

    describe("Reloading backend services") {

      it("should reload the backend services when the registry is asked to do so") {
        val genBackendOne = new com.hotels.styx.client.applications.BackendService.Builder()
          .id("shopping")
          .path("/shop/")
          .origins(origin("shopping", "shopping-01", "localhost", 9094))
          .build()

        var generatedBackendServices = writeToFile(asList(genBackendOne), "backends/generated/single.yaml")

        val registry = new FileBackedBackendServicesRegistry(newResource(generatedBackendServices.toString))
        StyxFutures.await(registry.start())

        assertThat(registry.get(), contains(genBackendOne))

        val genBackendTwo = new com.hotels.styx.client.applications.BackendService.Builder()
          .id("landing")
          .path("/landing/")
          .origins(origin("landing", "landing-01", "localhost", 9091))
          .build()

        generatedBackendServices = writeToFile(asList(genBackendOne, genBackendTwo), "backends/generated/single.yaml")

        StyxFutures.await(registry.reload())

        assertThat(registry.get(), contains(genBackendOne, genBackendTwo))

        StyxFutures.await(registry.stop())
      }


      it("should reload multiple times if content changes") {
        val genBackendOne = new com.hotels.styx.client.applications.BackendService.Builder()
          .id("shopping")
          .path("/shop/")
          .origins(origin("shopping", "shop-01", "localhost", 9094))
          .build()


        var generatedBackedServices = writeToFile(asList(genBackendOne), "backends/generated/single.yaml")

        val registry = new FileBackedBackendServicesRegistry(newResource(generatedBackedServices.toString))
        StyxFutures.await(registry.start())

        assertThat(registry.get(), contains(genBackendOne))

        val genBackendTwo = new com.hotels.styx.client.applications.BackendService.Builder()
          .id("landing")
          .path("/landing/")
          .origins(origin("landing", "landing-01", "localhost", 9091))
          .build()

        generatedBackedServices = writeToFile(asList(genBackendOne, genBackendTwo), "backends/generated/single.yaml")

        StyxFutures.await(registry.reload())

        assertThat(registry.get(), contains(genBackendOne, genBackendTwo))

        generatedBackedServices = writeToFile(asList(genBackendOne), "backends/generated/single.yaml")
        StyxFutures.await(registry.reload())

        assertThat(registry.get(), contains(genBackendOne))

        StyxFutures.await(registry.stop())
      }


      it("should not reload the backend services if content remains the same") {
        val genBackendOne = new com.hotels.styx.client.applications.BackendService.Builder()
          .id("shopping")
          .path("/shop/")
          .origins(origin("shopping", "shop-01", "localhost", 9094))
          .build()

        var generatedBackedServices = writeToFile(asList(genBackendOne), "backends/generated/single.yaml")

        val listener = mock(classOf[Registry.ChangeListener[com.hotels.styx.client.applications.BackendService]])

        val registry = new FileBackedBackendServicesRegistry(newResource(generatedBackedServices.toString))

        registry.addListener(listener)

        StyxFutures.await(registry.start())

        val changes = new Changes.Builder[com.hotels.styx.client.applications.BackendService]()
          .added(newBackendServices(genBackendOne))
          .build()

        verify(listener, times(1)).onChange(changes)
        assertThat(registry.get(), contains(genBackendOne))

        // update "last modified time" but don't change actual data
        generatedBackedServices = writeToFile(asList(genBackendOne), "backends/generated/single.yaml")

        registry.reload()

        assertThat(registry.get(), contains(genBackendOne))
        verifyNoMoreInteractions(listener)

        StyxFutures.await(registry.stop())
      }
    }
  }

  def writeToFile(applications: java.util.List[com.hotels.styx.client.applications.BackendService], path: String): File = {
    val output: File = new File(createOnMissing(path))
    Mapper.writer().writeValue(output, applications)
    output
  }


  def createOnMissing(path: String): String = {
    val absolutePath = Paths.get(getClass.getResource("/").toURI).resolve(path).toFile
    if (!absolutePath.getParentFile.exists()) {
      absolutePath.getParentFile.mkdirs()
    }
    absolutePath.toString
  }
}