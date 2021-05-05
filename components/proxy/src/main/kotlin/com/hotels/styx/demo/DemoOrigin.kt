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

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.UrlMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.common.FileSource
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.ResponseTransformer
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.http.ResponseDefinition
import com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH
import com.hotels.styx.api.HttpResponseStatus
import java.nio.charset.Charset


// This should probably be in a separate module, but let's just get it working first

fun main() {
    val port = launchDemoOrigin().port()

    println("Demo origin started on port $port")
}

fun launchDemoOrigin(): WireMockServer {
    val config: WireMockConfiguration = WireMockConfiguration()
        .port(9090)
        .extensions(DemoTransformer())

    val server = WireMockServer(config)

    server.stub("/") {
        withBody("Body not transformed")
        withTransformers("demo")
    }

    server.start()
    return server
}

class DemoTransformer : ResponseTransformer() {
    override fun name(): String = "demo"

    override fun transform(request: Request, responseDefinition: ResponseDefinition, files: FileSource) =
        responseDefinition.apply {
            request.queryParam("status")?.let {
                status = it.toInt()
            }

            body = "Demo origin response. Status = ${HttpResponseStatus.statusWithCode(status)}"

            request.queryParam("body")?.let {
                body = it
            }
        }

    override fun applyGlobally() = false
}

fun Request.queryParam(name: String): String? {
    return queryParameter(name).run {
        if (isPresent) {
            firstValue()
        } else {
            null
        }
    }
}

inline fun WireMockServer.stub(startsWith: String, block: ResponseDefinitionBuilder.() -> Unit): WireMockServer {
    val responseDefinitionBuilder = aResponse()
    responseDefinitionBuilder.block()

    stubFor(
        urlMatchingPattern("$startsWith.*").willReturn(
            responseDefinitionBuilder
        )
    )

    return this
}

fun urlMatchingPattern(pattern: String): MappingBuilder {
    val u = UrlMatchingStrategy()
    u.setUrlPattern(pattern)
    return WireMock.get(u)
}