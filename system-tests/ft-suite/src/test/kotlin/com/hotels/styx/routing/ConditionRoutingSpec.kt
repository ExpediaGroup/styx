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
package com.hotels.styx.routing

import com.hotels.styx.StyxConfig
import com.hotels.styx.StyxServer
import com.hotels.styx.startup.StyxServerComponents
import com.hotels.styx.support.ResourcePaths.fixturesHome
import io.kotlintest.Spec
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class ConditionRoutingSpec : StringSpec() {
    init {
        "Styx server should start" {
            println("Styx server address: ${styxServer.proxyHttpAddress()}")
            println("Styx admin address: ${styxServer.adminHttpAddress()}")
            println("I'm failing on purpose!")
            println("hello")
            1.shouldBe(1)
        }
    }

    val originsOk = fixturesHome(ConditionRoutingSpec::class.java, "/conf/origins/origins-correct.yml")

    val yamlText = """
        proxy:
          connectors:
            http:
              port: 0

        services:
          factories:
            backendServiceRegistry:
              class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry${'$'}Factory"
              config: {originsFile: "$originsOk"}

        admin:
          connectors:
            http:
              port: 0

        httpPipeline:
          name: "Main Pipeline"
          type: InterceptorPipeline
          config:
            handler:
              name: protocol-router
              type: ConditionRouter
              config:
                routes:
                  - condition: protocol() == "https"
                    destination:
                      name: proxy-to-https
                      type: StaticResponseHandler
                      config:
                        status: 200
                        body: "This is HTTPS"
                fallback:
                  name: proxy-to-http
                  type: StaticResponseHandler
                  config:
                    status: 200
                    body: "This is HTTP only"
      """.trimIndent()

    val styxServer = StyxServer(StyxServerComponents.Builder()
            .styxConfig(StyxConfig.fromYaml(yamlText))
            .build())

    override fun beforeSpec(spec: Spec) {
        styxServer.startAsync().awaitRunning()
    }

    override fun afterSpec(spec: Spec) {
        styxServer.stopAsync().awaitTerminated()
    }
}
