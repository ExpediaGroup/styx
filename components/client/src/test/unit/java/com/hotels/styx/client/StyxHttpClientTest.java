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
import com.hotels.styx.api.exceptions.OriginUnreachableException;
import com.hotels.styx.api.exceptions.ResponseTimeoutException;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.TlsSettings;
import com.hotels.styx.client.netty.connectionpool.NettyConnectionFactory;
import io.netty.handler.ssl.SslContext;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observable;

import java.util.concurrent.ExecutionException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.hotels.styx.api.FullHttpRequest.get;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpHeaderNames.USER_AGENT;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.common.StyxFutures.await;
import static com.hotels.styx.support.server.UrlMatchingStrategies.urlStartingWith;
import static java.lang.String.format;
import static java.lang.Thread.getAllStackTraces;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StyxHttpClientTest {
    private FullHttpRequest httpRequest;
    private FullHttpRequest secureRequest;
    private WireMockServer server;

    @BeforeMethod
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


    @AfterMethod
    public void tearDown() {
        server.stop();
    }

    @Test
    public void closesThreadPoolAfterUse() throws InterruptedException, ExecutionException {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .threadName("test-client")
                .build();

        // Ensures that a thread is created before the assertions
        client.send(httpRequest).get();

        assertThat(threadExists("test-client"), is(true));

        client.shutdown().get();

        assertThat(threadExists("test-client"), is(false));
    }

    private static Boolean threadExists(String threadName) {
        return getAllStackTraces().keySet().stream()
                .anyMatch(thread ->
                        thread.getName().startsWith(threadName));
    }

    @Test
    public void cannotBeModifiedAfterCreated() throws ExecutionException, InterruptedException {
        StyxHttpClient.Builder builder = new StyxHttpClient.Builder().userAgent("v1");

        StyxHttpClient client = builder.build();

        builder.userAgent("v2");

        assertThat(client.send(httpRequest).get().status(), is(OK));
        server.verify(
                getRequestedFor(urlEqualTo("/"))
                        .withHeader("User-Agent", equalTo("v1"))
        );
    }

    @Test
    public void usesDefaultUserAgent() throws ExecutionException, InterruptedException {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .userAgent("Simple-Client-Parent-Settings")
                .build();

        FullHttpResponse response = client
                .send(httpRequest)
                .get();

        assertThat(response.status(), is(OK));
        server.verify(
                getRequestedFor(urlEqualTo("/"))
                        .withHeader("User-Agent", equalTo("Simple-Client-Parent-Settings"))
        );
    }

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

    @Test
    public void replacesUserAgentIfAlreadyPresentInRequest() throws ExecutionException, InterruptedException {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .userAgent("My default user agent value")
                .build();

        FullHttpRequest request = get("/")
                .header(HOST, hostString(server.port()))
                .header(USER_AGENT, "My previous user agent")
                .build();

        client.send(request).get();

        server.verify(
                getRequestedFor(urlEqualTo("/"))
                        .withHeader("User-Agent", equalTo("My default user agent value"))
        );
    }


    @Test
    public void usesDefaultTlsSettings() throws ExecutionException, InterruptedException {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .tlsSettings(new TlsSettings.Builder().build())
                .build();

        FullHttpResponse response = client
                .send(secureRequest)
                .get();

        assertThat(response.status(), is(OK));
    }

    @Test
    public void overridesTlsSettingsWithSecure() throws ExecutionException, InterruptedException {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .build();

        FullHttpResponse response = client
                .secure()
                .send(secureRequest)
                .get();

        assertThat(response.status(), is(OK));
    }

    @Test
    public void overridesTlsSettingsWithSecureBoolean() throws ExecutionException, InterruptedException {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .build();

        FullHttpResponse response = client
                .secure(true)
                .send(secureRequest)
                .get();

        assertThat(response.status(), is(OK));
    }

    @Test
    public void overridesTlsSettingsWithSecureBooleanFalse() throws ExecutionException, InterruptedException {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .tlsSettings(new TlsSettings.Builder().build())
                .build();

        FullHttpResponse response = client
                .secure(false)
                .send(httpRequest)
                .get();

        assertThat(response.status(), is(OK));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void requiresValidTlsSettins() {
        new StyxHttpClient.Builder()
                .tlsSettings(null)
                .build();
    }

    @Test
    public void sendsMessagesInOriginUrlFormat() throws ExecutionException, InterruptedException {
        FullHttpResponse response = new StyxHttpClient.Builder()
                .build()
                .send(get("/index.html").header(HOST, hostString(server.port())).build())
                .get();

        assertThat(response.status(), is(OK));
        server.verify(
                getRequestedFor(urlEqualTo("/index.html"))
                        .withHeader("Host", equalTo(hostString(server.port())))
        );
    }

    @Test(expectedExceptions = ResponseTimeoutException.class)
    public void defaultResponseTimeout() throws Throwable {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .responseTimeout(1, SECONDS)
                .build();

        server.stubFor(WireMock.get(urlStartingWith("/slowResponse"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(3000)
                ));

        try {
            client.send(
                    get("/slowResponse")
                            .header(HOST, hostString(server.port()))
                            .build())
                    .get(2, SECONDS);
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    @Test(enabled = false)
    /*
     * Wiremock (or Jetty server) origin converts an absolute URL to an origin
     * form. Therefore we are unable to use an origin to verify that client used
     * an absolute URL. However I (Mikko) have verified with WireShark that the
     * request is indeed sent in absolute form.
     */
    public void sendsMessagesInAbsoluteUrlFormat() throws ExecutionException, InterruptedException {
        FullHttpResponse response = new StyxHttpClient.Builder()
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

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void requestWithNoHostOrUrlAuthorityCausesException() {
        FullHttpRequest request = get("/foo.txt").build();

        StyxHttpClient client = new StyxHttpClient.Builder().build();

        await(client.send(request));
    }

    @Test
    public void sendsToDefaultHttpPort() {
        NettyConnectionFactory factory = mockConnectionFactory();
        ArgumentCaptor<Origin> originCaptor = ArgumentCaptor.forClass(Origin.class);

        StyxHttpClient.sendRequestInternal(factory, get("/")
                        .header(HOST, "localhost")
                        .build(),
                new StyxHttpClient.Builder());

        verify(factory).createConnection(originCaptor.capture(), any(ConnectionSettings.class), any(SslContext.class));
        assertThat(originCaptor.getValue().port(), is(80));
    }

    @Test
    public void sendsToDefaultHttpsPort() {
        NettyConnectionFactory factory = mockConnectionFactory();
        ArgumentCaptor<Origin> originCaptor = ArgumentCaptor.forClass(Origin.class);

        StyxHttpClient.sendRequestInternal(factory, get("/")
                        .header(HOST, "localhost")
                        .build(),
                new StyxHttpClient.Builder().secure(true));

        verify(factory).createConnection(originCaptor.capture(), any(ConnectionSettings.class), any(SslContext.class));
        assertThat(originCaptor.getValue().port(), is(443));
    }

    private static NettyConnectionFactory mockConnectionFactory() {
        NettyConnectionFactory factory = mock(NettyConnectionFactory.class);
        when(factory.createConnection(any(Origin.class), any(ConnectionSettings.class), any(SslContext.class)))
                .thenReturn(Observable.just(mock(Connection.class)));
        return factory;
    }
}