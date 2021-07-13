/*
  Copyright (C) 2013-2021 Expedia Inc.

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

import com.google.common.net.MediaType.PLAIN_TEXT_UTF_8
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.plugins.spi.Plugin
import com.hotels.styx.common.http.handler.HttpAggregator
import com.hotels.styx.common.http.handler.StaticBodyHttpHandler
import com.hotels.styx.support.StyxServerProvider
import com.hotels.styx.support.adminRequest
import io.kotlintest.Spec
import io.kotlintest.matchers.string.shouldInclude
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import java.nio.charset.StandardCharsets.UTF_8

class PluginAdminInterfaceSpec : FeatureSpec() {

    val styxServer = StyxServerProvider(
            defaultConfig = """
                ---
                proxy:
                  connectors:
                    http:
                      port: 0
        
                admin:
                  connectors:
                    http:
                      port: 0
        
                """.trimIndent(),
            defaultAdditionalPlugins = mapOf(
                    "plugx" to PluginX(),
                    "plugy" to PluginY(),
                    "plugz" to PluginZ(),
                    "plugw" to PluginWithNoAdminFeatures()
            ))

    init {
        styxServer.restart()

        feature("Admin server index page") {
            scenario("Contains a link to plugins index page") {
                val response = styxServer.adminRequest("/admin")
                response.bodyAs(UTF_8).shouldInclude("<a href='/admin/plugins'>Plugins</a>")
            }
        }

        feature("Plugin admin interface endpoints") {
            scenario("Are exposed under /admin/plugins/<plugin-name>") {
                val respX1 = styxServer.adminRequest("/admin/plugins/plugx/path/one")
                val respX2 = styxServer.adminRequest("/admin/plugins/plugx/path/two")
                val respY1 = styxServer.adminRequest("/admin/plugins/plugy/path/one")
                val respY2 = styxServer.adminRequest("/admin/plugins/plugy/path/two")

                respX1.bodyAs(UTF_8).shouldBe("X: Response from first admin interface")
                respX2.bodyAs(UTF_8).shouldBe("X: Response from second admin interface")
                respY1.bodyAs(UTF_8).shouldBe("Y: Response from first admin interface")
                respY2.bodyAs(UTF_8).shouldBe("Y: Response from second admin interface")
            }

            scenario("Styx adds a missing path separator character") {
                val respZ1 = styxServer.adminRequest("/admin/plugins/plugz/path/one")
                val respZ2 = styxServer.adminRequest("/admin/plugins/plugz/path/two")

                respZ1.bodyAs(UTF_8).shouldBe("Z: Response from first admin interface")
                respZ2.bodyAs(UTF_8).shouldBe("Z: Response from second admin interface")
            }

        }

        feature("Plugins index page") {
            scenario("Contains links to a plugin-specific admin index pages") {
                val response = styxServer.adminRequest("/admin/plugins")

                response.bodyAs(UTF_8).shouldInclude("<a href='/admin/plugins/plugx'>plugx</a>")
                response.bodyAs(UTF_8).shouldInclude("<a href='/admin/plugins/plugy'>plugy</a>")
                response.bodyAs(UTF_8).shouldInclude("<a href='/admin/plugins/plugz'>plugz</a>")
                response.bodyAs(UTF_8).shouldInclude("<a href='/admin/plugins/plugw'>plugw</a>")
            }
        }

        feature("Plugin specific index page") {
            scenario("Contains a link to each plugin endpoint") {
                val response = styxServer.adminRequest("/admin/plugins/plugx")

                response.bodyAs(UTF_8).shouldInclude("<a href='/admin/plugins/plugx/path/one'>plugx: path/one</a>")
                response.bodyAs(UTF_8).shouldInclude("<a href='/admin/plugins/plugx/path/two'>plugx: path/two</a>")
            }

            scenario("Shows a message when plugin doesn't expose any endpoints.") {
                val response = styxServer.adminRequest("/admin/plugins/plugw")
                response.bodyAs(UTF_8).shouldInclude("This plugin (plugw) does not expose any admin interfaces")
            }
        }
    }

    override fun afterSpec(spec: Spec) {
        styxServer.stop()
    }
}

private class PluginX : Plugin {
    override fun intercept(request: LiveHttpRequest, chain: HttpInterceptor.Chain) = chain.proceed(request)

    override fun adminInterfaceHandlers() = mapOf<String, HttpHandler>(
            "/path/one" to HttpAggregator(StaticBodyHttpHandler(PLAIN_TEXT_UTF_8, "X: Response from first admin interface")),
            "/path/two" to HttpAggregator(StaticBodyHttpHandler(PLAIN_TEXT_UTF_8, "X: Response from second admin interface"))
    )
}

private class PluginY : Plugin {
    override fun intercept(request: LiveHttpRequest, chain: HttpInterceptor.Chain) = chain.proceed(request)

    override fun adminInterfaceHandlers() = mapOf<String, HttpHandler>(
            "/path/one" to HttpAggregator(StaticBodyHttpHandler(PLAIN_TEXT_UTF_8, "Y: Response from first admin interface")),
            "/path/two" to HttpAggregator(StaticBodyHttpHandler(PLAIN_TEXT_UTF_8, "Y: Response from second admin interface"))
    )
}

private class PluginZ : Plugin {
    override fun intercept(request: LiveHttpRequest, chain: HttpInterceptor.Chain) = chain.proceed(request)

    override fun adminInterfaceHandlers() = mapOf<String, HttpHandler>(
            "path/one" to HttpAggregator(StaticBodyHttpHandler(PLAIN_TEXT_UTF_8, "Z: Response from first admin interface")),
            "path/two" to HttpAggregator(StaticBodyHttpHandler(PLAIN_TEXT_UTF_8, "Z: Response from second admin interface"))
    )
}

private class PluginWithNoAdminFeatures() : Plugin {
    override fun intercept(request: LiveHttpRequest, chain: HttpInterceptor.Chain) = chain.proceed(request)

    override fun adminInterfaceHandlers() = mapOf<String, HttpHandler>()
}