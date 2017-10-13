/**
 * Copyright (C) 2013-2017 Expedia Inc.
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
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.AbstractIdleService;
import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.client.Connection;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.client.retrypolicy.spi.RetryPolicy;
import com.hotels.styx.api.metrics.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.api.netty.exceptions.OriginUnreachableException;
import com.hotels.styx.api.netty.exceptions.ResponseTimeoutException;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.client.connectionpool.ConnectionPoolSettings;
import com.hotels.styx.client.connectionpool.SimpleConnectionPool;
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor;
import com.hotels.styx.client.netty.HttpRequestOperation;
import com.hotels.styx.client.netty.connectionpool.NettyConnection;
import com.hotels.styx.client.netty.connectionpool.NettyConnectionFactory;
import com.hotels.styx.client.netty.connectionpool.StubConnectionPool;
import com.hotels.styx.client.ssl.TlsSettings;
import com.hotels.styx.client.stickysession.StickySessionConfig;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.local.LocalChannel;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.observers.Observers;
import rx.observers.TestSubscriber;
import rx.subjects.PublishSubject;

import java.io.IOException;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntConsumer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.google.common.base.Throwables.propagate;
import static com.hotels.styx.api.HttpHeaderNames.CHUNKED;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpHeaderNames.TRANSFER_ENCODING;
import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.HttpResponse.Builder.response;
import static com.hotels.styx.api.Id.GENERIC_APP;
import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static com.hotels.styx.api.support.HostAndPorts.localhost;
import static com.hotels.styx.client.Protocol.HTTP;
import static com.hotels.styx.client.Protocol.HTTPS;
import static com.hotels.styx.client.StyxHttpClient.newHttpClientBuilder;
import static com.hotels.styx.client.retry.RetryPolicies.doNotRetry;
import static com.hotels.styx.client.stickysession.StickySessionConfig.newStickySessionConfigBuilder;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static rx.Observable.just;

public class StyxHttpClientTest {
    private static final Observer<HttpResponse> DO_NOTHING = Observers.empty();
    private static final Origin SOME_ORIGIN = newOriginBuilder(localhost(9090)).applicationId(GENERIC_APP).build();
    private static final HttpRequest SOME_REQ = get("/some-req").build();

    private final OriginStatsFactory originStatsFactory = new OriginStatsFactory(new CodaHaleMetricRegistry());
    private final StickySessionConfig stickySessionConfig = stickySessionDisabled();
    private HttpRequestOperationFactory requestOperationFactory;
    private MetricRegistry metricRegistry;

    @BeforeMethod
    public void setUp() throws Exception {
        requestOperationFactory = this::mockHttpRequestOperation;
        metricRegistry = new CodaHaleMetricRegistry();
    }

    @Test
    public void sendsHttp() throws IOException {
        withOrigin(HTTP, port -> {
            HttpResponse response = httpClient(port).sendRequest(httpRequest(8080))
                    .doOnNext(StyxHttpClientTest::consumeBody)
                    .toBlocking()
                    .single();

            assertThat(response.status(), is(OK));
        });
    }

    @Test
    public void sendsHttps() throws IOException {
        withOrigin(HTTPS, port -> {
            HttpResponse response = httpsClient(port).sendRequest(httpsRequest(8080))
                    .doOnNext(StyxHttpClientTest::consumeBody)
                    .toBlocking()
                    .single();

            assertThat(response.status(), is(OK));
        });
    }

    @Test(expectedExceptions = Exception.class)
    public void cannotSendHttpsWhenConfiguredForHttp() throws IOException {
        withOrigin(HTTPS, port -> {
            HttpResponse response = httpClient(port).sendRequest(httpsRequest(8080))
                    .doOnNext(StyxHttpClientTest::consumeBody)
                    .toBlocking()
                    .single();

            assertThat(response.status(), is(OK));
        });
    }

    @Test(expectedExceptions = Exception.class)
    public void cannotSendHttpWhenConfiguredForHttps() throws IOException {
        withOrigin(HTTP, port -> {
            HttpResponse response = httpsClient(port).sendRequest(httpRequest(8080))
                    .doOnNext(StyxHttpClientTest::consumeBody)
                    .toBlocking()
                    .single();

            assertThat(response.status(), is(OK));
        });
    }

    private static HttpClient httpClient(int originPort) {
        return newHttpClientBuilder(backendWithOrigins(originPort))
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

        ConnectionPool.Factory connectionPoolFactory = new SimpleConnectionPool.Factory()
                .connectionFactory(new NettyConnectionFactory.Builder()
                        .tlsSettings(tlsSettings)
                        .build())
                .connectionPoolSettings(new ConnectionPoolSettings.Builder()
                        .build());

        return newHttpClientBuilder(
                backendBuilderWithOrigins(originPort)
                        .https(tlsSettings)
                        .build())
                .connectionPoolFactory(connectionPoolFactory)
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

    private static Subscription consumeBody(HttpResponse response) {
        return response.body().content().subscribe();
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

    @Test
    public void asksTheConnectionPoolForAConnection() {
        Connection connection = aConnection();
        ConnectionPool pool = mockPool(connection);
        StyxHttpClient styxHttpClient = newStyxHttpClient(pool);
        styxHttpClient.sendRequest(SOME_REQ).subscribe(DO_NOTHING);
        verify(pool).borrowConnection();
    }

    @Test
    public void releasesConnectionWhenOperationExecutionIsFinished() {
        Connection connection = aConnection();
        ConnectionPool pool = mockPool(connection);
        StyxHttpClient styxHttpClient = newStyxHttpClient(pool);
        TestSubscriber<HttpResponse> subscriber = new TestSubscriber<>();
        styxHttpClient.sendRequest(SOME_REQ).subscribe(subscriber);
        subscriber.awaitTerminalEvent();
        verify(pool).returnConnection(connection);
    }

    @Test
    public void closesConnectionWhenTransactionIsUnsubscribed() throws IOException {
        Connection connection = aConnection();
        ConnectionPool pool = mockPool(connection);
        PublishSubject<HttpResponse> responseSubject = PublishSubject.create();

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendWithOrigins(connection.getOrigin().host().getPort()))
                .connectionPoolFactory(origin -> pool)
                .requestOperationFactory(newRequestOperationFactory(responseSubject))
                .build();

        Subscription txnSubscription = styxHttpClient.sendRequest(SOME_REQ).subscribe();
        responseSubject.onNext(response(OK).build());

        txnSubscription.unsubscribe();
        responseSubject.onCompleted();

        verify(pool).closeConnection(connection);
    }

    @Test
    public void closesConnectionWhenTransactionIsCancelled() throws IOException {
        Connection connection = aConnection();
        ConnectionPool pool = mockPool(connection);
        PublishSubject<HttpResponse> responseSubject = PublishSubject.create();

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendWithOrigins(connection.getOrigin().host().getPort()))
                .connectionPoolFactory(origin -> pool)
                .requestOperationFactory(newRequestOperationFactory(responseSubject))
                .build();

        Observable<HttpResponse> transaction = styxHttpClient.sendRequest(SOME_REQ);

        Subscription subscription = transaction.subscribe();
        responseSubject.onNext(response(OK).build());

        subscription.unsubscribe();

        verify(pool).closeConnection(connection);
    }

    @Test
    public void updatesCountersWhenTransactionIsCancelled() throws IOException {
        PublishSubject<HttpResponse> responseSubject = PublishSubject.create();
        MetricRegistry metricRegistry = new CodaHaleMetricRegistry();
        Origin origin = originWithId("localhost:234", "App-X", "Origin-Y");
        ConnectionPool pool = connectionPool(origin);

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendWithOrigins(origin.host().getPort()))
                .connectionPoolFactory(o -> pool)
                .requestOperationFactory(newRequestOperationFactory(responseSubject))
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
        StyxHttpClient styxHttpClient = styxClient(nettyConnection(mock(Channel.class)), responseSubject, retryPolicy);

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
    public void releasesConnectionWhenOperationExecutionFails() {
        Connection connection = openConnection(SOME_ORIGIN);
        ConnectionPool pool = mockPool(connection);
        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendWithOrigins(SOME_ORIGIN.host().getPort()))
                .requestOperationFactory(newFailingRequestOperationFactory(new RuntimeException()))
                .connectionPoolFactory(origin -> pool)
                .retryPolicy(doNotRetry())
                .build();

        TestSubscriber<HttpResponse> subscriber = new TestSubscriber<>();
        styxHttpClient.sendRequest(SOME_REQ).subscribe(subscriber);
        subscriber.awaitTerminalEvent();
        verify(pool).closeConnection(connection);
    }

    @Test
    public void doesNotReleaseConnectionWhenBorrowingFails() {
        ConnectionPool pool = mock(ConnectionPool.class);
        when(pool.getOrigin()).thenReturn(SOME_ORIGIN);
        when(pool.borrowConnection()).thenReturn(Observable.error(new ResponseTimeoutException(SOME_ORIGIN)));

        StyxHttpClient styxHttpClient = newStyxHttpClient(pool);

        TestSubscriber<HttpResponse> subscriber = new TestSubscriber<>();
        styxHttpClient.sendRequest(SOME_REQ).subscribe(subscriber);

        subscriber.awaitTerminalEvent();
        verify(pool, never()).returnConnection(any(Connection.class));
    }

    @Test
    public void notifiesSubscribersOnConnectionBorrowingFailure() {
        ConnectionPool pool = mock(ConnectionPool.class);
        when(pool.getOrigin()).thenReturn(SOME_ORIGIN);
        Throwable timeoutException = new ResponseTimeoutException(SOME_ORIGIN);
        when(pool.borrowConnection()).thenReturn(Observable.error(timeoutException));

        StyxHttpClient styxClient = new StyxHttpClient.Builder(backendWithOrigins(SOME_ORIGIN.host().getPort()))
                .retryPolicy(doNotRetry())
                .connectionPoolFactory(origin -> pool)
                .build();

        TestSubscriber<HttpResponse> subscriber = new TestSubscriber<>();
        styxClient.sendRequest(SOME_REQ).subscribe(subscriber);
        subscriber.awaitTerminalEvent();
        assertThat(getOnErrorEvents(subscriber), contains(timeoutException));
    }

    @Test
    public void notifiesSubscribersOnOperationFailure() {
        ConnectionPool pool = mockPool(openConnection(SOME_ORIGIN));
        Throwable operationException = new RuntimeException("Error happened");
        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendWithOrigins(SOME_ORIGIN.host().getPort()))
                .requestOperationFactory(newFailingRequestOperationFactory(operationException))
                .connectionPoolFactory(origin -> pool)
                .build();

        TestSubscriber<HttpResponse> subscriber = new TestSubscriber<>();
        styxHttpClient.sendRequest(SOME_REQ).subscribe(subscriber);
        subscriber.awaitTerminalEvent();
        assertThat(getOnErrorEvents(subscriber), contains(operationException));
    }

    @Test
    public void notifiesSubscribersOnSuccessfulOperation() {
        ConnectionPool pool = mockPool(openConnection(SOME_ORIGIN));
        StyxHttpClient styxHttpClient = newStyxHttpClient(pool);

        TestSubscriber<HttpResponse> subscriber = new TestSubscriber<>();
        styxHttpClient.sendRequest(SOME_REQ).subscribe(subscriber);
        subscriber.awaitTerminalEvent();

        assertThat(responseFrom(subscriber).status(), is(OK));
    }

    @Test
    public void usesTheOriginSpecifiedInTheCookieIfStickySessionIsEnabled() {
        StickySessionConfig stickySessionConfig = newStickySessionConfigBuilder()
                .enabled(true)
                .build();

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(
                backendBuilderWithOrigins(SOME_ORIGIN.host().getPort())
                        .stickySessionConfig(stickySessionConfig)
                        .build())
                .requestOperationFactory(requestOperationFactory)
                .build();

        HttpRequest request = requestWithStickySessionCookie(GENERIC_APP, id("h1"));

        styxHttpClient.sendRequest(request).subscribe(new TestSubscriber<>());
    }

    @Test
    public void shouldStopMonitoringOriginsOnClientClose() {
        RecordingOriginHealthStatusMonitor monitor = new RecordingOriginHealthStatusMonitor();
        OriginHealthStatusMonitor.Factory factory = (id, healthCheckConfig, supplier) -> monitor;

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendWithOrigins(SOME_ORIGIN.host().getPort()))
                .originHealthStatusMonitorFactory(factory)
                .build();

        styxHttpClient.close();

        assertThat(monitor.isRunning(), is(false));
    }

    @Test
    public void incrementsResponseStatusMetricsForBadResponse() throws Exception {
        requestOperationFactory = requestOpFactory(request -> just(response(BAD_REQUEST).build()));
        StyxHttpClient styxHttpClient = newStyxHttpClient(mockPool(openConnection(SOME_ORIGIN)));

        HttpResponse response = styxHttpClient.sendRequest(SOME_REQ).toBlocking().first();
        assertThat(response.status(), is(BAD_REQUEST));
        assertThat(metricRegistry.counter("origins.response.status.400").getCount(), is(1L));
    }

    @Test
    public void incrementsResponseStatusMetricsFor401() throws Exception {
        requestOperationFactory = requestOpFactory(request -> just(response(UNAUTHORIZED).build()));
        StyxHttpClient styxHttpClient = newStyxHttpClient(mockPool(openConnection(SOME_ORIGIN)));

        HttpResponse response = styxHttpClient.sendRequest(SOME_REQ).toBlocking().first();
        assertThat(response.status(), is(UNAUTHORIZED));
        assertThat(metricRegistry.counter("origins.response.status.401").getCount(), is(1L));
    }

    @Test
    public void incrementsResponseStatusMetricsFor500() throws Exception {
        requestOperationFactory = requestOpFactory(request -> just(response(INTERNAL_SERVER_ERROR).build()));
        StyxHttpClient styxHttpClient = newStyxHttpClient(mockPool(openConnection(SOME_ORIGIN)));

        HttpResponse response = styxHttpClient.sendRequest(SOME_REQ).toBlocking().first();
        assertThat(response.status(), is(INTERNAL_SERVER_ERROR));
        assertThat(metricRegistry.counter("origins.response.status.500").getCount(), is(1L));
    }

    @Test
    public void incrementsResponseStatusMetricsFor501() throws Exception {
        requestOperationFactory = requestOpFactory(request -> just(response(NOT_IMPLEMENTED).build()));
        StyxHttpClient styxHttpClient = newStyxHttpClient(mockPool(openConnection(SOME_ORIGIN)));

        HttpResponse response = styxHttpClient.sendRequest(SOME_REQ).toBlocking().first();
        assertThat(response.status(), is(NOT_IMPLEMENTED));
        assertThat(metricRegistry.counter("origins.response.status.501").getCount(), is(1L));
    }

    @Test
    public void doesNotIncrementMetricsForSuccessfulResponses() throws Exception {
        requestOperationFactory = requestOpFactory(request -> just(response(OK).build()));
        StyxHttpClient styxHttpClient = newStyxHttpClient(mockPool(openConnection(SOME_ORIGIN)));

        HttpResponse response = styxHttpClient.sendRequest(SOME_REQ).toBlocking().first();
        assertThat(response.status(), is(OK));
        assertThat(metricRegistry.counter("origins.response.status.200").getCount(), is(0L));
    }

    @Test
    public void removesBadContentLength() throws Exception {
        requestOperationFactory = requestOpFactory(request -> just(
                response(OK)
                        .addHeader(CONTENT_LENGTH, 50)
                        .addHeader(TRANSFER_ENCODING, CHUNKED)
                        .build()));

        StyxHttpClient styxHttpClient = newStyxHttpClientBuilder(mockPool(openConnection(SOME_ORIGIN)))
                .enableContentValidation()
                .build();

        HttpResponse response = styxHttpClient.sendRequest(SOME_REQ).toBlocking().first();
        assertThat(response.status(), is(OK));
        assertThat(response.contentLength().isPresent(), is(false));
        assertThat(response.header(TRANSFER_ENCODING).get(), is("chunked"));
    }

    private HttpRequestOperationFactory requestOpFactory(Function<HttpRequest, Observable<HttpResponse>> handler) {
        return request -> new HttpRequestOperation(request, originStatsFactory) {
            @Override
            public Observable<HttpResponse> execute(NettyConnection nettyConnection) {
                return handler.apply(request);
            }
        };
    }

    private StyxHttpClient newStyxHttpClient(ConnectionPool pool) {
        return new StyxHttpClient.Builder(
                backendBuilderWithOrigins(SOME_ORIGIN.host().getPort())
                        .stickySessionConfig(stickySessionConfig)
                        .build())
                .connectionPoolFactory(origin -> pool)
                .requestOperationFactory(requestOperationFactory)
                .metricsRegistry(metricRegistry)
                .build();
    }

    private StyxHttpClient.Builder newStyxHttpClientBuilder(ConnectionPool pool) {
        return new StyxHttpClient.Builder(
                backendBuilderWithOrigins(SOME_ORIGIN.host().getPort())
                        .stickySessionConfig(stickySessionConfig)
                        .build())
                .connectionPoolFactory(origin -> pool)
                .requestOperationFactory(requestOperationFactory)
                .metricsRegistry(metricRegistry);
    }

    private static ConnectionPool mockPool(Connection connection) {
        ConnectionPool pool = mock(ConnectionPool.class);
        when(pool.isExhausted()).thenReturn(false);
        when(pool.getOrigin()).thenReturn(connection.getOrigin());
        when(pool.borrowConnection()).thenReturn(just(connection));
        return pool;
    }

    private static Iterable<Throwable> getOnErrorEvents(TestSubscriber<?> subscriber) {
        return subscriber.getOnErrorEvents();
    }

    private static HttpResponse responseFrom(TestSubscriber<HttpResponse> subscriber) {
        return subscriber.getOnNextEvents().iterator().next();
    }

    private StyxHttpClient styxClient(Connection connection, PublishSubject<HttpResponse> responseSubject, RetryPolicy retryPolicy) {
        StubConnectionPool pool = new StubConnectionPool(connection);

        return new StyxHttpClient.Builder(backendWithOrigins(SOME_ORIGIN.host().getPort()))
                .connectionPoolFactory(origin -> pool)
                .requestOperationFactory(newRequestOperationFactory(responseSubject))
                .retryPolicy(retryPolicy)
                .build();
    }

    private static Connection nettyConnection(Channel channel) {
        when(channel.pipeline()).thenReturn(mock(ChannelPipeline.class));
        when(channel.closeFuture()).thenReturn(mock(ChannelFuture.class));
        when(channel.isOpen()).thenReturn(true);
        return new NettyConnection(SOME_ORIGIN, channel);
    }

    private static HttpRequest requestWithStickySessionCookie(Id applicationId, Id originId) {
        return get("/some-req")
                .addCookie("styx_origin_" + applicationId, originId.toString())
                .build();
    }

    private static Origin originWithId(String host, String appId, String originId) {
        return newOriginBuilder(HostAndPort.fromString(host))
                .applicationId(appId)
                .id(originId)
                .build();
    }

    private static ConnectionPool connectionPool(Origin origin) {
        Connection connection = aConnection(origin);
        return mockPool(connection);
    }

    private static Connection openConnection(Origin origin) {
        return new NettyConnection(origin, new LocalChannel());
    }

    private static Connection aConnection() {
        return aConnection(SOME_ORIGIN);
    }

    private static Connection aConnection(Origin origin) {
        return openConnection(origin);
    }

    private HttpRequestOperation mockHttpRequestOperation(HttpRequest request) {
        return new HttpRequestOperation(request, originStatsFactory) {
            @Override
            public Observable<HttpResponse> execute(NettyConnection nettyConnection) {
                return just(response().body("body").build());
            }
        };
    }

    private HttpRequestOperationFactory newFailingRequestOperationFactory(Throwable operationException) {
        return request -> new FailingHttRequestOperation(request, originStatsFactory, operationException);
    }

    private HttpRequestOperationFactory newRequestOperationFactory(Observable<HttpResponse> responseSubject) {
        return request ->
                new HttpRequestOperation(request, originStatsFactory) {
                    @Override
                    public Observable<HttpResponse> execute(NettyConnection nettyConnection) {
                        return responseSubject;
                    }
                };
    }

    public static class RecordingOriginHealthStatusMonitor extends AbstractIdleService implements OriginHealthStatusMonitor {
        @Override
        protected void startUp() {
        }

        @Override
        protected void shutDown() {
        }

        @Override
        public OriginHealthStatusMonitor monitor(Set<Origin> origins) {
            return this;
        }

        @Override
        public OriginHealthStatusMonitor stopMonitoring(Set<Origin> origins) {
            return this;
        }

        @Override
        public OriginHealthStatusMonitor addOriginStatusListener(OriginHealthStatusMonitor.Listener listener) {
            return this;
        }
    }

    private static class FailingHttRequestOperation extends HttpRequestOperation {
        private final Throwable ex;

        FailingHttRequestOperation(HttpRequest request, OriginStatsFactory originStatsFactory, Throwable ex) {
            super(request, originStatsFactory);
            this.ex = ex;
        }

        @Override
        public Observable<HttpResponse> execute(NettyConnection nettyConnection) {
            throw propagate(ex);
        }
    }
}
