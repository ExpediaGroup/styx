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

import com.google.common.net.HostAndPort;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.client.RemoteHost;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import com.hotels.styx.api.client.retrypolicy.spi.RetryPolicy;
import com.hotels.styx.api.metrics.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.api.netty.exceptions.NoAvailableHostsException;
import com.hotels.styx.api.netty.exceptions.OriginUnreachableException;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.client.stickysession.StickySessionConfig;
import org.hamcrest.Matchers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.observers.Observers;
import rx.observers.TestSubscriber;
import rx.subjects.PublishSubject;

import java.util.List;
import java.util.Optional;

import static com.hotels.styx.api.HttpHeaderNames.CHUNKED;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.TRANSFER_ENCODING;
import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.HttpResponse.Builder.response;
import static com.hotels.styx.api.Id.GENERIC_APP;
import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static com.hotels.styx.api.client.RemoteHost.remoteHost;
import static com.hotels.styx.api.support.HostAndPorts.localhost;
import static com.hotels.styx.client.stickysession.StickySessionConfig.stickySessionDisabled;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_IMPLEMENTED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static rx.Observable.just;

public class StyxHttpClientTest {
    private static final Observer<HttpResponse> DO_NOTHING = Observers.empty();
    private static final Origin SOME_ORIGIN = newOriginBuilder(localhost(9090)).applicationId(GENERIC_APP).build();
    private static final HttpRequest SOME_REQ = get("/some-req").build();

    private static final Origin ORIGIN_1 = newOriginBuilder(localhost(9091)).applicationId("app").id("app-01").build();
    private static final Origin ORIGIN_2 = newOriginBuilder(localhost(9092)).applicationId("app").id("app-02").build();
    private static final Origin ORIGIN_3 = newOriginBuilder(localhost(9093)).applicationId("app").id("app-03").build();
    private static final Origin ORIGIN_4 = newOriginBuilder(localhost(9094)).applicationId("app").id("app-04").build();

    private final StickySessionConfig stickySessionConfig = stickySessionDisabled();
    private MetricRegistry metricRegistry;
    private final BackendService backendService = backendBuilderWithOrigins(SOME_ORIGIN.host().getPort())
            .stickySessionConfig(stickySessionConfig)
            .build();

    @BeforeMethod
    public void setUp() {
        metricRegistry = new CodaHaleMetricRegistry();
    }

    @Test
    public void sendsRequestToHostChosenByLoadBalancer() {
        StyxHostHttpClient hostClient = mockHostClient(just(response(OK).build()));

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendService)
                .metricsRegistry(metricRegistry)
                .loadBalancingStrategy(
                        mockLoadBalancer(
                                asList(remoteHost(SOME_ORIGIN, mock(ConnectionPool.class), hostClient))
                        ))
                .build();

        HttpResponse response = styxHttpClient.sendRequest(SOME_REQ).toBlocking().first();

        assertThat(response.status(), is(OK));
        verify(hostClient).sendRequest(eq(SOME_REQ));
    }


    @Test
    public void constructsRetryContextWhenLoadBalancerDoesNotFindAvailableOrigins() {
        RetryPolicy retryPolicy = mockRetryPolicy(true, true, true);

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendService)
                .metricsRegistry(metricRegistry)
                .loadBalancingStrategy(
                        mockLoadBalancer(
                                asList(),
                                asList(remoteHost(SOME_ORIGIN, mockPool(SOME_ORIGIN), mockHostClient(just(response(OK).build()))))))
                .retryPolicy(retryPolicy)
                .build();

        HttpResponse response = styxHttpClient.sendRequest(SOME_REQ).toBlocking().first();

        ArgumentCaptor<RetryPolicy.Context> retryContext = ArgumentCaptor.forClass(RetryPolicy.Context.class);
        ArgumentCaptor<LoadBalancingStrategy> lbStrategy= ArgumentCaptor.forClass(LoadBalancingStrategy.class);
        ArgumentCaptor<LoadBalancingStrategy.Context> lbContext = ArgumentCaptor.forClass(LoadBalancingStrategy.Context.class);

        verify(retryPolicy).evaluate(retryContext.capture(), lbStrategy.capture(), lbContext.capture());

        assertThat(retryContext.getValue().appId(), is(backendService.id()));
        assertThat(retryContext.getValue().currentRetryCount(), is(1));
        assertThat(retryContext.getValue().lastException(), is(Optional.empty()));

        assertThat(lbStrategy.getValue(), notNullValue());

        assertThat(lbContext.getValue().appId(), is(backendService.id()));
        assertThat(lbContext.getValue().oneMinuteRateForStatusCode5xx(SOME_ORIGIN), is(0.0));
        assertThat(lbContext.getValue().currentRequest(), is(SOME_REQ));

        assertThat(response.status(), is(OK));
    }

    @Test
    public void retriesWhenRetryPolicyTellsToRetry() {
        RetryPolicy retryPolicy = mockRetryPolicy(true, false);

        StyxHostHttpClient firstClient = mockHostClient(Observable.error(
                new OriginUnreachableException(ORIGIN_1, new RuntimeException("An error occurred"))));

        StyxHostHttpClient secondClient = mockHostClient(just(response(OK).build()));


        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendService)
                .metricsRegistry(metricRegistry)
                .loadBalancingStrategy(
                        mockLoadBalancer(
                                asList(remoteHost(ORIGIN_1, mockPool(ORIGIN_1), firstClient)),
                                asList(remoteHost(ORIGIN_2, mockPool(ORIGIN_2), secondClient))
                        ))
                .retryPolicy(
                        retryPolicy)
                .build();

        HttpResponse response = styxHttpClient.sendRequest(SOME_REQ).toBlocking().first();

        ArgumentCaptor<RetryPolicy.Context> retryContext = ArgumentCaptor.forClass(RetryPolicy.Context.class);
        ArgumentCaptor<LoadBalancingStrategy> lbStrategy= ArgumentCaptor.forClass(LoadBalancingStrategy.class);
        ArgumentCaptor<LoadBalancingStrategy.Context> lbContext = ArgumentCaptor.forClass(LoadBalancingStrategy.Context.class);

        verify(retryPolicy).evaluate(retryContext.capture(), lbStrategy.capture(), lbContext.capture());

        assertThat(retryContext.getValue().appId(), is(backendService.id()));
        assertThat(retryContext.getValue().currentRetryCount(), is(1));
        assertThat(retryContext.getValue().lastException().get(), Matchers.instanceOf(OriginUnreachableException.class));

        assertThat(lbStrategy.getValue(), notNullValue());

        assertThat(lbContext.getValue().appId(), is(backendService.id()));
        assertThat(lbContext.getValue().oneMinuteRateForStatusCode5xx(SOME_ORIGIN), is(0.0));
        assertThat(lbContext.getValue().currentRequest(), is(SOME_REQ));

        assertThat(response.status(), is(OK));

        InOrder ordered = inOrder(firstClient, secondClient);
        ordered.verify(firstClient).sendRequest(eq(SOME_REQ));
        ordered.verify(secondClient).sendRequest(eq(SOME_REQ));
    }

    @Test
    public void stopsRetriesWhenRetryPolicyTellsToStop() {
        StyxHostHttpClient firstClient = mockHostClient(Observable.error(new OriginUnreachableException(ORIGIN_1, new RuntimeException("An error occurred"))));
        StyxHostHttpClient secondClient = mockHostClient(Observable.error(new OriginUnreachableException(ORIGIN_2, new RuntimeException("An error occurred"))));
        StyxHostHttpClient thirdClient = mockHostClient(Observable.error(new OriginUnreachableException(ORIGIN_2, new RuntimeException("An error occurred"))));

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendService)
                .metricsRegistry(metricRegistry)
                .loadBalancingStrategy(
                        mockLoadBalancer(
                                asList(remoteHost(ORIGIN_1, mockPool(ORIGIN_1), firstClient)),
                                asList(remoteHost(ORIGIN_2, mockPool(ORIGIN_2), secondClient)),
                                asList(remoteHost(ORIGIN_3, mockPool(ORIGIN_3), thirdClient))
                        ))
                .retryPolicy(mockRetryPolicy(true, false))
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
        StyxHostHttpClient firstClient = mockHostClient(Observable.error(new OriginUnreachableException(ORIGIN_1, new RuntimeException("An error occurred"))));
        StyxHostHttpClient secondClient = mockHostClient(Observable.error(new OriginUnreachableException(ORIGIN_2, new RuntimeException("An error occurred"))));
        StyxHostHttpClient thirdClient = mockHostClient(Observable.error(new OriginUnreachableException(ORIGIN_3, new RuntimeException("An error occurred"))));
        StyxHostHttpClient fourthClient = mockHostClient(Observable.error(new OriginUnreachableException(ORIGIN_4, new RuntimeException("An error occurred"))));

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendService)
                .metricsRegistry(metricRegistry)
                .loadBalancingStrategy(
                        mockLoadBalancer(
                                asList(remoteHost(ORIGIN_1, mockPool(ORIGIN_1), firstClient)),
                                asList(remoteHost(ORIGIN_2, mockPool(ORIGIN_2), secondClient)),
                                asList(remoteHost(ORIGIN_3, mockPool(ORIGIN_3), thirdClient)),
                                asList(remoteHost(ORIGIN_4, mockPool(ORIGIN_4), fourthClient))
                        ))
                .retryPolicy(
                        mockRetryPolicy(true, true, true, true))
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


    @Test
    public void incrementsResponseStatusMetricsForBadResponse() {
        StyxHostHttpClient hostClient = mockHostClient(just(response(BAD_REQUEST).build()));

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendService)
                .metricsRegistry(metricRegistry)
                .loadBalancingStrategy(
                        mockLoadBalancer(asList(remoteHost(SOME_ORIGIN, mock(ConnectionPool.class), hostClient))))
                .build();

        HttpResponse response = styxHttpClient.sendRequest(SOME_REQ).toBlocking().first();

        assertThat(response.status(), is(BAD_REQUEST));
        verify(hostClient).sendRequest(eq(SOME_REQ));
        assertThat(metricRegistry.counter("origins.response.status.400").getCount(), is(1L));
    }

    @Test
    public void incrementsResponseStatusMetricsFor401() {
        StyxHostHttpClient hostClient = mockHostClient(just(response(UNAUTHORIZED).build()));

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendService)
                .metricsRegistry(metricRegistry)
                .loadBalancingStrategy(
                        mockLoadBalancer(asList(remoteHost(SOME_ORIGIN, mock(ConnectionPool.class), hostClient)))
                )
                .build();

        HttpResponse response = styxHttpClient.sendRequest(SOME_REQ).toBlocking().first();

        assertThat(response.status(), is(UNAUTHORIZED));
        verify(hostClient).sendRequest(eq(SOME_REQ));
        assertThat(metricRegistry.counter("origins.response.status.401").getCount(), is(1L));
    }

    @Test
    public void incrementsResponseStatusMetricsFor500() {
        StyxHostHttpClient hostClient = mockHostClient(just(response(INTERNAL_SERVER_ERROR).build()));

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendService)
                .metricsRegistry(metricRegistry)
                .loadBalancingStrategy(
                        mockLoadBalancer(asList(remoteHost(SOME_ORIGIN, mock(ConnectionPool.class), hostClient)))
                )
                .build();

        HttpResponse response = styxHttpClient.sendRequest(SOME_REQ).toBlocking().first();

        assertThat(response.status(), is(INTERNAL_SERVER_ERROR));
        verify(hostClient).sendRequest(eq(SOME_REQ));
        assertThat(metricRegistry.counter("origins.response.status.500").getCount(), is(1L));
    }

    @Test
    public void incrementsResponseStatusMetricsFor501() {
        StyxHostHttpClient hostClient = mockHostClient(just(response(NOT_IMPLEMENTED).build()));

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendService)
                .metricsRegistry(metricRegistry)
                .loadBalancingStrategy(
                        mockLoadBalancer(asList(remoteHost(SOME_ORIGIN, mock(ConnectionPool.class), hostClient)))
                )
                .build();

        HttpResponse response = styxHttpClient.sendRequest(SOME_REQ).toBlocking().first();
        assertThat(response.status(), is(NOT_IMPLEMENTED));
        verify(hostClient).sendRequest(SOME_REQ);
        assertThat(metricRegistry.counter("origins.response.status.501").getCount(), is(1L));
    }

    @Test
    public void removesBadContentLength() {
        StyxHostHttpClient hostClient = mockHostClient(
                just(response(OK)
                        .addHeader(CONTENT_LENGTH, 50)
                        .addHeader(TRANSFER_ENCODING, CHUNKED)
                        .build()));

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendService)
                .metricsRegistry(metricRegistry)
                .loadBalancingStrategy(
                        mockLoadBalancer(asList(remoteHost(SOME_ORIGIN, mock(ConnectionPool.class), hostClient)))
                )
                .enableContentValidation()
                .build();

        HttpResponse response = styxHttpClient.sendRequest(SOME_REQ).toBlocking().first();

        assertThat(response.status(), is(OK));

        assertThat(response.contentLength().isPresent(), is(false));
        assertThat(response.header(TRANSFER_ENCODING).get(), is("chunked"));
    }

    @Test
    public void updatesCountersWhenTransactionIsCancelled() {
        Origin origin = originWithId("localhost:234", "App-X", "Origin-Y");
        PublishSubject<HttpResponse> responseSubject = PublishSubject.create();

        StyxHttpClient styxHttpClient = new StyxHttpClient.Builder(backendWithOrigins(origin.host().getPort()))
                .loadBalancingStrategy(
                        mockLoadBalancer(asList(remoteHost(origin, mock(ConnectionPool.class), mockHostClient(responseSubject))))
                )
                .metricsRegistry(metricRegistry)
                .build();

        Observable<HttpResponse> transaction = styxHttpClient.sendRequest(SOME_REQ);
        Subscription subscription = transaction.subscribe();
        responseSubject.onNext(response(OK).build());

        subscription.unsubscribe();

        assertThat(metricRegistry.counter("origins.App-X.requests.cancelled").getCount(), is(1L));
        assertThat(metricRegistry.counter("origins.App-X.Origin-Y.requests.cancelled").getCount(), is(1L));
    }

    private RetryPolicy mockRetryPolicy(Boolean first, Boolean... outcomes) {
        RetryPolicy retryPolicy = mock(RetryPolicy.class);
        RetryPolicy.Outcome retryOutcome = mock(RetryPolicy.Outcome.class);

        when(retryOutcome.shouldRetry()).thenReturn(first, outcomes);

        RetryPolicy.Outcome[] retryOutcomes = stream(outcomes).map(outcome -> retryOutcome).toArray(RetryPolicy.Outcome[]::new);

        when(retryPolicy.evaluate(any(RetryPolicy.Context.class), any(LoadBalancingStrategy.class), any(LoadBalancingStrategy.Context.class)))
                .thenReturn(retryOutcome)
                .thenReturn(retryOutcome, retryOutcomes);

        return retryPolicy;
    }

    private LoadBalancingStrategy mockLoadBalancer(List<RemoteHost> first) {
        LoadBalancingStrategy lbStategy = mock(LoadBalancingStrategy.class);
        when(lbStategy.vote(any(LoadBalancingStrategy.Context.class))).thenReturn(first, emptyList());
        return lbStategy;
    }

    private LoadBalancingStrategy mockLoadBalancer(List<RemoteHost> first, List<RemoteHost>... remoteHostWrappers) {
        LoadBalancingStrategy lbStategy = mock(LoadBalancingStrategy.class);
        when(lbStategy.vote(any(LoadBalancingStrategy.Context.class))).thenReturn(first, remoteHostWrappers);
        return lbStategy;
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

    private static BackendService backendWithOrigins(int originPort) {
        return backendBuilderWithOrigins(originPort).build();
    }

    private static BackendService.Builder backendBuilderWithOrigins(int originPort) {
        return new BackendService.Builder()
                .origins(newOriginBuilder("localhost", originPort).build());
    }

    private static Origin originWithId(String host, String appId, String originId) {
        return newOriginBuilder(HostAndPort.fromString(host))
                .applicationId(appId)
                .id(originId)
                .build();
    }

}
