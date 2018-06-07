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
package com.hotels.styx.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.hotels.styx.api.FullHttpRequest;
import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.client.Connection;
import com.hotels.styx.api.service.TlsSettings;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observable;

import java.io.IOException;
import java.util.function.IntConsumer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.hotels.styx.api.FullHttpRequest.get;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpHeaderNames.USER_AGENT;
import static com.hotels.styx.api.messages.HttpResponseStatus.OK;
import static com.hotels.styx.client.Protocol.HTTP;
import static com.hotels.styx.client.Protocol.HTTPS;
import static com.hotels.styx.common.StyxFutures.await;
import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static com.hotels.styx.support.server.UrlMatchingStrategies.urlStartingWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SimpleHttpClientTest {
    private FullHttpRequest anyRequest;
    private static int MAX_LENGTH = 1024;

    @BeforeMethod
    public void setUp() {
        anyRequest = get("/foo.txt")
                .header(HOST, "localhost:1234")
                .build();
    }

    @Test
    public void sendsHttp() throws IOException {
        withOrigin(HTTP, port -> {
            FullHttpResponse response = await(httpClient().sendRequest(httpRequest(port)));
            assertThat(response.status(), is(OK));
        });
    }

    @Test
    public void sendsHttps() throws IOException {
        withOrigin(HTTPS, port -> {
            FullHttpResponse response = await(httpsClient().sendRequest(httpsRequest(port)));
            assertThat(response.status(), is(OK));
        });
    }

    @Test(expectedExceptions = Exception.class)
    public void cannotSendHttpsWhenConfiguredForHttp() throws IOException {
        withOrigin(HTTPS, port -> {
            FullHttpResponse response = await(httpClient().sendRequest(httpsRequest(port)));
            assertThat(response.status(), is(OK));
        });
    }

    @Test(expectedExceptions = Exception.class)
    public void cannotSendHttpWhenConfiguredForHttps() throws IOException {
        withOrigin(HTTP, port -> {
            FullHttpResponse response = await(httpsClient().sendRequest(httpRequest(port)));
            assertThat(response.status(), is(OK));
        });
    }

    private SimpleHttpClient httpClient() {
        return new SimpleHttpClient.Builder()
                .build();
    }

    private SimpleHttpClient httpsClient() {
        return new SimpleHttpClient.Builder()
                .connectionSettings(new ConnectionSettings(1000))
                .tlsSettings(new TlsSettings.Builder()
                        .authenticate(false)
                        .build())
                .responseTimeoutMillis(6000)
                .build();
    }

    private FullHttpRequest httpRequest(int port) {
        return get("http://localhost:" + port)
                .header(HOST, "localhost:" + port)
                .build();
    }

    private FullHttpRequest httpsRequest(int port) {
        return get("https://localhost:" + port)
                .header(HOST, "localhost:" + port)
                .build();
    }

    private void withOrigin(Protocol protocol, IntConsumer portConsumer) {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort().dynamicHttpsPort());

        try {
            server.start();
            int port = protocol == HTTP ? server.port() : server.httpsPort();
            server.stubFor(WireMock.get(urlStartingWith("/")).willReturn(aResponse().withStatus(200)));
            portConsumer.accept(port);
        } finally {
            server.stop();
        }
    }

    @Test
    public void willNotSetAnyUserAgentIfNotSpecified() {
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.write(any(HttpRequest.class))).thenReturn(Observable.just(HttpResponse.response().build()));

        SimpleHttpClient client = new SimpleHttpClient.Builder()
                .setConnectionFactory((origin, connectionSettings) -> Observable.just(mockConnection))
                .build();

        client.sendRequest(anyRequest);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockConnection).write(captor.capture());
        assertThat(captor.getValue().header(USER_AGENT), isAbsent());
    }

    @Test
    public void setsTheSpecifiedUserAgentWhenSpecified() {
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.write(any(HttpRequest.class))).thenReturn(Observable.just(HttpResponse.response().build()));

        SimpleHttpClient client = new SimpleHttpClient.Builder()
                .setConnectionFactory((origin, connectionSettings) -> Observable.just(mockConnection))
                .userAgent("Styx/5.6")
                .build();

        client.sendRequest(anyRequest);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockConnection).write(captor.capture());
        assertThat(captor.getValue().header(USER_AGENT), isValue("Styx/5.6"));
    }

    @Test
    public void retainsTheUserAgentStringFromTheRequest() {
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.write(any(HttpRequest.class))).thenReturn(Observable.just(HttpResponse.response().build()));

        SimpleHttpClient client = new SimpleHttpClient.Builder()
                .setConnectionFactory((origin, connectionSettings) -> Observable.just(mockConnection))
                .userAgent("Styx/5.6")
                .build();

        client.sendRequest(get("/foo.txt")
                .header(USER_AGENT, "Foo/Bar")
                .header(HOST, "localhost:1234")
                .build());

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockConnection).write(captor.capture());
        assertThat(captor.getValue().header(USER_AGENT), isValue("Foo/Bar"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void requestWithNoHostOrUrlAuthorityCausesException() {
        FullHttpRequest request = get("/foo.txt").build();

        SimpleHttpClient client = new SimpleHttpClient.Builder().build();

        await(client.sendRequest(request));
    }
}
