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
import com.hotels.styx.api.exceptions.OriginUnreachableException;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.TlsSettings;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observable;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.hotels.styx.api.FullHttpRequest.get;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.common.StyxFutures.await;
import static com.hotels.styx.support.server.UrlMatchingStrategies.urlStartingWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StyxHttpClientTest {
    private FullHttpRequest anyRequest;
    private static int MAX_LENGTH = 1024;
    private WireMockServer server;

    @BeforeMethod
    public void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort().dynamicHttpsPort());
        server.start();
        server.stubFor(WireMock.get(urlStartingWith("/")).willReturn(aResponse().withStatus(200)));

        anyRequest = get("/foo.txt")
                .header(HOST, "localhost:1234")
                .build();
    }


    @AfterMethod
    public void tearDown() {
        server.stop();
    }

    @Test
    public void closesThreadpoolAfterUse() throws InterruptedException, ExecutionException {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .threadName("test-client")
                .build();

        // Ensures that thread is created
        client.sendRequest(httpRequest(server.port())).get();

        assertThat(threadExists("test-client"), is(true));

        client.shutdown().get();

        assertThat(threadExists("test-client"), is(false));
    }

    private static Boolean threadExists(String threadName) {
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().startsWith(threadName)) return true;
        }
        return false;
    }

    @Test
    public void usesDefaultUserAgent() throws ExecutionException, InterruptedException {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .userAgent("Simple-Client-Parent-Settings")
                .build();

        FullHttpResponse response = client
                .sendRequest(httpRequest(server.port()))
                .get();

        assertThat(response.status(), is(OK));
        server.verify(
                getRequestedFor(urlEqualTo("/"))
                        .withHeader("User-Agent", equalTo("Simple-Client-Parent-Settings"))
        );
    }

    @Test
    public void usesPerTransactionUserAgent() throws ExecutionException, InterruptedException {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .userAgent("Simple-Client-Parent-Settings")
                .build();

        FullHttpResponse response = client
                .userAgent("Simple-Client-Transaction-Settings")
                .sendRequest(httpRequest(server.port()))
                .get();

        assertThat(response.status(), is(OK));
        server.verify(
                getRequestedFor(urlEqualTo("/"))
                        .withHeader("User-Agent", equalTo("Simple-Client-Transaction-Settings"))
        );
    }

    @Test
    public void usesDefaultTlsSettings() throws ExecutionException, InterruptedException {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .tlsSettings(new TlsSettings.Builder().build())
                .build();

        FullHttpResponse response = client
                .sendRequest(httpRequest(server.httpsPort()))
                .get();

        assertThat(response.status(), is(OK));
    }

    @Test
    public void overridesTlsSettingsWithSecure() throws ExecutionException, InterruptedException {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .build();

        FullHttpResponse response = client
                .secure()
                .sendRequest(httpRequest(server.httpsPort()))
                .get();

        assertThat(response.status(), is(OK));
    }

    @Test
    public void overridesTlsSettingsWithSecureBoolean() throws ExecutionException, InterruptedException {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .build();

        FullHttpResponse response = client
                .secure(true)
                .sendRequest(httpRequest(server.httpsPort()))
                .get();

        assertThat(response.status(), is(OK));
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "An insecure HTTP transaction is not allowed with HTTPS configuration.")
    public void canNotDowngradeDefaultSecureSettings() {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .tlsSettings(new TlsSettings.Builder().build())
                .build();

        client.secure(false);
    }

    @Test
    public void disablesTlsIfNullArgumentIsPassed() throws ExecutionException, InterruptedException {
        StyxHttpClient client = new StyxHttpClient.Builder()
                .tlsSettings(null)
                .build();

        FullHttpResponse response = client
                .secure(false)
                .sendRequest(httpRequest(server.port()))
                .get();

        assertThat(response.status(), is(OK));
    }

    @Test
    public void sendsHttp() {
        FullHttpResponse response = await(httpClient().sendRequest(httpRequest(server.port())));
        assertThat(response.status(), is(OK));
    }

    @Test
    public void sendsHttps() {
        FullHttpResponse response = await(httpsClient().sendRequest(httpsRequest(server.httpsPort())));
        assertThat(response.status(), is(OK));
    }

//    @Test(expectedExceptions = OriginUnreachableException.class)
//    public void throwsOriginUnreachableExceptionWhenDnsResolutionFails() throws Throwable {
//        try {
//            httpClient().sendRequest(get("/foo.txt").header(HOST, "a.b.c").build()).get();
//        } catch (ExecutionException cause) {
//            throw cause.getCause();
//        }
//    }

    @Test(expectedExceptions = Exception.class)
    public void cannotSendHttpsWhenConfiguredForHttp() {
        FullHttpResponse response = await(httpClient().sendRequest(httpsRequest(server.httpsPort())));
        assertThat(response.status(), is(OK));
    }

    @Test(expectedExceptions = Exception.class)
    public void cannotSendHttpWhenConfiguredForHttps() throws IOException {
        FullHttpResponse response = await(httpsClient().sendRequest(httpRequest(server.port())));
        assertThat(response.status(), is(OK));
    }

//    @Test
//    public void willNotSetAnyUserAgentIfNotSpecified() {
//        Connection mockConnection = mock(Connection.class);
//        when(mockConnection.write(any(HttpRequest.class))).thenReturn(Observable.just(response().build()));
//
//        StyxHttpClient client = new StyxHttpClient.Builder()
//                .setConnectionFactory((origin, connectionSettings) -> Observable.just(mockConnection))
//                .build();
//
//        client.sendRequest(anyRequest);
//
//        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
//        verify(mockConnection).write(captor.capture());
//        assertThat(captor.getValue().header(USER_AGENT), isAbsent());
//    }
//
//    @Test
//    public void setsTheSpecifiedUserAgent() {
//        Connection mockConnection = mock(Connection.class);
//        when(mockConnection.write(any(HttpRequest.class))).thenReturn(Observable.just(response().build()));
//
//        StyxHttpClient client = new StyxHttpClient.Builder()
//                .setConnectionFactory((origin, connectionSettings) -> Observable.just(mockConnection))
//                .userAgent("Styx/5.6")
//                .build();
//
//        client.sendRequest(anyRequest);
//
//        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
//        verify(mockConnection).write(captor.capture());
//        assertThat(captor.getValue().header(USER_AGENT), isValue("Styx/5.6"));
//    }
//
//    @Test
//    public void retainsTheUserAgentStringFromTheRequest() {
//        Connection mockConnection = mock(Connection.class);
//        when(mockConnection.write(any(HttpRequest.class))).thenReturn(Observable.just(response().build()));
//
//        StyxHttpClient client = new StyxHttpClient.Builder()
//                .setConnectionFactory((origin, connectionSettings) -> Observable.just(mockConnection))
//                .userAgent("Styx/5.6")
//                .build();
//
//        client.sendRequest(get("/foo.txt")
//                .header(USER_AGENT, "Foo/Bar")
//                .header(HOST, "localhost:1234")
//                .build());
//
//        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
//        verify(mockConnection).write(captor.capture());
//        assertThat(captor.getValue().header(USER_AGENT), isValue("Foo/Bar"));
//    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void requestWithNoHostOrUrlAuthorityCausesException() {
        FullHttpRequest request = get("/foo.txt").build();

        StyxHttpClient client = new StyxHttpClient.Builder().build();

        await(client.sendRequest(request));
    }

//    @Test
//    public void sendsToDefaultHttpPort() {
//        Connection.Factory connectionFactory = mockConnectionFactory(mockConnection(response(OK).build()));
//
//        StyxHttpClient client = new StyxHttpClient.Builder()
//                .setConnectionFactory(connectionFactory)
//                .build();
//
//        client.sendRequest(get("/")
//                .header(HOST, "localhost")
//                .build());
//
//        ArgumentCaptor<Origin> originCaptor = ArgumentCaptor.forClass(Origin.class);
//        verify(connectionFactory).createConnection(originCaptor.capture(), any(ConnectionSettings.class));
//
//        assertThat(originCaptor.getValue().port(), is(80));
//    }
//
//    @Test
//    public void sendsToDefaultHttpsPort() {
//        Connection.Factory connectionFactory = mockConnectionFactory(mockConnection(response(OK).build()));
//        TlsSettings tlsSettings = mock(TlsSettings.class);
//
//        StyxHttpClient client = new StyxHttpClient.Builder()
//                .setConnectionFactory(connectionFactory)
//                .tlsSettings(tlsSettings)
//                .build();
//
//        client.sendRequest(get("/")
//                .header(HOST, "localhost")
//                .build());
//
//        ArgumentCaptor<Origin> originCaptor = ArgumentCaptor.forClass(Origin.class);
//        verify(connectionFactory).createConnection(originCaptor.capture(), any(ConnectionSettings.class));
//
//        assertThat(originCaptor.getValue().port(), is(443));
//    }

    private StyxHttpClient httpClient() {
        return new StyxHttpClient.Builder()
                .build();
    }

    private StyxHttpClient httpsClient() {
        return new StyxHttpClient.Builder()
                .connectTimeout(1000)
                .tlsSettings(new TlsSettings.Builder()
                        .authenticate(false)
                        .build())
                .responseTimeout(6000)
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

    private Connection mockConnection(HttpResponse response) {
        Connection connection = mock(Connection.class);
        when(connection.write(any(HttpRequest.class))).thenReturn(Observable.just(response));
        return connection;
    }

    private Connection.Factory mockConnectionFactory(Connection connection) {
        Connection.Factory factory = mock(Connection.Factory.class);
        when(factory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(Observable.just(connection));
        return factory;
    }

}
