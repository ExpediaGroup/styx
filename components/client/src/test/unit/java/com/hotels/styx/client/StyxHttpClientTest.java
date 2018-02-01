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

import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.client.Connection;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import com.hotels.styx.api.client.retrypolicy.spi.RetryPolicy;
import com.hotels.styx.api.metrics.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.api.netty.exceptions.NoAvailableHostsException;
import com.hotels.styx.api.netty.exceptions.OriginUnreachableException;
import com.hotels.styx.api.netty.exceptions.ResponseTimeoutException;
import com.hotels.styx.client.OriginsInventory.RemoteHostWrapper;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.client.stickysession.StickySessionConfig;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.mockito.InOrder;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.observers.Observers;
import rx.observers.TestSubscriber;
import rx.subjects.PublishSubject;

import static com.hotels.styx.api.HttpHeaderNames.CHUNKED;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.TRANSFER_ENCODING;
import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.HttpResponse.Builder.response;
import static com.hotels.styx.api.Id.GENERIC_APP;
import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static com.hotels.styx.api.support.HostAndPorts.localhost;
import static com.hotels.styx.client.TestSupport.remoteHost;
import static com.hotels.styx.client.retry.RetryPolicies.doNotRetry;
import static com.hotels.styx.client.stickysession.StickySessionConfig.stickySessionDisabled;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_IMPLEMENTED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
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
    private static final int MAX_LENGTH = 1024;

    private static final Origin ORIGIN_1 = newOriginBuilder(localhost(9091)).applicationId("app").id("app-01").build();
    private static final Origin ORIGIN_2 = newOriginBuilder(localhost(9092)).applicationId("app").id("app-02").build();
    private static final Origin ORIGIN_3 = newOriginBuilder(localhost(9093)).applicationId("app").id("app-03").build();
    private static final Origin ORIGIN_4 = newOriginBuilder(localhost(9094)).applicationId("app").id("app-04").build();

    private final OriginStatsFactory originStatsFactory = new OriginStatsFactory(new CodaHaleMetricRegistry());
    private final StickySessionConfig stickySessionConfig = stickySessionDisabled();
    private MetricRegistry metricRegistry;
    private final BackendService backendService = backendBuilderWithOrigins(SOME_ORIGIN.host().getPort())
            .stickySessionConfig(stickySessionConfig)
            .build();

    @BeforeMethod
    public void setUp() {
        metricRegistry = new CodaHaleMetricRegistry();
    }

    private static BackendService backendWithOrigins(int originPort) {
        return new BackendService.Builder()
                .origins(newOriginBuilder("localhost", originPort).build())
                .build();
    }

    private static BackendService.Builder backendBuilderWithOrigins(int originPort) {
        return new BackendService.Builder()
                .origins(newOriginBuilder("localhost", originPort).build());
    }

    private Connection mockConnection(Origin origin, Observable<HttpResponse> response) {
        Connection connection = mock(Connection.class);
        when(connection.write(any(HttpRequest.class))).thenReturn(response);
        when(connection.getOrigin()).thenReturn(SOME_ORIGIN);
        return connection;
    }

    private LoadBalancingStrategy mockLbStrategy(ConnectionPool pool) {
        LoadBalancingStrategy lbStrategy = mock(LoadBalancingStrategy.class);
        when(lbStrategy.vote(any(LoadBalancingStrategy.Context.class))).thenReturn(ImmutableList.of(remoteHost(pool.getOrigin(), pool, mock(StyxHostHttpClient.class))));
        return lbStrategy;
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
    public void sendsRequestToHostChosenByLoadBalancer() {
        StyxHostHttpClient hostClient = mockHostClient(Observable.just(response(OK).build()));

        LoadBalancingStrategy lbStategy = mock(LoadBalancingStrategy.class);
        when(lbStategy.vote(any(LoadBalancingStrategy.Context.class)))
                .thenReturn(
                        ImmutableList.of(new RemoteHostWrapper(SOME_ORIGIN.id(), SOME_ORIGIN, mock(ConnectionPool.class), hostClient)));

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendService)
                .metricsRegistry(metricRegistry)
                .loadBalancingStrategy(lbStategy)
                .build();

        HttpResponse response = styxHttpClient.sendRequest(SOME_REQ).toBlocking().first();

        assertThat(response.status(), is(OK));
        verify(hostClient).sendRequest(eq(SOME_REQ));
    }


    @Test
    public void retriesWhenRetryPolicyTellsToRetry() {
        ConnectionPool firstPool = mockPool(ORIGIN_1);

        StyxHostHttpClient firstClient = mockHostClient(Observable.error(
                new OriginUnreachableException(ORIGIN_1, new RuntimeException("An error occurred"))));

        ConnectionPool secondPool = mockPool(ORIGIN_2);
        StyxHostHttpClient secondClient = mockHostClient(Observable.just(response(OK).build()));
        RetryPolicy retryPolicy = mock(RetryPolicy.class);
        RetryPolicy.Outcome retryOutcome = mock(RetryPolicy.Outcome.class);
        when(retryOutcome.shouldRetry())
                .thenReturn(true)
                .thenReturn(false);
        when(retryPolicy.evaluate(any(RetryPolicy.Context.class), any(LoadBalancingStrategy.class), any(LoadBalancingStrategy.Context.class)))
                .thenReturn(retryOutcome);

        LoadBalancingStrategy lbStategy = mock(LoadBalancingStrategy.class);
        when(lbStategy.vote(any(LoadBalancingStrategy.Context.class)))
                .thenReturn(
                        ImmutableList.of(new RemoteHostWrapper(ORIGIN_1.id(), ORIGIN_1, firstPool, firstClient)))
                .thenReturn(
                        ImmutableList.of(new RemoteHostWrapper(ORIGIN_2.id(), ORIGIN_2, secondPool, secondClient))
                );

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendService)
                .metricsRegistry(metricRegistry)
                .loadBalancingStrategy(lbStategy)
                .retryPolicy(retryPolicy)
                .build();

        assertThat(styxHttpClient.sendRequest(SOME_REQ).toBlocking().first().status(), is(OK));

        InOrder ordered = inOrder(firstClient, secondClient);
        ordered.verify(firstClient).sendRequest(eq(SOME_REQ));
        ordered.verify(secondClient).sendRequest(eq(SOME_REQ));
    }

    @Test
    public void stopsRetriesWhenRetryPolicyTellsToStop() {
        ConnectionPool firstPool = mockPool(ORIGIN_1);
        StyxHostHttpClient firstClient = mockHostClient(Observable.error(new OriginUnreachableException(ORIGIN_1, new RuntimeException("An error occurred"))));

        ConnectionPool secondPool = mockPool(ORIGIN_2);
        StyxHostHttpClient secondClient = mockHostClient(Observable.error(new OriginUnreachableException(ORIGIN_2, new RuntimeException("An error occurred"))));

        ConnectionPool thirdPool = mockPool(ORIGIN_2);
        StyxHostHttpClient thirdClient = mockHostClient(Observable.error(new OriginUnreachableException(ORIGIN_2, new RuntimeException("An error occurred"))));

        RetryPolicy retryPolicy = mock(RetryPolicy.class);
        RetryPolicy.Outcome retryOutcome = mock(RetryPolicy.Outcome.class);
        when(retryOutcome.shouldRetry())
                .thenReturn(true)
                .thenReturn(false);
        when(retryPolicy.evaluate(any(RetryPolicy.Context.class), any(LoadBalancingStrategy.class), any(LoadBalancingStrategy.Context.class)))
                .thenReturn(retryOutcome)
                .thenReturn(retryOutcome);

        LoadBalancingStrategy lbStategy = mock(LoadBalancingStrategy.class);
        when(lbStategy.vote(any(LoadBalancingStrategy.Context.class)))
                .thenReturn(ImmutableList.of(new RemoteHostWrapper(ORIGIN_1.id(), ORIGIN_1, firstPool, firstClient)))
                .thenReturn(ImmutableList.of(new RemoteHostWrapper(ORIGIN_2.id(), ORIGIN_2, secondPool, secondClient)))
                .thenReturn(ImmutableList.of(new RemoteHostWrapper(ORIGIN_3.id(), ORIGIN_3, secondPool, secondClient))
                );

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendService)
                .metricsRegistry(metricRegistry)
                .loadBalancingStrategy(lbStategy)
                .retryPolicy(retryPolicy)
                .build();

        TestSubscriber<HttpResponse> testSubscriber = new TestSubscriber<>();
        styxHttpClient.sendRequest(SOME_REQ).subscribe(testSubscriber);
        testSubscriber.awaitTerminalEvent();

        assertThat(testSubscriber.getOnErrorEvents().size(), is(1));
        assertThat(testSubscriber.getOnErrorEvents().get(0), instanceOf(OriginUnreachableException.class));

        InOrder ordered = inOrder(firstClient, secondClient, thirdClient);
        ordered.verify(firstClient).sendRequest(eq(SOME_REQ));
        ordered.verify(secondClient).sendRequest(eq(SOME_REQ));
        ordered.verify(thirdClient, never()).sendRequest(eq(SOME_REQ));
    }


    @Test
    public void retriesAtMost3Times() {
        ConnectionPool firstPool = mockPool(ORIGIN_1);
        StyxHostHttpClient firstClient = mockHostClient(Observable.error(new OriginUnreachableException(ORIGIN_1, new RuntimeException("An error occurred"))));

        ConnectionPool secondPool = mockPool(ORIGIN_2);
        StyxHostHttpClient secondClient = mockHostClient(Observable.error(new OriginUnreachableException(ORIGIN_2, new RuntimeException("An error occurred"))));

        ConnectionPool thirdPool = mockPool(ORIGIN_3);
        StyxHostHttpClient thirdClient = mockHostClient(Observable.error(new OriginUnreachableException(ORIGIN_3, new RuntimeException("An error occurred"))));

        ConnectionPool fourthPool = mockPool(ORIGIN_4);
        StyxHostHttpClient fourthClient = mockHostClient(Observable.error(new OriginUnreachableException(ORIGIN_4, new RuntimeException("An error occurred"))));

        RetryPolicy retryPolicy = mock(RetryPolicy.class);
        RetryPolicy.Outcome retryOutcome = mock(RetryPolicy.Outcome.class);
        when(retryOutcome.shouldRetry())
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(true);

        when(retryPolicy.evaluate(any(RetryPolicy.Context.class), any(LoadBalancingStrategy.class), any(LoadBalancingStrategy.Context.class)))
                .thenReturn(retryOutcome)
                .thenReturn(retryOutcome)
                .thenReturn(retryOutcome)
                .thenReturn(retryOutcome);

        LoadBalancingStrategy lbStategy = mock(LoadBalancingStrategy.class);
        when(lbStategy.vote(any(LoadBalancingStrategy.Context.class)))
                .thenReturn(ImmutableList.of(new RemoteHostWrapper(ORIGIN_1.id(), ORIGIN_1, firstPool, firstClient)))
                .thenReturn(ImmutableList.of(new RemoteHostWrapper(ORIGIN_2.id(), ORIGIN_2, secondPool, secondClient)))
                .thenReturn(ImmutableList.of(new RemoteHostWrapper(ORIGIN_3.id(), ORIGIN_3, thirdPool, thirdClient)))
                .thenReturn(ImmutableList.of(new RemoteHostWrapper(ORIGIN_4.id(), ORIGIN_4, fourthPool, fourthClient)));

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendService)
                .metricsRegistry(metricRegistry)
                .loadBalancingStrategy(lbStategy)
                .retryPolicy(retryPolicy)
                .build();

        TestSubscriber<HttpResponse> testSubscriber = new TestSubscriber<>();
        styxHttpClient.sendRequest(SOME_REQ).subscribe(testSubscriber);
        testSubscriber.awaitTerminalEvent();

        assertThat(testSubscriber.getOnErrorEvents().size(), is(1));
        assertThat(testSubscriber.getOnErrorEvents().get(0), instanceOf(NoAvailableHostsException.class));

        InOrder ordered = inOrder(firstClient, secondClient, thirdClient, fourthClient);
        ordered.verify(firstClient).sendRequest(eq(SOME_REQ));
        ordered.verify(secondClient).sendRequest(eq(SOME_REQ));
        ordered.verify(thirdClient).sendRequest(eq(SOME_REQ));
        ordered.verify(fourthClient, never()).sendRequest(any(HttpRequest.class));
    }

    private StyxHostHttpClient mockHostClient(Observable<HttpResponse> responseObservable) {
        StyxHostHttpClient secondClient = mock(StyxHostHttpClient.class);
        when(secondClient.sendRequest(any(HttpRequest.class))).thenReturn(responseObservable);
        return secondClient;
    }

    private ConnectionPool mockPool(Origin someOrigin) {
        ConnectionPool firstPool = mock(ConnectionPool.class);
        when(firstPool.getOrigin()).thenReturn(someOrigin);
        return firstPool;
    }


    @Test(enabled = false)
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

    @Test(enabled = false)
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

    @Test(enabled = false)
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

    @Test(enabled = false)
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
    @Test(enabled = false)
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

    @Test(enabled = false)
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

    @Test(enabled = false)
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

    @Test(enabled = false)
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

    @Test(enabled = false)
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

    @Test(enabled = false)
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

    @Test(enabled = false)
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
