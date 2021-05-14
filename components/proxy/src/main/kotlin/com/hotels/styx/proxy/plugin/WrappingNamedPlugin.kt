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
package com.hotels.styx.proxy.plugin

import com.hotels.styx.api.*
import com.hotels.styx.api.plugins.spi.Plugin
import com.hotels.styx.common.Preconditions.checkArgument

class WrappingNamedPlugin(val name: String, val plugin: Plugin) : NamedPlugin {
    @Volatile
    private var enabled = true

    init {
        checkArgument(
            plugin !is WrappingNamedPlugin,
            "Cannot wrap %s in %s",
            WrappingNamedPlugin::class.java.name,
            WrappingNamedPlugin::class.java.name
        )
    }

    override fun intercept(request: LiveHttpRequest, chain: HttpInterceptor.Chain): Eventual<LiveHttpResponse> =
        if (enabled) {
            plugin.intercept(request, chain)
        } else {
            chain.proceed(request)
        }

    override fun originalPlugin(): Plugin = plugin
    override fun name(): String = name
    override fun enabled(): Boolean = enabled
    override fun styxStarting() = plugin.styxStarting()
    override fun styxStopping() = plugin.styxStopping()
    override fun adminInterfaceHandlers(): MutableMap<String, HttpHandler> = plugin.adminInterfaceHandlers()

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}

fun wrapWithName(name: String, plugin: Plugin): NamedPlugin = WrappingNamedPlugin(name, plugin)
