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
package com.hotels.styx.admin

import java.nio.charset.StandardCharsets.UTF_8

import com.hotels.styx.api.FullHttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.infrastructure.HttpResponseImplicits
import com.hotels.styx.{DefaultStyxConfiguration, StyxClientSupplier, StyxProxySpec}
import org.scalatest.FunSpec


class AdminSpec extends FunSpec
  with StyxProxySpec
  with DefaultStyxConfiguration
  with StyxClientSupplier
  with HttpResponseImplicits {

  describe("health check") {
    it("should return 200 and string 'PONG' on /admin/ping") {
      val response = decodedRequest(get(styxServer.adminURL("/admin/ping")).build())
      assert(response.status == OK)
      assert(response.isNotCacheAble())
      response.bodyAs(UTF_8) should include("pong")
    }
  }
}
