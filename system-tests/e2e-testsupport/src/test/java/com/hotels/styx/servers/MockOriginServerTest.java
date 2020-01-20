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
package com.hotels.styx.servers;

import com.github.tomakehurst.wiremock.client.ValueMatchingStrategy;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.extension.service.TlsSettings;
import com.hotels.styx.client.HttpClient;
import com.hotels.styx.client.StyxHttpClient;
import com.hotels.styx.server.HttpConnectorConfig;
import com.hotels.styx.server.HttpsConnectorConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.HostnameVerifier;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.common.StyxFutures.await;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier;
import static javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class MockOriginServerTest {

    private MockOriginServer server;
    private HttpClient client;
    private HostnameVerifier oldHostNameVerifier;
    private StyxHttpClient tlsClient;

    @BeforeEach
    public void setUp() {
        client = new StyxHttpClient.Builder().build();
        tlsClient = new StyxHttpClient.Builder().tlsSettings(new TlsSettings.Builder().build()).build();
        oldHostNameVerifier = disableHostNameVerification();
    }

    @AfterEach
    public void tearDown() {
        server.stop();
        setDefaultHostnameVerifier(oldHostNameVerifier);
    }

    @Test
    public void configuresEndpoints() {
        server = MockOriginServer.create("", "", 0, new HttpConnectorConfig(0))
                .start()
                .stub(WireMock.get(urlMatching("/.*")), aResponse()
                        .withStatus(200)
                        .withHeader("a", "b")
                        .withBody("Hello, World!"));

        HttpResponse response = await(client.sendRequest(
                get(format("http://localhost:%d/mock", server.port()))
                        .header("X-Forwarded-Proto", "http")
                        .build()));

        assertThat(response.status(), is(OK));
        assertThat(response.header("a"), is(Optional.of("b")));
        assertThat(response.bodyAs(UTF_8), is("Hello, World!"));

        server.verify(getRequestedFor(urlEqualTo("/mock"))
                .withHeader("X-Forwarded-Proto", valueMatchingStrategy("http")));
    }

    @Test
    public void configuresTlsEndpoints() throws Exception {
        server = MockOriginServer.create("", "", 0,
                new HttpsConnectorConfig.Builder()
                        .port(0)
                        .build())
                .start()
                .stub(WireMock.get(urlMatching("/.*")), aResponse()
                        .withStatus(200)
                        .withHeader("a", "b")
                        .withBody("Hello, World!"));

        HttpResponse response = await(
                tlsClient.sendRequest(
                        get(format("https://localhost:%d/mock", server.port()))
                                .header("X-Forwarded-Proto", "http")
                                .build()));

        assertThat(response.status(), is(OK));
        assertThat(response.header("a"), is(Optional.of("b")));

        server.verify(getRequestedFor(urlEqualTo("/mock"))
                .withHeader("X-Forwarded-Proto", valueMatchingStrategy("http")));
    }

    private ValueMatchingStrategy valueMatchingStrategy(String matches) {
        ValueMatchingStrategy strategy = new ValueMatchingStrategy();
        strategy.setMatches(matches);
        return strategy;
    }

    private HostnameVerifier disableHostNameVerification() {
        HostnameVerifier old = getDefaultHostnameVerifier();
        setDefaultHostnameVerifier((s, sslSession) -> true);
        return old;
    }

}