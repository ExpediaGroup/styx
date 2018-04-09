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
package com.hotels.styx.routing.config

import com.hotels.styx.StyxConfig
import org.scalatest.{FunSpec, ShouldMatchers}
import com.hotels.styx.routing.config.ConfigVersionResolver.Version.ROUTING_CONFIG_V1
import com.hotels.styx.routing.config.ConfigVersionResolver.Version.ROUTING_CONFIG_V2

class ConfigVersionResolverSpec extends FunSpec with ShouldMatchers {

  it ("returns v2 when 'httpPipeline' is present") {
    val config = StyxConfig.fromYaml(
      """
        |httpPipeline:
        |  pipeline:
        |    - plugA
        |    - plugB
        |  handler:
        |    name: MyHandler
        |    type: ProxyToBackend
        |    config:
        |      bar: 1
      """.stripMargin)

    new ConfigVersionResolver(config).version() should be (ROUTING_CONFIG_V2)
  }

  it ("returns v1 when 'httpPipeline' is absent") {
    val config = StyxConfig.fromYaml(
      """
        |---
        |services:
        |  factories:
        |    backendServiceRegistry:
        |      class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry$Factory"
        |      config: {originsFile: "/a/b/origins.conf"}
        |
      """.stripMargin
    )

    new ConfigVersionResolver(config).version() should be (ROUTING_CONFIG_V1)
  }

  ignore ("errors when 'services.factories.backendServicesRegistry' attribute is absent in v1 configuration") {
    val config = StyxConfig.fromYaml(
      """
        |services:
        |  factories:
        |    this_is_missing:
        |      class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry$Factory"
        |      config: {originsFile: "/a/b/origins.conf"}
      """.stripMargin)

    val e = intercept[IllegalArgumentException] {
      new ConfigVersionResolver(config).version()
    }
    e.getMessage should be ("Missing mandatory 'services.factories.backendServiceRegistry' attribute in configuration version 1.")
  }

}
