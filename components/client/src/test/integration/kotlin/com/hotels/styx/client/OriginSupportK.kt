package com.hotels.styx.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.api.extension.Origin.newOriginBuilder
import com.hotels.styx.support.server.FakeHttpServer

fun configureAndStart(origin: Origin): FakeHttpServer {
    val webServer: FakeHttpServer = FakeHttpServer(origin.port()).start()

    return webServer.stub(urlMatching("/version.txt"), aResponse()
        .withStatus(200)
        .withHeader("Stub-Origin-Info", origin.applicationInfo()))
}

fun originAndWireMockServer(applicationId: String, originId: String): Pair<Origin, WireMockServer> {
    val server = WireMockServer(wireMockConfig().dynamicPort())
    server.start()

    val origin = newOriginBuilder("localhost", server.port()).applicationId(applicationId).id(originId).build()
    return origin to server
}

fun originFrom(server : FakeHttpServer) = newOriginBuilder("localhost", server.port()).applicationId(server.appId()).id(server.originId()).build()
