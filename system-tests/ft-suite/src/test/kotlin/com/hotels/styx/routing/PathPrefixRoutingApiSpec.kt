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
import com.hotels.styx.routing.handlers.RouteRefLookup
import com.hotels.styx.routing.handlers.StaticResponseHandler
import com.hotels.styx.startup.StyxServerComponents
import com.hotels.styx.support.StyxServerProvider
//import com.hotels.styx.support.proxyHttpHostHeader
import io.kotlintest.Spec
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import reactor.core.publisher.toMono
import java.nio.charset.StandardCharsets.UTF_8

class PathPrefixRoutingApiSpec : StringSpec() {

    val client: StyxHttpClient = StyxHttpClient.Builder().build()

    val styxServer = StyxServerProvider("""
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

        routingObjects:
          root:
             type: PathPrefixRouter
             config:
               routes:
                 - prefix: /
                   destination: default
                 - prefix: /database
                   destination: database

          default:
            type: StaticResponseHandler
            config:
              status: 200
              content: "I'm default"

          database:
            type: StaticResponseHandler
            config:
              status: 200
              content: "I'm database"

        httpPipeline: root
      """.trimIndent())

    override fun beforeSpec(spec: Spec) {
        styxServer.restart()
    }

    override fun afterSpec(spec: Spec) {
        styxServer.stop()
    }
}
