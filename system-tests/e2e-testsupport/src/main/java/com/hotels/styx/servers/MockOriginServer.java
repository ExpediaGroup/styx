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
package com.hotels.styx.servers;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockApp;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestHandler;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.google.common.util.concurrent.ServiceManager;
import com.hotels.styx.InetServer;
import com.hotels.styx.StyxServers;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.server.HttpConnectorConfig;
import com.hotels.styx.server.HttpServers;
import com.hotels.styx.server.HttpsConnectorConfig;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.hotels.styx.servers.WiremockResponseConverter.toStyxResponse;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.ofNullable;

public final class MockOriginServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MockOriginServer.class);
    private static final int MAX_CONTENT_LENGTH = 256 * 1024;

    private final String appId;
    private final String originId;
    private final int adminPort;
    private final int serverPort;
    private final InetServer adminServer;
    private final InetServer mockServer;
    private static WireMockApp wireMockApp;

    static {
        System.setProperty("org.mortbay.log.class", "com.github.tomakehurst.wiremock.jetty.LoggerAdapter");
    }

    private ServiceManager services;

    private MockOriginServer(String appId, String originId, int adminPort, int serverPort, InetServer adminServer, InetServer mockServer) {
        this.appId = appId;
        this.originId = originId;
        this.adminPort = adminPort;
        this.serverPort = serverPort;
        this.adminServer = adminServer;
        this.mockServer = mockServer;
    }

    public static MockOriginServer create(String appId, String originId, int adminPort, HttpConnectorConfig httpConfig) {
        wireMockApp = wireMockApp();
        InetServer adminServer = createAdminServer(originId, adminPort, wireMockApp);
        InetServer mockServer = HttpServers.createHttpServer(
            "mock-stub-" + originId,
            httpConfig,
            mockHandler(originId, wireMockApp)
        );
        int serverPort = httpConfig.port();

        return new MockOriginServer(appId, originId, adminPort, serverPort, adminServer, mockServer);
    }

    public static MockOriginServer create(String appId, String originId, int adminPort, HttpsConnectorConfig httpsConfig) {
        wireMockApp = wireMockApp();
        InetServer adminServer = createAdminServer(originId, adminPort, wireMockApp);
        InetServer mockServer = HttpServers.createHttpsServer(
            "mock-stub-" + originId,
            httpsConfig,
            mockHandler(originId, wireMockApp));
        int serverPort = httpsConfig.port();

        return new MockOriginServer(appId, originId, adminPort, serverPort, adminServer, mockServer);
    }


    private static HttpHandler mockHandler(String originId, WireMockApp wireMockApp) {
        return newHandler(originId, wireMockApp.buildStubRequestHandler());
    }

    private static HttpHandler newHandler(String originId, RequestHandler requestHandler) {
        return (httpRequest, ctx) ->
                httpRequest.aggregate(MAX_CONTENT_LENGTH)
                        .map(fullRequest -> {
                            LOGGER.info("{} received: {}\n{}", originId, fullRequest.url(), fullRequest.body());
                            return fullRequest;
                        })
                        .flatMap(fullRequest -> {
                            Request wmRequest = new WiremockStyxRequestAdapter(fullRequest);
                            WiremockHttpResponder responder = new WiremockHttpResponder();
                            requestHandler.handle(wmRequest, responder, null);
                            return Eventual.of(toStyxResponse(responder.getResponse()).stream());
                        });
    }

    private static ByteBuf toByteBuf(String string) {
        return Unpooled.copiedBuffer(string, UTF_8);
    }

    public MockOriginServer start() {
        services = new ServiceManager(List.of(StyxServers.toGuavaService(adminServer), StyxServers.toGuavaService(mockServer)));
        services.startAsync().awaitHealthy();
        return this;
    }

    public MockOriginServer stop() {
        services.stopAsync().awaitStopped();
        return this;
    }

    public MockOriginServer stub(UrlPattern urlPattern, ResponseDefinitionBuilder response) {
        configureFor("localhost", adminPort());
        stubFor(WireMock.get(urlPattern).willReturn(response));
        return this;
    }

    public MockOriginServer stub(MappingBuilder mappingBuilder, ResponseDefinitionBuilder response) {
        configureFor("localhost", adminPort());
        stubFor(mappingBuilder.willReturn(response));
        return this;
    }

    public void verify(int count, RequestPatternBuilder builder) {
        configureFor("localhost", adminPort());
        WireMock.verify(count, builder);
    }

    public void verify(RequestPatternBuilder builder) {
        configureFor("localhost", adminPort());
        WireMock.verify(builder);
    }

    public MockOriginServer reset() {
        configureFor("localhost", adminPort());
        WireMock.reset();
        return this;
    }

    public String appId() {
        return appId;
    }

    public String originId() {
        return originId;
    }

    public int port() {
        return serverPort == 0 ? ofNullable(mockServer.inetAddress()).map(InetSocketAddress::getPort).orElse(-1) : serverPort;
    }

    public int adminPort() {
        return adminPort == 0 ? ofNullable(adminServer.inetAddress()).map(InetSocketAddress::getPort).orElse(-1) : adminPort;
    }

    public boolean isRunning() {
        return services.isHealthy();
    }

    private static WireMockApp wireMockApp() {
        return new WireMockApp(wireMockConfig(), null);
    }

    private static InetServer createAdminServer(String originId, int adminPort, WireMockApp wireMockApp) {
        return HttpServers.createHttpServer(
                "mock-admin-" + originId,
                new HttpConnectorConfig(adminPort),
                adminHandler(wireMockApp));
    }

    private static HttpHandler adminHandler(WireMockApp wireMockApp) {
        return newHandler(wireMockApp.buildAdminRequestHandler());
    }

    private static HttpHandler newHandler(RequestHandler requestHandler) {
        return (httpRequest, ctx) ->
                httpRequest.aggregate(MAX_CONTENT_LENGTH)
                        .map(fullRequest -> {
                            LOGGER.info("Received: {}\n{}", fullRequest.url(), fullRequest.body());
                            return fullRequest;
                        })
                        .flatMap(fullRequest -> {
                            Request wmRequest = new WiremockStyxRequestAdapter(fullRequest);
                            WiremockHttpResponder responder = new WiremockHttpResponder();
                            requestHandler.handle(wmRequest, responder, null);
                            return Eventual.of(toStyxResponse(responder.getResponse()).stream());
                        });
    }
}
