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
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.startup.StyxServerComponents
import com.hotels.styx.support.ResourcePaths.fixturesHome
import io.kotlintest.Spec
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets.UTF_8

class ConditionRoutingSpec : StringSpec() {

    init {
        "Routes HTTP protocol" {
            val request = get("/11")
                    .header(HOST, "${styxServer.proxyHttpAddress().hostName}:${styxServer.proxyHttpAddress().port}")
                    .build();

            val response = Mono.fromCompletionStage(client.send(request))
                    .block();

            response?.status() shouldBe (OK)
            response?.bodyAs(UTF_8) shouldBe ("Hello, from http server!")
        }

        "Routes HTTPS protocol" {
            val request = get("/2")
                    .header(HOST, "${styxServer.proxyHttpsAddress().hostName}:${styxServer.proxyHttpsAddress().port}")
                    .build();

            val response = Mono.fromCompletionStage(
                    client.secure()
                            .send(request))
                    .block();

            response?.status() shouldBe (OK)
            response?.bodyAs(UTF_8) shouldBe ("Hello, from secure server!")
        }
    }

    val originsOk = fixturesHome(ConditionRoutingSpec::class.java, "/conf/origins/origins-correct.yml")
    val yamlText = """
        proxy:
          connectors:
            http:
              port: 0

            https:
              port: 0
              sslProvider: JDK
              sessionTimeoutMillis: 300000
              sessionCacheSize: 20000

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
          type: InterceptorPipeline
          config:
            handler:
              type: ConditionRouter
              config:
                routes:
                  - condition: protocol() == "https"
                    destination:
                      name: proxy-to-https
                      type: StaticResponseHandler
                      config:
                        status: 200
                        content: "Hello, from secure server!"
                fallback:
                  type: StaticResponseHandler
                  config:
                    status: 200
                    content: "Hello, from http server!"
      """.trimIndent()

    val client: StyxHttpClient = StyxHttpClient.Builder().build()

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
