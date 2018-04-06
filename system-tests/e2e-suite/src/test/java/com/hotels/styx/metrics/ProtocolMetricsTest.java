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
import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.client.UrlConnectionHttpClient;
import com.hotels.styx.api.messages.FullHttpResponse;
import com.hotels.styx.client.SimpleNettyHttpClient;
import com.hotels.styx.client.connectionpool.CloseAfterUseConnectionDestination;
import com.hotels.styx.testapi.StyxServer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observable;

import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.support.HostAndPorts.freePort;
import static com.hotels.styx.support.api.BlockingObservables.waitForResponse;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ProtocolMetricsTest {
    private final HttpClient client = new SimpleNettyHttpClient.Builder()
            .connectionDestinationFactory(new CloseAfterUseConnectionDestination.Factory())
            .build();

    private StyxServer styxServer;

    private WireMockServer nonSslOrigin;
    private WireMockServer sslOrigin;

    @BeforeMethod
    public void startOrigins() {
        nonSslOrigin = new WireMockServer(wireMockConfig()
                .port(freePort()));

        sslOrigin = new WireMockServer(wireMockConfig()
                .httpsPort(freePort()));

        nonSslOrigin.start();
        sslOrigin.start();

        configureFor(nonSslOrigin.port());
        stubFor(WireMock.get(urlPathEqualTo("/")).willReturn(aResponse()
                .withHeader("origin", "first")
                .withBody("foo")
                .withStatus(OK.code())));

        configureFor(sslOrigin.port());
        stubFor(WireMock.get(urlPathEqualTo("/")).willReturn(aResponse()
                .withHeader("origin", "second")
                .withBody("foo")
                .withStatus(OK.code())));
    }

    @AfterMethod
    public void stopStyx() {
        styxServer.stop();
    }

    @AfterMethod
    public void stopOrigins() {
        nonSslOrigin.stop();
        sslOrigin.stop();
    }

    @Test
    public void recordsServerSideHttp() {
        styxServer = new StyxServer.Builder()
                .addRoute("/", nonSslOrigin.port())
                .start();

        FullHttpResponse response = doGet("/");

        assertThat(response.status(), is(OK));

        assertThat(styxServer.metrics().meter("styx.server.http.requests").getCount(), is(1L));
        assertThat(styxServer.metrics().meter("styx.server.https.requests").getCount(), is(0L));

        assertThat(styxServer.metrics().meter("styx.server.http.responses.200").getCount(), is(1L));
        assertThat(styxServer.metrics().meter("styx.server.https.responses.200").getCount(), is(0L));
    }

    @Test
    public void recordsServerSideHttps() {
        styxServer = new StyxServer.Builder()
                .addRoute("/", nonSslOrigin.port())
                .start();

        FullHttpResponse response = doHttpsGet("/");

        assertThat(response.status(), is(OK));

        assertThat(styxServer.metrics().meter("styx.server.http.requests").getCount(), is(0L));
        assertThat(styxServer.metrics().meter("styx.server.https.requests").getCount(), is(1L));

        assertThat(styxServer.metrics().meter("styx.server.http.responses.200").getCount(), is(0L));
        assertThat(styxServer.metrics().meter("styx.server.https.responses.200").getCount(), is(1L));
    }

    private FullHttpResponse doGet(String path) {
        return doRequest(client, "http", styxServer.proxyHttpPort(), startWithSlash(path));
    }

    private FullHttpResponse doHttpsGet(String path) {
        UrlConnectionHttpClient client1 = new UrlConnectionHttpClient(2000, 5000);
        return doRequest(client1, "https", styxServer.proxyHttpsPort(), path);
    }

    private static FullHttpResponse doRequest(HttpClient client, String protocol, int port, String path) {
        String url = format("%s://localhost:%s%s", protocol, port, startWithSlash(path));

        HttpRequest request = get(url)
                .body(body("foo", "bar", "baz"))
                .build();

        return waitForResponse(client.sendRequest(request));
    }

    // used to ensure that we do not increment counters for successive chunks
    private static Observable<ByteBuf> body(String... chunks) {
        return Observable.from(
                Stream.of(chunks)
                        .map(chunk -> Unpooled.copiedBuffer(chunk, UTF_8))
                        .collect(toList())
        );
    }

    private static String startWithSlash(String path) {
        return !path.isEmpty() && path.charAt(0) == '/' ? path : "/" + path;
    }

}
