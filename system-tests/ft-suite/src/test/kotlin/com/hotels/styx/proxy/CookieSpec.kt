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
package com.hotels.styx.proxy

import com.hotels.styx.api.HttpHeaderNames
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.support.StyxServerProvider
import com.hotels.styx.support.proxyHttpHostHeader
import com.hotels.styx.support.wait
import io.kotlintest.Spec
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class CookieSpec : StringSpec() {

    init {
        "Response Cookie should contain the SameSite attribute " {

            client.send(
                    get("/")
                            .header(HttpHeaderNames.HOST, styxServer().proxyHttpHostHeader())
                            .build())
                    .wait()!!
                    .let {
                        it.cookies().iterator().next().sameSite().get() shouldBe "Strict"
                    }
        }
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
              headers:
                - name: Set-Cookie
                  value: name=value; Secure=true; HttpOnly=true; SameSite=Strict
      """.trimIndent())


    override fun beforeSpec(spec: Spec) {
        styxServer.restart()
    }

    override fun afterSpec(spec: Spec) {
        styxServer.stop()
    }

}