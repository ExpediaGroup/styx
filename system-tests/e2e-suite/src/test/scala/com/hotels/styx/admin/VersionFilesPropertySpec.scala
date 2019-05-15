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
package com.hotels.styx.admin

import java.nio.charset.StandardCharsets.UTF_8

import com.google.common.net.HostAndPort
import com.google.common.net.HostAndPort._
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origins, StyxConfig}
import com.hotels.styx.{E2EResources, StyxProxySpec}
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.infrastructure.HttpResponseImplicits
import org.scalatest.{FunSpec, Matchers}

class VersionFilesPropertySpec extends FunSpec with StyxProxySpec with Matchers with HttpResponseImplicits {
  val normalBackend = FakeHttpServer.HttpStartupConfig().start()

  val fileLocation = E2EResources.resolve("/version.txt")

  override val styxConfig = StyxConfig(
    yamlText = s"""
        |versionFiles: $fileLocation
        """.stripMargin('|')
  )

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    styxServer.setBackends("/" -> HttpBackend("app1", Origins(normalBackend)))
  }

  override protected def afterAll(): Unit = {
    normalBackend.stop()
    super.afterAll()
  }

  describe("version text ") {

    it("Styx gets the version text property") {
      val response = decodedRequest(get(styxServer.adminURL("/version.txt")).build())
      assert(response.status == OK)
      response.bodyAs(UTF_8) should include("some data")
    }
  }

  def styxHostAndPort: HostAndPort = {
    fromParts("localhost", styxServer.httpPort)
  }

}
