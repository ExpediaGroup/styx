/*
  Copyright (C) 2013-2018 Expedia Inc.

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
package com.hotels.styx.metrics;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.client.HttpClient;
import com.hotels.styx.client.StyxHttpClient;
import com.hotels.styx.testapi.StyxServer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.common.StyxFutures.await;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ProtocolMetricsTest {
    private final HttpClient client = new StyxHttpClient.Builder().build();

    private StyxServer styxServer;

    private WireMockServer origin;

    @BeforeMethod
    public void startOrigins() {
        origin = new WireMockServer(wireMockConfig().dynamicPort());
        origin.start();

        configureFor(origin.port());
        stubFor(WireMock.get(urlPathEqualTo("/")).willReturn(aResponse()
                .withHeader("origin", "first")
                .withBody("foo")
                .withStatus(OK.code())));
    }

    @AfterMethod
    public void stopStyx() {
        styxServer.stop();
    }

    @AfterMethod
    public void stopOrigins() {
        origin.stop();
    }

    @Test
    public void recordsServerSideHttp() {
        styxServer = new StyxServer.Builder()
                .addRoute("/", origin.port())
                .start();

        HttpResponse response = doGet("/");

        assertThat(response.status(), is(OK));

        assertThat(styxServer.metrics().meter("styx.server.http.requests").getCount(), is(1L));
        assertThat(styxServer.metrics().meter("styx.server.https.requests").getCount(), is(0L));

        assertThat(styxServer.metrics().meter("styx.server.http.responses.200").getCount(), is(1L));
        assertThat(styxServer.metrics().meter("styx.server.https.responses.200").getCount(), is(0L));
    }

    @Test
    public void recordsServerSideHttps() {
        styxServer = new StyxServer.Builder()
                .addRoute("/", origin.port())
                .start();

        HttpResponse response = doHttpsGet("/");

        assertThat(response.status(), is(OK));

        assertThat(styxServer.metrics().meter("styx.server.http.requests").getCount(), is(0L));
        assertThat(styxServer.metrics().meter("styx.server.https.requests").getCount(), is(1L));

        assertThat(styxServer.metrics().meter("styx.server.http.responses.200").getCount(), is(0L));
        assertThat(styxServer.metrics().meter("styx.server.https.responses.200").getCount(), is(1L));
    }

    private HttpResponse doGet(String path) {
        return doRequest(client, "http", styxServer.proxyHttpPort(), startWithSlash(path));
    }

    private HttpResponse doHttpsGet(String path) {
        HttpClient client1 = new StyxHttpClient.Builder().build();
        return doRequest(client1, "https", styxServer.proxyHttpsPort(), path);
    }

    private static HttpResponse doRequest(HttpClient client, String protocol, int port, String path) {
        String url = format("%s://localhost:%s%s", protocol, port, startWithSlash(path));

        HttpRequest request = get(url)
                .body("foobarbaz", UTF_8)
                .build();

        return await(client.sendRequest(request));
    }

    private static String startWithSlash(String path) {
        return !path.isEmpty() && path.charAt(0) == '/' ? path : "/" + path;
    }

}
