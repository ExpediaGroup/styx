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
package com.hotels.styx.support.api;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.HttpsSettings;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.matching.UrlPattern;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

public final class FakeHttpServer {
    private final String appId;
    private final String originId;
    /*
             * The FakeHttpServer can be started as a HTTPS server or a bare HTTP server.
             *
             * The FakeHttpServer is built on top of WireMock, which also supports secure HTTPS protocol.
             * When WireMock is used as a HTTPS mock server, it still needs to be configured via
             * non-SSL HTTP protocol. For this reason, the FakeHttpServer uses two separate ports in SSL
             * mode: An 'adminPort' for configuring and assertions, and 'serverPort' for serving the mock
             * endpoints.
             *
             * When FakeHttpServer is started as a bare HTTP server, the adminPort and serverPort are
             * the same.
             *
             */
    private final HttpsSettings httpsSettings;
    private final int serverPort;
    private final int adminPort;
    private final WireMockServer server;

    static {
        System.setProperty("org.mortbay.log.class", "com.github.tomakehurst.wiremock.jetty.LoggerAdapter");
    }

    private FakeHttpServer(String appId, String originId, WireMockConfiguration wireMockConfiguration) {
        this.appId = appId;
        this.originId = originId;
        httpsSettings = wireMockConfiguration.httpsSettings();
        server = new WireMockServer(wireMockConfiguration);

        if (httpsSettings.enabled()) {
            this.adminPort = wireMockConfiguration.portNumber();
            this.serverPort = httpsSettings.port();
        } else {
            this.adminPort = wireMockConfiguration.portNumber();
            this.serverPort = wireMockConfiguration.portNumber();
        }
    }

    public FakeHttpServer(int port) {
        this("generic-app", "generic-app-" + port, wireMockConfig().port(port));
    }

    public static FakeHttpServer newHttpServer(int port) {
        return new FakeHttpServer(port);
    }

    public static FakeHttpServer newHttpServer(String appId, String originId, WireMockConfiguration wireMockConfiguration) {
        return new FakeHttpServer(appId, originId, wireMockConfiguration);
    }


    public FakeHttpServer start() {
        configureFor("localhost", adminPort);
        if (!server.isRunning()) {
            server.start();
        }
        return this;
    }

    public FakeHttpServer stub(UrlPattern urlPattern, ResponseDefinitionBuilder response) {
        configureFor("localhost", adminPort());
        stubFor(WireMock.get(urlPattern).willReturn(response));
        return this;
    }

    public FakeHttpServer stub(MappingBuilder mappingBuilder, ResponseDefinitionBuilder response) {
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

    public FakeHttpServer stop() {
        configureFor("localhost", adminPort());
        if (server.isRunning()) {
            server.stop();
        }
        return this;
    }

    public FakeHttpServer reset() {
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
        return serverPort;
    }

    public int adminPort() {
        return adminPort;
    }

    public boolean ssl() {

        return httpsSettings.enabled();
    }

    public FakeHttpServer setDelay(int delay) {
        configureFor("localhost", adminPort());
        WireMock.setGlobalFixedDelay(delay);
        return this;
    }

    public boolean isRunning() {
        return server.isRunning();
    }
}
