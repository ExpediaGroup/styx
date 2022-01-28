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
package com.hotels.styx.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.api.extension.Origin.newOriginBuilder
import com.hotels.styx.support.server.FakeHttpServer
//
//fun configureAndStart(origin: Origin): FakeHttpServer {
//    val webServer: FakeHttpServer = FakeHttpServer(origin.port()).start()
//
//    return webServer.stub(urlMatching("/version.txt"), aResponse()
//        .withStatus(200)
//        .withHeader("Stub-Origin-Info", origin.applicationInfo()))
//}
//
//fun originAndWireMockServer(applicationId: String, originId: String): Pair<Origin, WireMockServer> {
//    val server = WireMockServer(wireMockConfig().dynamicPort())
//    server.start()
//
//    val origin = newOriginBuilder("localhost", server.port()).applicationId(applicationId).id(originId).build()
//    return origin to server
//}

fun originFrom(server: FakeHttpServer): Origin =
    newOriginBuilder("localhost", server.port()).applicationId(server.appId()).id(server.originId()).build()
