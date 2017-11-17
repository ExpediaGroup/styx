/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.servers;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.UrlMatchingStrategy;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockApp;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.global.NotImplementedRequestDelayControl;
import com.github.tomakehurst.wiremock.http.AdminRequestHandler;
import com.github.tomakehurst.wiremock.http.BasicResponseRenderer;
import com.github.tomakehurst.wiremock.http.ProxyResponseRenderer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestHandler;
import com.github.tomakehurst.wiremock.http.StubRequestHandler;
import com.github.tomakehurst.wiremock.http.StubResponseRenderer;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ServiceManager;
import com.hotels.styx.api.HttpHandler2;
import com.hotels.styx.server.HttpConnectorConfig;
import com.hotels.styx.server.HttpServer;
import com.hotels.styx.server.HttpServers;
import com.hotels.styx.server.HttpsConnectorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.google.common.base.Optional.absent;
import static com.hotels.styx.servers.WiremockResponseConverter.toStyxResponse;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static rx.Observable.just;

public final class MockOriginServer {
    private static final String FILES_ROOT = "__files";
    private static final Logger LOGGER = LoggerFactory.getLogger(MockOriginServer.class);
    private static final int MAX_CONTENT_LENGTH = 256 * 1024;

    private final String appId;
    private final String originId;
    private final int adminPort;

    private int serverPort;
    private final HttpServer adminServer;
    private final HttpServer mockServer;

    static {
        System.setProperty("org.mortbay.log.class", "com.github.tomakehurst.wiremock.jetty.LoggerAdapter");
    }

    private ServiceManager services;

    private MockOriginServer(String appId, String originId, int adminPort, int serverPort, HttpServer adminServer, HttpServer mockServer) {
        this.appId = appId;
        this.originId = originId;
        this.adminPort = adminPort;
        this.serverPort = serverPort;
        this.adminServer = adminServer;
        this.mockServer = mockServer;
    }

    private static WireMockApp wireMockApp() {
        return new WireMockApp(
                new NotImplementedRequestDelayControl(),
                false,
                stubMappings -> {

                },
                mappings -> {

                },
                false,
                absent(),
                emptyMap(),
                null,
                null
        );
    }

    private static HttpServer createAdminServer(String originId, int adminPort, WireMockApp wireMockApp) {
        return HttpServers.createHttpServer(
                "mock-admin-" + originId,
                new HttpConnectorConfig(adminPort),
                adminHandler(wireMockApp));
    }

    public static MockOriginServer create(String appId, String originId, int adminPort, HttpConnectorConfig httpConfig) {
        WireMockApp wireMockApp = wireMockApp();
        HttpServer adminServer = createAdminServer(originId, adminPort, wireMockApp);
        HttpServer mockServer = HttpServers.createHttpServer(
                "mock-stub-" + originId,
                httpConfig,
                mockHandler(wireMockApp, new WireMockConfiguration()));
        int serverPort = httpConfig.port();

        return new MockOriginServer(appId, originId, adminPort, serverPort, adminServer, mockServer);
    }

    public static MockOriginServer create(String appId, String originId, int adminPort, HttpsConnectorConfig httpsConfig) {
        WireMockApp wireMockApp = wireMockApp();
        HttpServer adminServer = createAdminServer(originId, adminPort, wireMockApp);
        HttpServer mockServer = HttpServers.createHttpsServer(
                "mock-stub-" + originId,
                httpsConfig,
                mockHandler(wireMockApp, new WireMockConfiguration()));
        int serverPort = httpsConfig.port();
        System.out.println(format("Creating HTTPS MockOriginServer - %d, %d", adminPort, serverPort));
        return new MockOriginServer(appId, originId, adminPort, serverPort, adminServer, mockServer);
    }

    private static HttpHandler2 mockHandler(WireMockApp wireMockApp, WireMockConfiguration defaultConfig) {
        return newHandler(new StubRequestHandler(
                wireMockApp,
                new StubResponseRenderer(
                        defaultConfig.filesRoot().child(FILES_ROOT),
                        wireMockApp.getGlobalSettingsHolder(),
                        new ProxyResponseRenderer(
                                defaultConfig.proxyVia(),
                                defaultConfig.httpsSettings().trustStore(),
                                defaultConfig.shouldPreserveHostHeader(),
                                defaultConfig.proxyHostHeader()
                        )
                )
        ));
    }

    private static HttpHandler2 adminHandler(WireMockApp wireMockApp) {
        return newHandler(new AdminRequestHandler(wireMockApp, new BasicResponseRenderer()));
    }

    private static HttpHandler2 newHandler(RequestHandler wireMockHandler) {
        return (httpRequest, ctx) ->
                httpRequest.decode(byteBuf -> byteBuf.toString(UTF_8), MAX_CONTENT_LENGTH)
                        .doOnNext(decoded -> LOGGER.info("Received: {}\n{}", new Object[]{decoded.requestBuilder().build().url(), decoded.body()}))
                        .flatMap(decoded -> {
                            Request wmRequest = new WiremockStyxRequestAdapter(decoded.requestBuilder().build(), decoded.body());
                            com.github.tomakehurst.wiremock.http.Response wmResponse = wireMockHandler.handle(wmRequest);
                            return just(toStyxResponse(wmResponse));
                        });
    }

    public MockOriginServer start() {
        services = new ServiceManager(ImmutableList.of(adminServer, mockServer));
        services.startAsync().awaitHealthy();
        return this;
    }

    public MockOriginServer stop() {
        services.stopAsync().awaitStopped();
        return this;
    }

    public MockOriginServer stub(UrlMatchingStrategy urlMatchingStrategy, ResponseDefinitionBuilder response) {
        WireMock wm = new WireMock("localhost", adminPort());
        wm.register(WireMock.get(urlMatchingStrategy).willReturn(response));
        return this;
    }

    public MockOriginServer stub(MappingBuilder mappingBuilder, ResponseDefinitionBuilder response) {
        configureFor("localhost", adminPort());
        stubFor(mappingBuilder.willReturn(response));
        return this;
    }

    public void verify(int count, RequestPatternBuilder builder) {
        WireMock wm = new WireMock("localhost", adminPort());
        wm.verifyThat(count, builder);
    }

    public void verify(RequestPatternBuilder builder) {
        WireMock wm = new WireMock("localhost", adminPort());
        wm.verifyThat(builder);
    }

    public MockOriginServer reset() {
        WireMock wm = new WireMock("localhost", adminPort());
        wm.resetMappings();
        return this;
    }

    public String appId() {
        return appId;
    }

    public String originId() {
        return originId;
    }

    public int port() {
        return serverPort;
    }

    public int adminPort() {
        return adminPort;
    }

    public boolean isRunning() {
        return services.isHealthy();
    }
}
