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
package com.hotels.styx.demo

import com.hotels.styx.api.*
import com.hotels.styx.api.plugins.spi.Plugin
import com.hotels.styx.api.plugins.spi.PluginFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.atomic.AtomicInteger

/**
 * A plugin to demonstrate how plugins work in Styx.
 *
 * By default, this plugin lets requests and responses pass through unmodified.
 *
 * To demonstrates how plugin errors are handled, the "exception" query parameter can be used with one of the following values:
 *
 * <ul>
 *  <li>onRequest - throws a DemoPluginException as soon as the request is received</li>
 *  <li>onResponse - proxies to the backend, then throws a DemoPluginException after receiving a response</li>
 * </ul>
 *
 * The plugin also exposes a page on the admin interface called 'hits' which just shows how many times the plugin has received
 * a request.
 */
class DemoPlugin : Plugin {
    private val logger: Logger = getLogger(DemoPlugin::class.java)
    private val hits = AtomicInteger()

    init {
        logger.info("Demo plugin initialised")
    }

    override fun adminInterfaceHandlers() = mapOf(
        "hits" to respondWith {
            "Hits: ${hits.get()}"
        }
    )

    override fun intercept(request: LiveHttpRequest, chain: HttpInterceptor.Chain): Eventual<LiveHttpResponse> {
        logger.info("Demo plugin received request $request")
        hits.incrementAndGet()

        if (request.askedForExceptionOnRequest()) {
            throw DemoPluginException("Demo Plugin: Deliberate exception on request")
        }

        return chain.proceed(request).map { response ->
            if (request.askedForExceptionOnResponse())
                throw DemoPluginException("Demo Plugin: Deliberate exception on response")
            else
                response
        }
    }
}

private fun respondWith(content: () -> String) = handler {
    body(content(), UTF_8)
}

private fun handler(lambda: HttpResponse.Builder.() -> Unit) = HttpHandler { _, _ ->
    val builder = HttpResponse.response()
    builder.lambda()

    Eventual.of(builder.build().stream())
}

private fun LiveHttpRequest.exceptionParam() = queryParam("exception").orElse(null)
private fun LiveHttpRequest.askedForExceptionOnRequest() = exceptionParam() matches "onRequest"
private fun LiveHttpRequest.askedForExceptionOnResponse() = exceptionParam() matches "onResponse"

private infix fun String?.matches(value: String) = this?.equals(value, true) ?: false

class DemoPluginFactory : PluginFactory {
    override fun create(environment: PluginFactory.Environment) = DemoPlugin()
}

class DemoPluginException(msg: String) : Exception(msg)
