/*
  Copyright (C) 2013-2018 Expedia Inc.

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
/**
 * Copyright (C) 2013-2018 Expedia Inc.
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
package com.hotels.styx.admin

import java.io.{ByteArrayInputStream, File}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files.{copy, delete}
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

import com.google.common.io.Files.createTempDir
import com.hotels.styx.api.FullHttpRequest.get
import com.hotels.styx.api.HttpResponseStatus
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration._
import com.hotels.styx.{StyxClientSupplier, StyxServerSupport}
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, ConfigMap, FunSpec, ShouldMatchers}

class FileBasedOriginsFileChangeMonitorSpec extends FunSpec
  with StyxServerSupport
  with StyxClientSupplier
  with ShouldMatchers
  with BeforeAndAfterAll
  with Eventually {

  val tempDir = createTempDir()
  val styxOriginsFile = new File(tempDir, "origins.yml").toPath

  val origin = FakeHttpServer.HttpStartupConfig(appId = "app", originId="app").start()

  val configTemplate =
    """---
      |- id: "%s"
      |  path: "%s"
      |  connectionPool:
      |    maxConnectionsPerHost: 45
      |    maxPendingConnectionsPerHost: 15
      |    socketTimeoutMillis: 120000
      |    connectTimeoutMillis: 1000
      |    pendingConnectionTimeoutMillis: 8000
      |  healthCheck:
      |    uri: "/version.txt"
      |    intervalMillis: 5000
      |  responseTimeoutMillis: 60000
      |  origins:
      |  - { id: "app1", host: "localhost:%d" }
      |""".stripMargin

  private val yamlConfig: String =
    s"""---
       |proxy:
       |  connectors:
       |    http:
       |      port: 0
       |
       |admin:
       |  connectors:
       |    http:
       |      port: 0
       |
       |services:
       |  factories:
       |    backendServiceRegistry:
       |      class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry$$Factory"
       |      config:
       |        originsFile: "${styxOriginsFile.toString.replace("\\","/")}"
       |        monitor:
       |          enabled: true
    """.stripMargin

  writeConfig(styxOriginsFile, configTemplate.format("appv1", "/app01/", origin.port()))

  val styxServer = StyxYamlConfig(yamlConfig = yamlConfig).startServer()
  val reqToApp01 = get(s"http://localhost:${styxServer.proxyHttpAddress().getPort}/app01/x").build()
  val reqToApp02 = get(s"http://localhost:${styxServer.proxyHttpAddress().getPort}/app02/x").build()


  override protected def beforeAll(configMap: ConfigMap) {
    styxServer.isRunning should be(true)
  }

  it("Automatically detects changes in origins file.") {
    decodedRequest(reqToApp01).status() should be (HttpResponseStatus.OK)
    decodedRequest(reqToApp02).status() should be (HttpResponseStatus.BAD_GATEWAY)

    writeConfig(styxOriginsFile, configTemplate.format("appv2", "/app02/", origin.port()))

    Thread.sleep(2000)

    decodedRequest(reqToApp01).status() should be (HttpResponseStatus.BAD_GATEWAY)
    decodedRequest(reqToApp02).status() should be (HttpResponseStatus.OK)
  }

  def writeConfig(path: Path, text: String): Unit = {
    println(s"Updating origins configuration to $path")
    println(text)
    copy(new ByteArrayInputStream(text.getBytes(UTF_8)), path, REPLACE_EXISTING)
  }

  override protected def afterAll(): Unit = {
    styxServer.stopAsync().awaitTerminated()
    delete(styxOriginsFile)
    delete(tempDir.toPath)
    origin.stop()
  }
}
