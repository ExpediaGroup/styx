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
package com.hotels.styx.routing

import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.support.ResourcePaths.fixturesHome
import com.hotels.styx.support.StyxServerProvider
import com.hotels.styx.support.adminHostHeader
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.core.publisher.toMono
import java.nio.charset.StandardCharsets.UTF_8

class VersionFilesPropertySpec : StringSpec() {

    init {
        "Gets the version text property" {
            val request = get("/version.txt")
                    .header(HOST, styxServer().adminHostHeader())
                    .build()

            client.send(request)
                    .toMono()
                    .block()
                    .let {
                        it!!.status() shouldBe (OK)
                        it.bodyAs(UTF_8) shouldBe ("MyFakeVersionText\n")
                    }
        }
    }

    val client: StyxHttpClient = StyxHttpClient.Builder().build()

    val fileLocation = fixturesHome(VersionFilesPropertySpec::class.java,"/version.txt")

    val styxServer = StyxServerProvider("""
        proxy:
          connectors:
            http:
              port: 0

        admin:
          connectors:
            http:
              port: 0

        userDefined:
          versionFiles: $fileLocation
      """.trimIndent())

    override suspend fun beforeSpec(spec: Spec) {
        styxServer.restart()
    }

    override suspend fun afterSpec(spec: Spec) {
        styxServer.stop()
    }
}
