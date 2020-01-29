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
package com.hotels.styx.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.exceptions.ContentTimeoutException;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.TlsSettings;
import com.hotels.styx.client.netty.connectionpool.NettyConnectionFactory;
import io.netty.handler.ssl.SslContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.util.concurrent.ExecutionException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpHeaderNames.USER_AGENT;
import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.common.StyxFutures.await;
import static com.hotels.styx.support.server.UrlMatchingStrategies.urlStartingWith;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StyxHttpClientTest {

    private HttpRequest httpRequest;
    private HttpRequest secureRequest;
    private WireMockServer server;

    @BeforeEach
    public void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort().dynamicHttpsPort());
        server.start();
        server.stubFor(WireMock.get(urlStartingWith("/")).willReturn(aResponse().withStatus(200)));

        httpRequest = get("/")
                .header(HOST, hostString(server.port()))
                .build();

        secureRequest = get("/")
                .header(HOST, hostString(server.httpsPort()))
                .build();
    }


    @AfterEach
    public void tearDown() {
        server.stop();
    }

    /*
     * StyxHttpClient.Builder
     * - Cannot retrospectively modify user agent string
     */
    @Test
    public void cannotBeModifiedAfterCreated() throws ExecutionException, InterruptedException {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .userAgent("v1")
                .build();

        assertThat(client.send(httpRequest).get().status(), is(OK));
        server.verify(
                getRequestedFor(urlEqualTo("/"))
                        .withHeader("User-Agent", equalTo("v1"))
        );
    }

    /*
     * StyxHttpClient.Builder
     */
    @Test
    public void requiresValidTlsSettings() {
        assertThrows(NullPointerException.class,
            () -> new StyxHttpClient.Builder()
                .tlsSettings(null)
                .build());
    }


    /*
     * StyxHttpClient
     * - Uses default user-agent string.
     */
    @Test
    public void usesDefaultUserAgent() throws ExecutionException, InterruptedException {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .userAgent("Simple-Client-Parent-Settings")
                .build();

        HttpResponse response = client.send(httpRequest).get();

        assertThat(response.status(), is(OK));
        server.verify(
                getRequestedFor(urlEqualTo("/"))
                        .withHeader("User-Agent", equalTo("Simple-Client-Parent-Settings"))
        );
    }

    /*
     * StyxHttpClient
     * - Doesn't set any user-agent string if none is specified.
     */
    @Test
    public void doesNotSetAnyUserAgentIfNotSpecified() throws ExecutionException, InterruptedException {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .build();

        client.send(httpRequest).get();

        server.verify(
                getRequestedFor(urlEqualTo("/"))
                        .withoutHeader("User-Agent")
        );
    }

    /*
     * StyxHttpClient
     * - User-Agent string from the request takes precedence.
     */
    @Test
    public void replacesUserAgentIfAlreadyPresentInRequest() throws ExecutionException, InterruptedException {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .userAgent("My default user agent value")
                .build();

        HttpRequest request = get("/")
                .header(HOST, hostString(server.port()))
                .header(USER_AGENT, "My previous user agent")
                .build();

        client.send(request).get();

        server.verify(
                getRequestedFor(urlEqualTo("/"))
                        .withHeader("User-Agent", equalTo("My default user agent value"))
        );
    }


    /*
     * StyxHttpClient
     * - Applies default TLS settings
     */
    @Test
    public void usesDefaultTlsSettings() throws ExecutionException, InterruptedException {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .tlsSettings(new TlsSettings.Builder().build())
                .build();

        HttpResponse response = client.send(secureRequest)
                .get();

        assertThat(response.status(), is(OK));
    }

    /*
     * StyxHttpClientTransaction
     * - secure() method applies default TLS settings
     */
    @Test
    public void overridesTlsSettingsWithSecure() throws ExecutionException, InterruptedException {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .build();

        HttpResponse response = client
                .secure()
                .send(secureRequest)
                .get();

        assertThat(response.status(), is(OK));
    }

    /*
     * StyxHttpClientTransaction
     * - secure(true) applies default TLS settings
     */
    @Test
    public void overridesTlsSettingsWithSecureBoolean() throws ExecutionException, InterruptedException {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .build();

        HttpResponse response = client
                .secure(true)
                .send(secureRequest)
                .get();

        assertThat(response.status(), is(OK));
    }

    /*
     * StyxHttpClientTransaction
     * - secure(false) disables TLS protection
     */
    @Test
    public void overridesTlsSettingsWithSecureBooleanFalse() throws ExecutionException, InterruptedException {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .tlsSettings(new TlsSettings.Builder().build())
                .build();

        HttpResponse response = client
                .secure(false)
                .send(httpRequest)
                .get();

        assertThat(response.status(), is(OK));
    }

    /*
     * StyxHttpClient
     * - Sends a request when HTTP "request-target" is in origin format.
     * - Ref: https://tools.ietf.org/html/rfc7230#section-5.3.1
     */
    @Test
    public void sendsMessagesInOriginUrlFormat() throws ExecutionException, InterruptedException {
        HttpResponse response = new StyxHttpClient.Builder()
                .build()
                .send(
                        get("/index.html")
                                .header(HOST, hostString(server.port()))
                                .build())
                .get();

        assertThat(response.status(), is(OK));
        server.verify(
                getRequestedFor(urlEqualTo("/index.html"))
                        .withHeader("Host", equalTo(hostString(server.port())))
        );
    }

    /*
     * HttpClient.StreamingTransaction
     * - Sends LiveHttpRequest messages
     */
    @Test
    public void sendsStreamingRequests() throws ExecutionException, InterruptedException {
        LiveHttpResponse response = new StyxHttpClient.Builder()
                .build()
                .streaming()
                .send(httpRequest.stream())
                .get();

        assertThat(response.status(), is(OK));

        Mono.from(response.aggregate(10000)).block();

        server.verify(
                getRequestedFor(urlEqualTo("/"))
                        .withHeader("Host", equalTo(hostString(server.port())))
        );
    }

    /*
     * HttpClient.StreamingTransaction
     * - Sends LiveHttpRequest messages created from StyxHttpClientTransactions
     */
    @Test
    public void sendsSecureStreamingRequests() throws ExecutionException, InterruptedException {
        LiveHttpResponse response = new StyxHttpClient.Builder()
                .build()
                .secure(true)
                .streaming()
                .send(secureRequest.stream())
                .get();

        assertThat(response.status(), is(OK));

        Mono.from(response.aggregate(10000)).block();

        server.verify(
                getRequestedFor(urlEqualTo("/"))
                        .withHeader("Host", equalTo(hostString(server.httpsPort())))
        );
    }


    /*
     * StyxHttpClient
     * - Applies response timeout
     */
    @Test
    public void defaultResponseTimeout() throws Throwable {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .responseTimeout(1, SECONDS)
                .build();

        server.stubFor(WireMock.get(urlStartingWith("/slowResponse"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(3000)
                ));

        Exception e = assertThrows(ExecutionException.class,
                () -> client.send(
                    get("/slowResponse")
                            .header(HOST, hostString(server.port()))
                            .build())
                    .get(2, SECONDS));
        assertEquals(ContentTimeoutException.class, e.getCause().getClass());
    }

    @Disabled
    @Test
    /*
     * Wiremock (or Jetty server) origin converts an absolute URL to an origin
     * form. Therefore we are unable to use an origin to verify that client used
     * an absolute URL. However I (Mikko) have verified with WireShark that the
     * request is indeed sent in absolute form.
     */
    public void sendsMessagesInAbsoluteUrlFormat() throws ExecutionException, InterruptedException {
        HttpResponse response = new StyxHttpClient.Builder()
                .build()
                .send(get(format("http://%s/index.html", hostString(server.port()))).build())
                .get();

        assertThat(response.status(), is(OK));
        server.verify(
                getRequestedFor(urlEqualTo(format("http://%s/index.html", hostString(server.port()))))
                        .withHeader("Host", equalTo(hostString(server.port())))
        );
    }

    private String hostString(int port) {
        return "localhost:" + port;
    }

    /*
     * StyxHttpClient
     * - Rejects requests without URL authority or host header
     */
    @Test
    public void requestWithNoHostOrUrlAuthorityCausesException() {
        HttpRequest request = get("/foo.txt").build();

        StyxHttpClient client = new StyxHttpClient.Builder().build();

        assertThrows(IllegalArgumentException.class, () -> await(client.send(request)));
    }

    /*
     * StyxHttpClient.sendRequestInternal
     * - Uses default HTTP port 8080 when not specified in Host header
     */
    @Test
    public void sendsToDefaultHttpPort() {
        NettyConnectionFactory factory = mockConnectionFactory();
        ArgumentCaptor<Origin> originCaptor = ArgumentCaptor.forClass(Origin.class);

        StyxHttpClient.sendRequestInternal(factory, get("/")
                        .header(HOST, "localhost")
                        .build()
                        .stream(),
                new StyxHttpClient.Builder());

        verify(factory).createConnection(originCaptor.capture(), any(ConnectionSettings.class), nullable(SslContext.class));
        assertThat(originCaptor.getValue().port(), is(80));
    }

    /*
     * StyxHttpClient.sendRequestInternal
     * - Uses default HTTPS port 443 when not specified in Host header
     */
    @Test
    public void sendsToDefaultHttpsPort() {
        NettyConnectionFactory factory = mockConnectionFactory();
        ArgumentCaptor<Origin> originCaptor = ArgumentCaptor.forClass(Origin.class);

        StyxHttpClient.sendRequestInternal(factory, get("/")
                        .header(HOST, "localhost")
                        .build()
                        .stream(),
                new StyxHttpClient.Builder().secure(true));

        verify(factory).createConnection(originCaptor.capture(), any(ConnectionSettings.class), any(SslContext.class));
        assertThat(originCaptor.getValue().port(), is(443));
    }

    private static NettyConnectionFactory mockConnectionFactory() {
        NettyConnectionFactory factory = mock(NettyConnectionFactory.class);
        when(factory.createConnection(any(Origin.class), any(ConnectionSettings.class), nullable(SslContext.class)))
                .thenReturn(Mono.just(mock(Connection.class)));
        return factory;
    }
}