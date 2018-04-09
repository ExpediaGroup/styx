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
package com.hotels.styx.plugins

import java.nio.charset.StandardCharsets.UTF_8

import com.hotels.styx.StyxProxySpec
import com.hotels.styx.api.HttpRequest.Builder._
import com.hotels.styx.api.messages.HttpResponseStatus.OK
import com.hotels.styx.support.configuration.StyxConfig
import org.scalatest.FunSpec
import org.scalatest.concurrent.Eventually

class FormUrlEncodedDataSpec extends FunSpec with StyxProxySpec with Eventually {
  override val styxConfig = StyxConfig(plugins = List("formdata" -> new FormUrlEncodedDataTesterPlugin()))

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    styxServer.setBackends()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
  }

  describe("Styx as a plugin container") {
    it("decodes correctly a post parameter") {
      val request = post(styxServer.adminURL("/admin/plugins/formdata/foo?"))
        .body("version=54.0")
        .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
        .build()
      val resp = decodedRequest(request)
      assert(resp.status() == OK)
      assert(resp.bodyAs(UTF_8) == "version: 54.0")
    }

    it("decodes correctly multiple post parameter") {
      val request = post(styxServer.adminURL("/admin/plugins/formdata/foo?"))
        .body("version=54.0&app=bar")
        .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
        .build()
      val resp = decodedRequest(request)
      assert(resp.status() == OK)
      assert(resp.bodyAs(UTF_8) == s"app: bar\nversion: 54.0")
    }
  }

}
