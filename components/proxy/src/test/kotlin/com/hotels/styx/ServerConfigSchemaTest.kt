/*
  Copyright (C) 2013-2022 Expedia Inc.

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
package com.hotels.styx

import com.hotels.styx.ServerConfigSchema.validateServerConfiguration
import com.hotels.styx.infrastructure.configuration.ConfigurationParser
import com.hotels.styx.infrastructure.configuration.ConfigurationSource.configSource
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfiguration
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfigurationFormat.YAML
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.Optional

class ServerConfigSchemaTest : DescribeSpec({
    describe("Styx Server Configuration") {
        it("Validates a minimal server configuration") {
            validateServerConfiguration(yamlConfig("""
                  proxy:
                    compressResponses: true
                    connectors:
                      http:
                        port: 8080
                  
                  admin:
                    connectors:
                      http:
                        port: 9000
                  
                  services:
                    factories:
                      backendServiceRegistry:
                        class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry${'$'}Factory"
                        config: {originsFile: "${'$'}{originsFile:classpath:conf/origins.yml}"}
            """.trimIndent())) shouldBe (Optional.empty())
        }


        it("An optional 'proxy' field can be absent.") {
            validateServerConfiguration(yamlConfig("""
                  admin:
                    connectors:
                      http:
                        port: 9000

                  services:
                    factories:
                      backendServiceRegistry:
                        class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry${'$'}Factory"
                        config: {originsFile: "${'$'}{originsFile:classpath:conf/origins.yml}"}
            """.trimIndent())) shouldBe (Optional.empty())
        }

        it("Detects a missing mandatory `admin' configuration.") {
            validateServerConfiguration(yamlConfig("""
                  proxy:
                    connectors:
                      http:
                        port: 8080

                  services:
                    factories:
                      backendServiceRegistry:
                        class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry${'$'}Factory"
                        config: {originsFile: "${'$'}{originsFile:classpath:conf/origins.yml}"}
            """.trimIndent())) shouldBe (Optional.of("Missing a mandatory field 'admin'"))
        }

        it("Accepts 'jvmRouteName' field as a STRING") {
            validateServerConfiguration(yamlConfig(minimalConfig + """
                jvmRouteName: 'instance-01'
                """.trimIndent()
            )) shouldBe (Optional.empty())

            validateServerConfiguration(yamlConfig(minimalConfig + """
                jvmRouteName: 101
                """.trimIndent()
            )) shouldBe (Optional.of("Unexpected field type. Field 'jvmRouteName' should be STRING, but it is NUMBER"))
        }

        it("Accepts 'request-logging' field as an OBJECT") {
            validateServerConfiguration(yamlConfig(minimalConfig + """
                    request-logging:
                      inbound:
                        enabled: true
                        longFormat: true
                      outbound:
                        enabled: true
                        longFormat: false
                      hideHeaders:
                        - header1
                        - header2
                      hideCookies:
                        - cookie1
                        - cookie2                        
            """.trimIndent()
            )) shouldBe (Optional.empty())
        }

        it("Accepts 'retrypolicy' field - but does not validate") {
            validateServerConfiguration(yamlConfig(minimalConfig + """
                retrypolicy:
                  x: false
              """.trimIndent()
            )) shouldBe (Optional.empty())
        }

        it("Accepts 'loadBalancing' field - but does not validate") {
            validateServerConfiguration(yamlConfig(minimalConfig + """
                loadBalancing:
                  x: false
            """.trimIndent()
            )) shouldBe (Optional.empty())
        }

        it("Accepts 'url' field - as an OBJECT") {
            validateServerConfiguration(yamlConfig(minimalConfig + """
                url:
                  encoding:
                    unwiseCharactersToEncode: '43'
                  """.trimIndent()
            )) shouldBe (Optional.empty())
        }

        it("Accepts 'originRestrictionCookie' field - as a STRING") {
            validateServerConfiguration(yamlConfig(minimalConfig + """
                originRestrictionCookie: mycookie
                """.trimIndent()
            )) shouldBe (Optional.empty())
        }

        it("Accepts 'styxHeaders' field as an OBJECT") {
            validateServerConfiguration(yamlConfig(minimalConfig + """
                styxHeaders:
                  styxInfo:
                    name: "X-Styx-Info"
                    valueFormat: "xyz"
                  originId:
                    name: "X-Origin-Id"
                  requestId:
                    name: "X-Request-Id"
              """.trimIndent()
            )) shouldBe (Optional.empty())
        }
    }

    describe("Incorrect configuration") {
        it("Detects unknown configuration options") {
            validateServerConfiguration(yamlConfig(minimalConfig + """
                abc: 1
            """.trimIndent())) shouldBe (Optional.of("Unexpected field: 'abc'"))
        }
    }

    describe("StyxHeaders Object") {

        it("Aaccepts 'valueFormat' field for 'styxInfo' only") {
            validateServerConfiguration(yamlConfig(minimalConfig + """
                styxHeaders:
                  originId:
                    name: "X-Origin-Id"
                    valueFormat: "x"
                """.trimIndent()
            )) shouldBe (Optional.of("Unexpected field: 'styxHeaders.originId.valueFormat'"))

            validateServerConfiguration(yamlConfig(minimalConfig + """
                styxHeaders:
                  requestId:
                    name: "X-Request-Id"
                    valueFormat: "x"
              """.trimIndent()
            )) shouldBe (Optional.of("Unexpected field: 'styxHeaders.requestId.valueFormat'"))
        }

        it("Accepts a missing optional 'valueFormat' in 'styxInfo' configuration") {
            validateServerConfiguration(yamlConfig(minimalConfig + """
                styxHeaders:
                  styxInfo:
                    name: "X-Styx-Info"
              """.trimIndent()
            )) shouldBe (Optional.empty())
        }

        it("Provides 'userDefined' as an opaque configuration block for custom attributes.") {
            validateServerConfiguration(yamlConfig(minimalConfig + """
                userDefined:
                  websiteName: hyper-super.com
                  threadCount: 7
                  pluginVersion: 3.5.1-134
              """.trimIndent()
            )) shouldBe (Optional.empty())
        }

    }

    describe("Proxy Configuration") {
        it("Simultaneously accepts both 'http' and 'https' connectors") {
            validateServerConfiguration(yamlConfig("""
                  proxy:
                    connectors:
                      http:
                        port: 8080
                      https:
                        port: 8443

                  admin:
                    connectors:
                      http:
                        port: 9000

                  services:
                    factories:
                      backendServiceRegistry:
                        class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry${'$'}Factory"
                        config: {originsFile: "${'$'}{originsFile:classpath:conf/origins.yml}"}
            """.trimIndent())) shouldBe (Optional.empty())
        }

        it("Accepts https.cipherSuites as a list of STRINGs") {
            validateServerConfiguration(yamlConfig("""
                  proxy:
                    connectors:
                      http:
                        port: 8080
                      https:
                        port: 8443
                        cipherSuites:
                         - TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384
                         - TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
                         - TLS_RSA_WITH_AES_256_CBC_SHA256

                  admin:
                    connectors:
                      http:
                        port: 9000

                  services:
                    factories:
                      backendServiceRegistry:
                        class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry${'$'}Factory"
                        config: {originsFile: "${'$'}{originsFile:classpath:conf/origins.yml}"}
            """.trimIndent())) shouldBe (Optional.empty())
        }

        it("Accepts https.protocols as a list of STRINGs") {
            validateServerConfiguration(yamlConfig("""
                  proxy:
                    connectors:
                      http:
                        port: 8080
                      https:
                        port: 8443
                        protocols:
                         - TLSv1.2

                  admin:
                    connectors:
                      http:
                        port: 9000

                  services:
                    factories:
                      backendServiceRegistry:
                        class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry${'$'}Factory"
                        config: {originsFile: "${'$'}{originsFile:classpath:conf/origins.yml}"}
                """.trimIndent())) shouldBe (Optional.empty())
        }
    }

    describe("Plugins configuration") {
        it("Accepts plugins.active as string") {
            validateServerConfiguration(yamlConfig(minimalConfig + """
                plugins:
                  active: xyz
            """.trimIndent()))
        }

        it("Accepts an absent plugins.active field") {
            validateServerConfiguration(yamlConfig(minimalConfig + """
                    plugins:
                      all: xyz
            """.trimIndent()))
        }

        it("Accepts an plugins.all as an opaque object") {
            validateServerConfiguration(yamlConfig(minimalConfig + """
                    plugins:
                      all: xyz
            """.trimIndent()))
        }
    }

    describe("routingObjects") {
        it("Is a map of routing objects") {
            validateServerConfiguration(yamlConfig(minimalConfig + """
                routingObjects: x
            """.trimIndent())) shouldBe Optional.of(
                    "Unexpected field type. Field 'routingObjects' should be MAP(OBJECT(name, type, tags, config)), but it is STRING")
        }

        it("Validates nested HTTP handlers") {
            validateServerConfiguration(yamlConfig(minimalConfig + """
                routingObjects:
                  staticResponse:
                    type: StaticResponseHandler
                    config:
                      status: 200
                      content: "Hello, world"
            """.trimIndent())) shouldBe Optional.empty()
        }

        it("Detects configuration errors in nested routing objects") {
            validateServerConfiguration(yamlConfig(minimalConfig + """
                    routingObjects:
                      staticResponse:
                        type: StaticResponseHandler
                        config:
                          status: 200
                          content: "Hello, world"
                      condition:
                        type: ConditionRouter
                        config:
                          routes:
                            - condition: "some condition"
                              destination: "destination 1"
                            - condition: "another condition"
                              destination: "destination 2"
                          fallback:
                            type: InterceptorPipeline
                            config:
                              pipeline:
                                - "bar"
                              handler:
                                type: StaticResponseHandler
                                config:
                                  status: 200
                                  contentS: "Fallback"
                """.trimIndent())) shouldBe Optional.of("Unexpected field: 'routingObjects[condition].config.fallback.config.handler.config.contentS'")
        }
    }

    describe("Styx server objects") {
        it("Accepts HttpServer objects") {
            validateServerConfiguration(yamlConfig(minimalConfig + """
                servers:
                  myServer:
                    type: HttpServer
                    config:
                      port: 8080
                      handler: abc
            """.trimIndent())) shouldBe Optional.empty()
        }

        it("Validates HttpServer objects - invalid port number") {
            validateServerConfiguration(yamlConfig(minimalConfig + """
                servers:
                  myServer:
                    type: HttpServer
                    config:
                      port: foobar
            """.trimIndent())) shouldBe Optional.of("Unexpected field type. Field 'servers[myServer].config.port' should be INTEGER, but it is STRING")
        }

        it("Validates HttpServer objects - missing handler attribute") {
            validateServerConfiguration(yamlConfig(minimalConfig + """
                servers:
                  myServer:
                    type: HttpServer
                    config:
                      port: 8080
            """.trimIndent())) shouldBe Optional.of("Missing a mandatory field 'servers[myServer].config.handler'")
        }
    }
})

private val minimalConfig = """
      proxy:
        connectors:
          http:
            port: 8080
          https:
            port: 8443
      
      admin:
        connectors:
          http:
            port: 9000
      
      services:
        factories:
          backendServiceRegistry:
            class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry${'$'}Factory"
            config: {originsFile: "${'$'}{originsFile:classpath:conf/origins.yml}"}
      
    """.trimIndent()

private fun yamlConfig(text: String) =
        ConfigurationParser.Builder<YamlConfiguration>()
                .format(YAML)
                .build()
                .parse(configSource(text))

