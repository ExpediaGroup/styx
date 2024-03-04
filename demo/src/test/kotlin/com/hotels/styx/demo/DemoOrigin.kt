/*
  Copyright (C) 2013-2024 Expedia Inc.

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
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2
import com.github.tomakehurst.wiremock.http.Response
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import com.hotels.styx.api.HttpResponseStatus.statusWithCode

/**
 * This can be executed to launch a fake backend on localhost:9090.
 * It can be used to demonstrate Styx or for manual testing.
 *
 * To return particular types of responses, there are a couple of query parameters than can be sent.
 *
 * <ul>
 *     <li>status - responds with whatever status code you specify</li>
 *     <li>body - responds with a body of whatever you specify</li>
 * </ul>
 *
 * Note that this is only for demonstration purposes, and should not be used in any production environment for any reason,
 * as it could cause security issues.
 */
fun main() {
    val port = launchDemoOrigin().port()

    println("Demo origin started on port $port")
}

fun launchDemoOrigin(
    port: Int = 9090,
    path: String = "/",
): WireMockServer {
    val config =
        WireMockConfiguration()
            .port(port)
            .extensions(DemoTransformer())

    return WireMockServer(config).apply {
        stub(path) {
            withHeader("Content-Type", "text/plain")
            withBody("Default Demo Origin Response Body")
            withTransformers("demo")
        }

        start()
    }
}

private class DemoTransformer : ResponseTransformerV2 {
    override fun getName(): String = "demo"

    override fun transform(
        response: Response,
        serveEvent: ServeEvent,
    ): Response =
        serveEvent.request.let { req ->
            val status = req.queryParameter("status")?.takeIf { it.isPresent }?.firstValue()?.toInt() ?: 200
            Response.Builder.like(response)
                .status(status)
                .body(
                    req.queryParameter("body")?.takeIf { it.isPresent }?.firstValue()
                        ?: "Demo origin response. Status = ${statusWithCode(status)}",
                )
                .build()
        }

    override fun applyGlobally() = false
}

private inline fun WireMockServer.stub(
    startsWith: String,
    block: ResponseDefinitionBuilder.() -> Unit,
) = apply {
    stubFor(
        urlMatchingPattern("$startsWith.*").willReturn(
            aResponse().apply(block),
        ),
    )
}

private fun urlMatchingPattern(pattern: String): MappingBuilder = get(urlMatching(pattern))
