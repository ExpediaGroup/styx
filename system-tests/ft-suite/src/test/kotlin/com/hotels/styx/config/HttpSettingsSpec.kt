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
package com.hotels.styx.config

import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.support.StyxServerProvider
//import com.hotels.styx.support.proxyHttpHostHeader
import io.kotlintest.Spec
import io.kotlintest.specs.FeatureSpec

class HttpSettingsSpec : FeatureSpec() {

    init {
//        feature("Initial Line Length") {
//            styxServer.restart()
//
//            scenario("Accepts requests within max initial length") {
//                val response = client.send(HttpRequest.get("/a/" + "b".repeat(80))
//                        .header(HOST, styxServer().proxyHttpHostHeader())
//                        .build())
//                        .wait()!!
//
//                response.status() shouldBe OK
//                response.bodyAs(UTF_8) shouldBe "Test origin."
//
//            }
//
//            scenario("Rejects requests exceeding the initial line length") {
//                val response = client.send(HttpRequest.get("/a/" + "b".repeat(95))
//                        .header(HOST, styxServer().proxyHttpHostHeader())
//                        .build())!!
//                        .wait()!!
//
//                response.status() shouldBe REQUEST_ENTITY_TOO_LARGE
//                response.bodyAs(UTF_8) shouldBe "Request Entity Too Large"
//                response.header("X-Styx-Info").isPresent.shouldBeTrue()
//            }
//        }
    }

    val client: StyxHttpClient = StyxHttpClient.Builder().build()

    val styxServer = StyxServerProvider("""
        proxy:
          connectors:
            http:
              port: 0
          maxInitialLength: 100

        admin:
          connectors:
            http:
              port: 0

        httpPipeline:
            type: StaticResponseHandler
            config:
              status: 200
              content: "Test origin."
      """.trimIndent())


    override fun afterSpec(spec: Spec) {
        styxServer.stop()
    }

}
