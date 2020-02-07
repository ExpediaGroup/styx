/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx.testapi;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.common.collect.ImmutableMap;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.extension.service.TlsSettings;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import com.hotels.styx.client.HttpClient;
import com.hotels.styx.client.StyxHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.common.StyxFutures.await;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static com.hotels.styx.testapi.Origins.origin;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StyxServerTest {
    private final HttpClient client = new StyxHttpClient.Builder()
            .build();

    private StyxServer styxServer;

    private WireMockServer originServer1;
    private WireMockServer originServer2;
    private WireMockServer secureOriginServer;

    @BeforeEach
    public void startOrigins() {
        originServer1 = new WireMockServer(wireMockConfig()
                .dynamicPort());

        originServer2 = new WireMockServer(wireMockConfig()
                .dynamicPort());

        secureOriginServer = new WireMockServer(wireMockConfig()
                .dynamicHttpsPort()
        );

        originServer1.start();
        originServer2.start();
        secureOriginServer.start();

        configureFor(originServer1.port());
        stubFor(WireMock.get(urlPathEqualTo("/")).willReturn(aResponse()
                .withHeader("origin", "first")
                .withStatus(OK.code())));

        configureFor(originServer2.port());
        stubFor(WireMock.get(urlPathEqualTo("/")).willReturn(aResponse()
                .withHeader("origin", "second")
                .withStatus(OK.code())));

        // HTTP port is still used to identify the WireMockServer, even when we are using it for HTTPS
        configureFor(secureOriginServer.port());
        stubFor(WireMock.get(urlPathEqualTo("/")).willReturn(aResponse()
                .withHeader("origin", "secure")
                .withStatus(OK.code())));
    }

    @AfterEach
    public void stopStyx() {
        if (styxServer != null) {
            styxServer.stop();
        }
    }

    @AfterEach
    public void stopOrigins() {
        originServer1.stop();
        originServer2.stop();
        secureOriginServer.stop();
    }

    private String startWithSlash(String path) {
        return !path.isEmpty() && path.charAt(0) == '/' ? path : "/" + path;
    }

    static {
        System.setProperty("org.mortbay.log.class", "com.github.tomakehurst.wiremock.jetty.LoggerAdapter");
    }
}