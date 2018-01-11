/**
 * Copyright (C) 2013-2018 Expedia Inc.
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
package com.hotels.styx.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;
import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.client.Connection;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import com.hotels.styx.api.client.retrypolicy.spi.RetryPolicy;
import com.hotels.styx.api.metrics.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.api.netty.exceptions.OriginUnreachableException;
import com.hotels.styx.api.netty.exceptions.ResponseTimeoutException;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.client.ssl.TlsSettings;
import com.hotels.styx.client.stickysession.StickySessionConfig;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.observers.Observers;
import rx.observers.TestSubscriber;
import rx.subjects.PublishSubject;

import java.io.IOException;
import java.util.function.IntConsumer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.hotels.styx.api.HttpHeaderNames.CHUNKED;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpHeaderNames.TRANSFER_ENCODING;
import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.HttpResponse.Builder.response;
import static com.hotels.styx.api.Id.GENERIC_APP;
import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static com.hotels.styx.api.support.HostAndPorts.localhost;
import static com.hotels.styx.client.Protocol.HTTP;
import static com.hotels.styx.client.StyxHttpClient.newHttpClientBuilder;
import static com.hotels.styx.client.retry.RetryPolicies.doNotRetry;
import static com.hotels.styx.client.stickysession.StickySessionConfig.stickySessionDisabled;
import static com.hotels.styx.support.server.UrlMatchingStrategies.urlStartingWith;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_IMPLEMENTED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static rx.Observable.just;

public class StyxHttpClientTest {
    private static final Observer<HttpResponse> DO_NOTHING = Observers.empty();
    private static final Origin SOME_ORIGIN = newOriginBuilder(localhost(9090)).applicationId(GENERIC_APP).build();
    private static final HttpRequest SOME_REQ = get("/some-req").build();
    private static final int MAX_LENGTH = 1024;

    private final OriginStatsFactory originStatsFactory = new OriginStatsFactory(new CodaHaleMetricRegistry());
    private final StickySessionConfig stickySessionConfig = stickySessionDisabled();
    private MetricRegistry metricRegistry;

    @BeforeMethod
    public void setUp() {
        metricRegistry = new CodaHaleMetricRegistry();
    }

//    @Test
//    public void sendsHttp() {
//        withOrigin(HTTP, port -> {
//            FullHttpResponse response = httpClient(port).sendRequest(httpRequest(8080))
//                    .flatMap(r -> r.toFullResponse(MAX_LENGTH))
//                    .toBlocking()
//                    .single();
//
//            assertThat(response.status(), is(HttpResponseStatus.OK));
//        });
//    }
//
//    @Test
//    public void sendsHttps() {
//        withOrigin(HTTPS, port -> {
//            FullHttpResponse response = httpsClient(port).sendRequest(httpsRequest(8080))
//                    .flatMap(r -> r.toFullResponse(MAX_LENGTH))
//                    .toBlocking()
//                    .single();
//
//            assertThat(response.status(), is(HttpResponseStatus.OK));
//        });
//    }
//
//    @Test(expectedExceptions = Exception.class)
//    public void cannotSendHttpsWhenConfiguredForHttp() {
//        withOrigin(HTTPS, port -> {
//            httpClient(port).sendRequest(httpsRequest(8080))
//                    .flatMap(r -> r.toFullResponse(MAX_LENGTH))
//                    .toBlocking()
//                    .single();
//        });
//    }
//
//    @Test(expectedExceptions = Exception.class)
//    public void cannotSendHttpWhenConfiguredForHttps() {
//        withOrigin(HTTP, port -> {
//            httpsClient(port).sendRequest(httpRequest(8080))
//                    .flatMap(r -> r.toFullResponse(MAX_LENGTH))
//                    .toBlocking()
//                    .single();
//        });
//    }

    private static HttpClient httpClient(int originPort) {
        BackendService backendService = backendWithOrigins(originPort);

        return newHttpClientBuilder(backendService)
                .build();
    }

    private static BackendService backendWithOrigins(int originPort) {
        return new BackendService.Builder()
                .origins(newOriginBuilder("localhost", originPort).build())
                .build();
    }

    private static BackendService.Builder backendBuilderWithOrigins(int originPort) {
        return new BackendService.Builder()
                .origins(newOriginBuilder("localhost", originPort).build())
                ;
    }

    private static HttpClient httpsClient(int originPort) {
        TlsSettings tlsSettings = new TlsSettings.Builder()
                .trustAllCerts(true)
                .build();

        BackendService backendService = backendBuilderWithOrigins(originPort)
                .https(tlsSettings)
                .build();

        return newHttpClientBuilder(
                backendService)
                .build();
    }

    private static HttpRequest httpRequest(int port) {
        return get("http://localhost:" + port)
                .header(HOST, "localhost:" + port)
                .build();
    }

    private static HttpRequest httpsRequest(int port) {
        return get("https://localhost:" + port)
                .header(HOST, "localhost:" + port)
                .build();
    }

    private static void withOrigin(Protocol protocol, IntConsumer portConsumer) {
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

//    @Test
//    public void releasesConnectionWhenOperationExecutionIsFinished() {
//        Connection connection = aConnection();
//        ConnectionPool pool = mockPool(connection);
//        StyxHttpClient styxHttpClient = newStyxHttpClient(pool);
//        TestSubscriber<HttpResponse> subscriber = new TestSubscriber<>();
//        styxHttpClient.sendRequest(SOME_REQ).subscribe(subscriber);
//        subscriber.awaitTerminalEvent();
//        verify(pool).returnConnection(connection);
//    }

    private Connection mockConnection(Origin origin, Observable<HttpResponse> response) {
        Connection connection = mock(Connection.class);
        when(connection.write(any(HttpRequest.class))).thenReturn(response);
        when(connection.getOrigin()).thenReturn(SOME_ORIGIN);
        return connection;
    }

    private LoadBalancingStrategy mockLbStrategy(ConnectionPool pool) {
        LoadBalancingStrategy lbStrategy = mock(LoadBalancingStrategy.class);
        when(lbStrategy.vote(any(LoadBalancingStrategy.Context.class))).thenReturn(ImmutableList.of(pool));
        return lbStrategy;
    }

    @Test
    public void closesConnectionWhenTransactionIsUnsubscribed() {
        PublishSubject<HttpResponse> responseSubject = PublishSubject.create();
        Connection connection = mockConnection(SOME_ORIGIN, responseSubject);
        ConnectionPool pool = mockPool(SOME_ORIGIN, connection);
        LoadBalancingStrategy lbStrategy = mockLbStrategy(pool);

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendWithOrigins(9001))
                .loadBalancingStrategy(lbStrategy)
                .build();

        Subscription txnSubscription = styxHttpClient.sendRequest(SOME_REQ).subscribe();
        responseSubject.onNext(response(OK).build());

        txnSubscription.unsubscribe();
        responseSubject.onCompleted();
        verify(pool).closeConnection(any(Connection.class));
    }

    @Test
    public void updatesCountersWhenTransactionIsCancelled() {
        Origin origin = originWithId("localhost:234", "App-X", "Origin-Y");

        PublishSubject<HttpResponse> responseSubject = PublishSubject.create();
        Connection connection = mockConnection(origin, responseSubject);
        ConnectionPool pool = mockPool(origin, connection);
        LoadBalancingStrategy lbStrategy = mockLbStrategy(pool);

        BackendService backendService = backendWithOrigins(origin.host().getPort());

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendService)
                .loadBalancingStrategy(lbStrategy)
                .metricsRegistry(metricRegistry)
                .build();

        Observable<HttpResponse> transaction = styxHttpClient.sendRequest(SOME_REQ);
        Subscription subscription = transaction.subscribe();
        responseSubject.onNext(response(OK).build());

        subscription.unsubscribe();

        assertThat(metricRegistry.counter("origins.App-X.requests.cancelled").getCount(), is(1L));
        assertThat(metricRegistry.counter("origins.App-X.Origin-Y.requests.cancelled").getCount(), is(1L));
    }

    @Test
    public void doesNotRetryAnAlreadyCancelledRequest() {
        RetryPolicy retryPolicy = mock(RetryPolicy.class);

        PublishSubject<HttpResponse> responseSubject = PublishSubject.create();
        Connection connection = mockConnection(SOME_ORIGIN, responseSubject);
        ConnectionPool pool = mockPool(SOME_ORIGIN, connection);
        LoadBalancingStrategy lbStrategy = mockLbStrategy(pool);

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendWithOrigins(9001))
                .loadBalancingStrategy(lbStrategy)
                .retryPolicy(retryPolicy)
                .build();

        LoggingTestSupport support = new LoggingTestSupport(StyxHttpClient.class);
        try {
            Subscription subscription = styxHttpClient
                    .sendRequest(SOME_REQ)
                    .subscribe(new TestSubscriber<>());

            responseSubject.onNext(response(OK).build());
            subscription.unsubscribe();

            responseSubject.onError(new OriginUnreachableException(newOriginBuilder("hotels.com", 8080).build(), new RuntimeException("test")));

            verifyZeroInteractions(retryPolicy);
        } finally {
            support.stop();
        }
    }

    @Test
    public void passesThroughConnectionObservableExceptions() {
        Throwable timeoutException = new ResponseTimeoutException(SOME_ORIGIN);
        Connection connection = mockConnection(SOME_ORIGIN, Observable.error(timeoutException));
        ConnectionPool pool = mockPool(SOME_ORIGIN, connection);
        LoadBalancingStrategy lbStrategy = mockLbStrategy(pool);

        StyxHttpClient styxClient = new StyxHttpClient.Builder(backendWithOrigins(9001))
                .loadBalancingStrategy(lbStrategy)
                .retryPolicy(doNotRetry())
                .build();

        TestSubscriber<HttpResponse> subscriber = new TestSubscriber<>();
        styxClient.sendRequest(SOME_REQ).subscribe(subscriber);
        subscriber.awaitTerminalEvent();
        assertThat(getOnErrorEvents(subscriber), contains(timeoutException));
    }

    @Test
    public void notifiesSubscribersOnSuccessfulOperation() {
        Connection connection = mockConnection(SOME_ORIGIN, just(response(OK).build()));
        ConnectionPool pool = mockPool(SOME_ORIGIN, connection);
        LoadBalancingStrategy lbStrategy = mockLbStrategy(pool);

        StyxHttpClient styxClient = new StyxHttpClient.Builder(backendWithOrigins(9001))
                .loadBalancingStrategy(lbStrategy)
                .retryPolicy(doNotRetry())
                .build();

        HttpResponse response = styxClient.sendRequest(SOME_REQ).toBlocking().first();

        assertThat(response.status(), is(OK));
    }

    // TODO: Mikko: should be moved to styx client factory test:
    // This is because sticky session functionality is injected into
    // the client via Load Balancing Strategy alone
//    @Test
//    public void usesTheOriginSpecifiedInTheCookieIfStickySessionIsEnabled() {
//        Connection connection = mockConnection(SOME_ORIGIN, just(response(OK).build()));
//        ConnectionPool pool = mockPool(SOME_ORIGIN, connection);
//        LoadBalancingStrategy lbStrategy = mockLbStrategy(pool);
//
//        StickySessionConfig stickySessionConfig = newStickySessionConfigBuilder()
//                .enabled(true)
//                .build();
//
//        StyxHttpClient styxClient = new StyxHttpClient.Builder(backendWithOrigins(9001))
//                .loadBalancingStrategy(lbStrategy)
//                .retryPolicy(doNotRetry())
//                .build();
//
//        BackendService backendService = backendBuilderWithOrigins(SOME_ORIGIN.host().getPort())
//                .stickySessionConfig(stickySessionConfig)
//                .build();
//
//        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendService)
//                .build();
//
//        HttpRequest request = requestWithStickySessionCookie(GENERIC_APP, id("h1"));
//
//        styxHttpClient.sendRequest(request).subscribe(new TestSubscriber<>());
//    }

    @Test
    public void incrementsResponseStatusMetricsForBadResponse() {
        Connection connection = mockConnection(SOME_ORIGIN, just(response(BAD_REQUEST).build()));
        LoadBalancingStrategy lbStrategy = mockLbStrategy(mockPool(SOME_ORIGIN, connection));

        BackendService backendService = backendBuilderWithOrigins(SOME_ORIGIN.host().getPort())
                .stickySessionConfig(stickySessionConfig)
                .build();

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendService)
                .metricsRegistry(metricRegistry)
                .loadBalancingStrategy(lbStrategy)
                .build();

        HttpResponse response = styxHttpClient.sendRequest(SOME_REQ).toBlocking().first();

        assertThat(response.status(), is(BAD_REQUEST));
        assertThat(metricRegistry.counter("origins.response.status.400").getCount(), is(1L));
    }

    @Test
    public void incrementsResponseStatusMetricsFor401() {
        Connection connection = mockConnection(SOME_ORIGIN, just(response(UNAUTHORIZED).build()));
        LoadBalancingStrategy lbStrategy = mockLbStrategy(mockPool(SOME_ORIGIN, connection));

        BackendService backendService = backendBuilderWithOrigins(SOME_ORIGIN.host().getPort())
                .stickySessionConfig(stickySessionConfig)
                .build();

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendService)
                .metricsRegistry(metricRegistry)
                .loadBalancingStrategy(lbStrategy)
                .build();

        HttpResponse response = styxHttpClient.sendRequest(SOME_REQ).toBlocking().first();
        assertThat(response.status(), is(UNAUTHORIZED));
        assertThat(metricRegistry.counter("origins.response.status.401").getCount(), is(1L));
    }

    @Test
    public void incrementsResponseStatusMetricsFor500() {
        Connection connection = mockConnection(SOME_ORIGIN, just(response(INTERNAL_SERVER_ERROR).build()));
        LoadBalancingStrategy lbStrategy = mockLbStrategy(mockPool(SOME_ORIGIN, connection));

        BackendService backendService = backendBuilderWithOrigins(SOME_ORIGIN.host().getPort())
                .stickySessionConfig(stickySessionConfig)
                .build();

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendService)
                .metricsRegistry(metricRegistry)
                .loadBalancingStrategy(lbStrategy)
                .build();

        HttpResponse response = styxHttpClient.sendRequest(SOME_REQ).toBlocking().first();
        assertThat(response.status(), is(INTERNAL_SERVER_ERROR));
        assertThat(metricRegistry.counter("origins.response.status.500").getCount(), is(1L));
    }

    @Test
    public void incrementsResponseStatusMetricsFor501() {
        Connection connection = mockConnection(SOME_ORIGIN, just(response(NOT_IMPLEMENTED).build()));
        LoadBalancingStrategy lbStrategy = mockLbStrategy(mockPool(SOME_ORIGIN, connection));

        BackendService backendService = backendBuilderWithOrigins(SOME_ORIGIN.host().getPort())
                .stickySessionConfig(stickySessionConfig)
                .build();

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendService)
                .metricsRegistry(metricRegistry)
                .loadBalancingStrategy(lbStrategy)
                .build();

        HttpResponse response = styxHttpClient.sendRequest(SOME_REQ).toBlocking().first();
        assertThat(response.status(), is(NOT_IMPLEMENTED));
        assertThat(metricRegistry.counter("origins.response.status.501").getCount(), is(1L));
    }

    // TODO: Mikko: Why not to update 200 OK counters? Where is it incremented?
    @Test
    public void doesNotIncrementMetricsForSuccessfulResponses() {
        Connection connection = mockConnection(SOME_ORIGIN, just(response(OK).build()));
        LoadBalancingStrategy lbStrategy = mockLbStrategy(mockPool(SOME_ORIGIN, connection));

        BackendService backendService = backendBuilderWithOrigins(SOME_ORIGIN.host().getPort())
                .stickySessionConfig(stickySessionConfig)
                .build();

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendService)
                .metricsRegistry(metricRegistry)
                .loadBalancingStrategy(lbStrategy)
                .build();

        HttpResponse response = styxHttpClient.sendRequest(SOME_REQ).toBlocking().first();

        assertThat(response.status(), is(OK));
        assertThat(metricRegistry.counter("origins.response.status.200").getCount(), is(0L));
    }

    @Test
    public void removesBadContentLength() {
        Connection connection = mockConnection(SOME_ORIGIN, just(
                response(OK)
                        .addHeader(CONTENT_LENGTH, 50)
                        .addHeader(TRANSFER_ENCODING, CHUNKED)
                        .build()));
        LoadBalancingStrategy lbStrategy = mockLbStrategy(mockPool(SOME_ORIGIN, connection));

        BackendService backendService = backendBuilderWithOrigins(SOME_ORIGIN.host().getPort())
                .stickySessionConfig(stickySessionConfig)
                .build();

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendService)
                .metricsRegistry(metricRegistry)
                .loadBalancingStrategy(lbStrategy)
                .enableContentValidation()
                .build();

        HttpResponse response = styxHttpClient.sendRequest(SOME_REQ).toBlocking().first();

        assertThat(response.status(), is(OK));

        assertThat(response.contentLength().isPresent(), is(false));
        assertThat(response.header(TRANSFER_ENCODING).get(), is("chunked"));
    }

    private static ConnectionPool mockPool(Origin origin, Connection connection) {
        ConnectionPool pool = mock(ConnectionPool.class);
        when(pool.isExhausted()).thenReturn(false);
        when(pool.getOrigin()).thenReturn(origin);
        when(pool.borrowConnection()).thenReturn(just(connection));
        return pool;
    }

    private static Iterable<Throwable> getOnErrorEvents(TestSubscriber<?> subscriber) {
        return subscriber.getOnErrorEvents();
    }

    private static Origin originWithId(String host, String appId, String originId) {
        return newOriginBuilder(HostAndPort.fromString(host))
                .applicationId(appId)
                .id(originId)
                .build();
    }

}
