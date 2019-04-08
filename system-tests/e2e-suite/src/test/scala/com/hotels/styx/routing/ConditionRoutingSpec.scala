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

import java.nio.charset.StandardCharsets.UTF_8

import com.github.tomakehurst.wiremock.client.ValueMatchingStrategy
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus._
import com.hotels.styx.api.extension
import com.hotels.styx.infrastructure.{MemoryBackedRegistry, RegistryServiceAdapter}
import com.hotels.styx.support.ResourcePaths.fixturesHome
import com.hotels.styx.support.configuration._
import com.hotels.styx.{BackendServicesRegistrySupplier, StyxClientSupplier, StyxProxySpec}
import org.scalatest.{FunSpec, SequentialNestedSuiteExecution}

class ConditionRoutingSpec extends FunSpec
  with StyxProxySpec
  with StyxClientSupplier
  with SequentialNestedSuiteExecution
  with BackendServicesRegistrySupplier {

  val httpBackendRegistry = new MemoryBackedRegistry[extension.service.BackendService]()
  val logback = fixturesHome(this.getClass, "/conf/logback/logback-debug-stdout.xml")

  override val styxConfig = StyxConfig(
    ProxyConfig(
      Connectors(
        HttpConnectorConfig(),
        HttpsConnectorConfig())
    ),
    logbackXmlLocation = logback,
    additionalServices = Map(
      "backendServicesRegistry" -> new RegistryServiceAdapter(httpBackendRegistry),
    ),
    yamlText =
      """
        |httpPipeline:
        |  name: "Main Pipeline"
        |  type: InterceptorPipeline
        |  config:
        |    handler:
        |      name: protocol-router
        |      type: ConditionRouter
        |      config:
        |        routes:
        |          - condition: protocol() == "https"
        |            destination:
        |              name: proxy-to-https
        |              type: StaticResponse
        |              config:
        |                status: 200
        |                body: "This is HTTPS"
        |        fallback:
        |          name: proxy-to-http
        |          type: StaticResponse
        |          config:
        |            status: 200
        |            body: "This is HTTP only"
      """.stripMargin
  )

  def httpRequest(path: String) = get(styxServer.routerURL(path)).build()

  def httpsRequest(path: String) = get(styxServer.secureRouterURL(path)).build()

  def valueMatchingStrategy(matches: String) = {
    val matchingStrategy = new ValueMatchingStrategy()
    matchingStrategy.setMatches(matches)
    matchingStrategy
  }

  describe("Styx routing of HTTP requests") {
    ignore("Routes HTTP protocol to HTTP origins") {
      val response = decodedRequest(httpRequest("/app.1"))

      assert(response.status() == OK)
      assert(response.bodyAs(UTF_8) == "This is HTTP only")
    }

    ignore("Routes HTTPS protocol to HTTPS origins") {
      val response = decodedRequest(httpsRequest("/app.2"), secure = true)

      assert(response.status() == OK)
      assert(response.bodyAs(UTF_8) == "This is HTTPS")
    }
  }
}
