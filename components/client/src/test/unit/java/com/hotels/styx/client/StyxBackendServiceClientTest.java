/*
  Copyright (C) 2013-2022 Expedia Inc.

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

import com.google.common.net.HostAndPort;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpHeaderNames;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpResponseStatus;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.MeterRegistry;
import com.hotels.styx.api.MicrometerRegistry;
import com.hotels.styx.api.HttpInterceptor.Context;
import com.hotels.styx.api.exceptions.NoAvailableHostsException;
import com.hotels.styx.api.exceptions.OriginUnreachableException;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.RemoteHost;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer;
import com.hotels.styx.api.extension.retrypolicy.spi.RetryPolicy;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.StickySessionConfig;
import com.hotels.styx.client.retry.RetryNTimes;
import com.hotels.styx.metrics.CentralisedMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Optional;

import static com.hotels.styx.api.HttpHeaderNames.CHUNKED;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.TRANSFER_ENCODING;
import static com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST;
import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static com.hotels.styx.api.HttpResponseStatus.NOT_IMPLEMENTED;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.HttpResponseStatus.UNAUTHORIZED;
import static com.hotels.styx.api.Id.GENERIC_APP;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static com.hotels.styx.api.LiveHttpResponse.response;
import static com.hotels.styx.api.RequestCookie.requestCookie;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.extension.RemoteHost.remoteHost;
import static com.hotels.styx.api.extension.service.StickySessionConfig.stickySessionDisabled;
import static com.hotels.styx.support.Support.requestContext;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StyxBackendServiceClientTest {
    private static final Origin SOME_ORIGIN = newOriginBuilder("localhost", 9090).applicationId(GENERIC_APP).build();
    private static final LiveHttpRequest SOME_REQ = get("/some-req").build();

    private static final Origin ORIGIN_1 = newOriginBuilder("localhost", 9091).applicationId("app").id("app-01").build();
    private static final Origin ORIGIN_2 = newOriginBuilder("localhost", 9092).applicationId("app").id("app-02").build();
    private static final Origin ORIGIN_3 = newOriginBuilder("localhost", 9093).applicationId("app").id("app-03").build();
    private static final Origin ORIGIN_4 = newOriginBuilder("localhost", 9094).applicationId("app").id("app-04").build();

    private static final LiveHttpRequest testRequest = LiveHttpRequest.get("/test").header(HttpHeaderNames.HOST, "www.expedia.com").build();
    private static final LiveHttpResponse testResponse = LiveHttpResponse.response(HttpResponseStatus.OK).build();
    private static final String incomingHostname = "www.expedia.com";
    private static final String updatedHostName = "host.domain.com";

    private final StickySessionConfig stickySessionConfig = stickySessionDisabled();
    private MeterRegistry meterRegistry;
    private CentralisedMetrics metrics;
    private final BackendService backendService = backendBuilderWithOrigins(SOME_ORIGIN.port())
            .stickySessionConfig(stickySessionConfig)
            .build();

    @BeforeEach
    public void setUp() {
        meterRegistry = new MicrometerRegistry(new SimpleMeterRegistry());
        metrics = new CentralisedMetrics(meterRegistry);
    }

    @Test
    public void sendsRequestToHostChosenByLoadBalancer() {
        StyxHostHttpClient hostClient = mockHostClient(Flux.just(response(OK).build()));

        StyxBackendServiceClient styxHttpClient = new StyxBackendServiceClient.Builder(backendService.id())
                .metrics(metrics)
                .loadBalancer(
                        mockLoadBalancer(
                                Optional.of(remoteHost(SOME_ORIGIN, toHandler(hostClient), hostClient))
                        ))
                .build();

        LiveHttpResponse response = Mono.from(styxHttpClient.sendRequest(SOME_REQ, requestContext())).block();

        assertThat(response.status(), is(OK));
        verify(hostClient).sendRequest(eq(SOME_REQ), any(Context.class));
    }

    @Test
    public void constructsRetryContextWhenLoadBalancerDoesNotFindAvailableOrigins() {
        RetryPolicy retryPolicy = mockRetryPolicy(true, true, true);
        StyxHostHttpClient hostClient = mockHostClient(Flux.just(response(OK).build()));

        StyxBackendServiceClient styxHttpClient = new StyxBackendServiceClient.Builder(backendService.id())
                .metrics(metrics)
                .loadBalancer(
                        mockLoadBalancer(
                                Optional.empty(),
                                Optional.of(remoteHost(SOME_ORIGIN, toHandler(hostClient), hostClient))))
                .retryPolicy(retryPolicy)
                .build();

        LiveHttpResponse response = Mono.from(styxHttpClient.sendRequest(SOME_REQ, requestContext())).block();

        ArgumentCaptor<RetryPolicy.Context> retryContext = ArgumentCaptor.forClass(RetryPolicy.Context.class);
        ArgumentCaptor<LoadBalancer> lbPreference = ArgumentCaptor.forClass(LoadBalancer.class);
        ArgumentCaptor<LoadBalancer.Preferences> lbContext = ArgumentCaptor.forClass(LoadBalancer.Preferences.class);

        verify(retryPolicy).evaluate(retryContext.capture(), lbPreference.capture(), lbContext.capture());

        assertThat(retryContext.getValue().appId(), is(backendService.id()));
        assertThat(retryContext.getValue().currentRetryCount(), is(1));
        assertThat(retryContext.getValue().lastException(), is(Optional.empty()));

        assertThat(lbPreference.getValue(), notNullValue());

        assertThat(lbContext.getValue().avoidOrigins(), is(empty()));
        assertThat(lbContext.getValue().preferredOrigins(), is(Optional.empty()));

        assertThat(response.status(), is(OK));
    }

    @Test
    public void retriesWhenRetryPolicyTellsToRetry() {
        RetryPolicy retryPolicy = mockRetryPolicy(true, false);

        StyxHostHttpClient firstClient = mockHostClient(Flux.error(
                new OriginUnreachableException(ORIGIN_1, new RuntimeException("An error occurred"))));

        StyxHostHttpClient secondClient = mockHostClient(Flux.just(response(OK).build()));

        StyxBackendServiceClient styxHttpClient = new StyxBackendServiceClient.Builder(backendService.id())
                .metrics(metrics)
                .loadBalancer(
                        mockLoadBalancer(
                                Optional.of(remoteHost(ORIGIN_1, toHandler(firstClient), firstClient)),
                                Optional.of(remoteHost(ORIGIN_2, toHandler(secondClient), secondClient))
                        ))
                .retryPolicy(retryPolicy)
                .build();

        LiveHttpResponse response = Mono.from(styxHttpClient.sendRequest(SOME_REQ, requestContext())).block();

        ArgumentCaptor<RetryPolicy.Context> retryContext = ArgumentCaptor.forClass(RetryPolicy.Context.class);
        ArgumentCaptor<LoadBalancer> lbPreference = ArgumentCaptor.forClass(LoadBalancer.class);
        ArgumentCaptor<LoadBalancer.Preferences> lbContext = ArgumentCaptor.forClass(LoadBalancer.Preferences.class);

        verify(retryPolicy).evaluate(retryContext.capture(), lbPreference.capture(), lbContext.capture());

        assertThat(retryContext.getValue().appId(), is(backendService.id()));
        assertThat(retryContext.getValue().currentRetryCount(), is(1));
        assertThat(retryContext.getValue().lastException().get(), Matchers.instanceOf(OriginUnreachableException.class));

        assertThat(lbPreference.getValue(), notNullValue());

        assertThat(lbContext.getValue().preferredOrigins(), is(Optional.empty()));
        assertThat(lbContext.getValue().avoidOrigins(), is(asList(ORIGIN_1)));

        assertThat(response.status(), is(OK));

        InOrder ordered = inOrder(firstClient, secondClient);
        ordered.verify(firstClient).sendRequest(eq(SOME_REQ), any(Context.class));
        ordered.verify(secondClient).sendRequest(eq(SOME_REQ), any(Context.class));
    }

    @Test
    public void stopsRetriesWhenRetryPolicyTellsToStop() {
        StyxHostHttpClient firstClient = mockHostClient(Flux.error(new OriginUnreachableException(ORIGIN_1, new RuntimeException("An error occurred"))));
        StyxHostHttpClient secondClient = mockHostClient(Flux.error(new OriginUnreachableException(ORIGIN_2, new RuntimeException("An error occurred"))));
        StyxHostHttpClient thirdClient = mockHostClient(Flux.error(new OriginUnreachableException(ORIGIN_2, new RuntimeException("An error occurred"))));

        StyxBackendServiceClient styxHttpClient = new StyxBackendServiceClient.Builder(backendService.id())
                .metrics(metrics)
                .loadBalancer(
                        mockLoadBalancer(
                                Optional.of(remoteHost(ORIGIN_1, toHandler(firstClient), firstClient)),
                                Optional.of(remoteHost(ORIGIN_2, toHandler(secondClient), secondClient)),
                                Optional.of(remoteHost(ORIGIN_3, toHandler(thirdClient), thirdClient))
                        ))
                .retryPolicy(mockRetryPolicy(true, false))
                .build();

        StepVerifier.create(styxHttpClient.sendRequest(SOME_REQ, requestContext()))
                .verifyError(OriginUnreachableException.class);

        InOrder ordered = inOrder(firstClient, secondClient, thirdClient);
        ordered.verify(firstClient).sendRequest(eq(SOME_REQ), any(Context.class));
        ordered.verify(secondClient).sendRequest(eq(SOME_REQ), any(Context.class));
        ordered.verify(thirdClient, never()).sendRequest(eq(SOME_REQ), any(Context.class));
    }

    @Test
    public void retriesAtMost3Times() {
        StyxHostHttpClient firstClient = mockHostClient(Flux.error(new OriginUnreachableException(ORIGIN_1, new RuntimeException("An error occurred"))));
        StyxHostHttpClient secondClient = mockHostClient(Flux.error(new OriginUnreachableException(ORIGIN_2, new RuntimeException("An error occurred"))));
        StyxHostHttpClient thirdClient = mockHostClient(Flux.error(new OriginUnreachableException(ORIGIN_3, new RuntimeException("An error occurred"))));
        StyxHostHttpClient fourthClient = mockHostClient(Flux.error(new OriginUnreachableException(ORIGIN_4, new RuntimeException("An error occurred"))));

        StyxBackendServiceClient styxHttpClient = new StyxBackendServiceClient.Builder(backendService.id())
                .metrics(metrics)
                .loadBalancer(
                        mockLoadBalancer(
                                Optional.of(remoteHost(ORIGIN_1, toHandler(firstClient), firstClient)),
                                Optional.of(remoteHost(ORIGIN_2, toHandler(secondClient), secondClient)),
                                Optional.of(remoteHost(ORIGIN_3, toHandler(thirdClient), thirdClient)),
                                Optional.of(remoteHost(ORIGIN_4, toHandler(fourthClient), fourthClient))
                        ))
                .retryPolicy(
                        mockRetryPolicy(true, true, true, true))
                .build();

        StepVerifier.create(styxHttpClient.sendRequest(SOME_REQ, requestContext()))
                .verifyError(NoAvailableHostsException.class);

        InOrder ordered = inOrder(firstClient, secondClient, thirdClient, fourthClient);
        ordered.verify(firstClient).sendRequest(eq(SOME_REQ), any(Context.class));
        ordered.verify(secondClient).sendRequest(eq(SOME_REQ), any(Context.class));
        ordered.verify(thirdClient).sendRequest(eq(SOME_REQ), any(Context.class));
        ordered.verify(fourthClient, never()).sendRequest(any(LiveHttpRequest.class), any(Context.class));
    }


    @Test
    public void incrementsResponseStatusMetricsForBadResponse() {
        StyxHostHttpClient hostClient = mockHostClient(Flux.just(response(BAD_REQUEST).build()));

        StyxBackendServiceClient styxHttpClient = new StyxBackendServiceClient.Builder(backendService.id())
                .metrics(metrics)
                .loadBalancer(
                        mockLoadBalancer(Optional.of(remoteHost(SOME_ORIGIN, toHandler(hostClient), hostClient))))
                .build();

        LiveHttpResponse response = Mono.from(styxHttpClient.sendRequest(SOME_REQ, requestContext())).block();

        assertThat(response.status(), is(BAD_REQUEST));
        verify(hostClient).sendRequest(eq(SOME_REQ), any(Context.class));
        assertThat(meterRegistry.get("proxy.client.responseCode.errorStatus").tag("statusCode", "400").counter(), is(notNullValue()));
    }

    @Test
    public void incrementsResponseStatusMetricsFor401() {
        StyxHostHttpClient hostClient = mockHostClient(Flux.just(response(UNAUTHORIZED).build()));

        StyxBackendServiceClient styxHttpClient = new StyxBackendServiceClient.Builder(backendService.id())
                .metrics(metrics)
                .loadBalancer(
                        mockLoadBalancer(Optional.of(remoteHost(SOME_ORIGIN, toHandler(hostClient), hostClient)))
                )
                .build();

        LiveHttpResponse response = Mono.from(styxHttpClient.sendRequest(SOME_REQ, requestContext())).block();

        assertThat(response.status(), is(UNAUTHORIZED));
        verify(hostClient).sendRequest(eq(SOME_REQ), any(Context.class));
        assertThat(meterRegistry.get("proxy.client.responseCode.errorStatus").tag("statusCode", "401").counter(), is(notNullValue()));
    }

    @Test
    public void incrementsResponseStatusMetricsFor500() {
        StyxHostHttpClient hostClient = mockHostClient(Flux.just(response(INTERNAL_SERVER_ERROR).build()));

        StyxBackendServiceClient styxHttpClient = new StyxBackendServiceClient.Builder(backendService.id())
                .metrics(metrics)
                .loadBalancer(
                        mockLoadBalancer(Optional.of(remoteHost(SOME_ORIGIN, toHandler(hostClient), hostClient)))
                )
                .build();

        LiveHttpResponse response = Mono.from(styxHttpClient.sendRequest(SOME_REQ, requestContext())).block();

        assertThat(response.status(), is(INTERNAL_SERVER_ERROR));
        verify(hostClient).sendRequest(eq(SOME_REQ), any(Context.class));
        assertThat(meterRegistry.get("proxy.client.responseCode.errorStatus").tag("statusCode", "500").counter(), is(notNullValue()));
    }

    @Test
    public void incrementsResponseStatusMetricsFor501() {
        StyxHostHttpClient hostClient = mockHostClient(Flux.just(response(NOT_IMPLEMENTED).build()));

        StyxBackendServiceClient styxHttpClient = new StyxBackendServiceClient.Builder(backendService.id())
                .metrics(metrics)
                .loadBalancer(
                        mockLoadBalancer(Optional.of(remoteHost(SOME_ORIGIN, toHandler(hostClient), hostClient)))
                )
                .build();

        LiveHttpResponse response = Mono.from(styxHttpClient.sendRequest(SOME_REQ, requestContext())).block();
        assertThat(response.status(), is(NOT_IMPLEMENTED));
        verify(hostClient).sendRequest(eq(SOME_REQ), any(Context.class));
        assertThat(meterRegistry.get("proxy.client.responseCode.errorStatus").tag("statusCode", "501").counter(), is(notNullValue()));
    }

    @Test
    public void removesBadContentLength() {
        StyxHostHttpClient hostClient = mockHostClient(
                Flux.just(response(OK)
                        .addHeader(CONTENT_LENGTH, 50)
                        .addHeader(TRANSFER_ENCODING, CHUNKED)
                        .build()));

        StyxBackendServiceClient styxHttpClient = new StyxBackendServiceClient.Builder(backendService.id())
                .metrics(metrics)
                .loadBalancer(
                        mockLoadBalancer(Optional.of(remoteHost(SOME_ORIGIN, toHandler(hostClient), hostClient)))
                )
                .build();

        LiveHttpResponse response = Mono.from(styxHttpClient.sendRequest(SOME_REQ, requestContext())).block();

        assertThat(response.status(), is(OK));

        assertThat(response.contentLength().isPresent(), is(false));
        assertThat(response.header(TRANSFER_ENCODING).get(), is("chunked"));
    }

    @Test
    public void updatesCountersWhenTransactionIsCancelled() {
        Origin origin = originWithId("localhost:234", "App-X", "Origin-Y");
        Processor<LiveHttpResponse, LiveHttpResponse> processor = EmitterProcessor.create();

        StyxHostHttpClient hostClient = mockHostClient(processor);

        StyxBackendServiceClient styxHttpClient = new StyxBackendServiceClient.Builder(backendService.id())
                .loadBalancer(
                        mockLoadBalancer(Optional.of(remoteHost(origin, toHandler(hostClient), hostClient)))
                )
                .metrics(metrics)
                .build();

        StepVerifier.create(styxHttpClient.sendRequest(SOME_REQ, requestContext()))
                .thenCancel()
                .verify();

        assertThat(meterRegistry.get("proxy.client.requests.cancelled")
                .tags("appId", "App-X", "originId", "Origin-Y")
                .counter().count(), is(1.0));
    }

    @Test
    public void prefersStickyOrigins() {
        Origin origin = originWithId("localhost:234", "App-X", "Origin-Y");
        StyxHostHttpClient hostClient = mockHostClient(Flux.just(response(OK).build()));

        LoadBalancer loadBalancer = mockLoadBalancer(Optional.of(remoteHost(origin, toHandler(hostClient), hostClient)));

        StyxBackendServiceClient styxHttpClient = new StyxBackendServiceClient.Builder(backendService.id())
                .loadBalancer(loadBalancer)
                .metrics(metrics)
                .build();

        LiveHttpResponse response = Mono.from(styxHttpClient.sendRequest(
                        get("/foo")
                                .cookies(requestCookie("styx_origin_" + Id.GENERIC_APP, "Origin-Y"))
                                .build(),
                        requestContext()))
                .block();

        assertThat(response.status(), is(OK));

        ArgumentCaptor<LoadBalancer.Preferences> argCaptor = ArgumentCaptor.forClass(LoadBalancer.Preferences.class);
        verify(loadBalancer).choose(argCaptor.capture());
        assertThat(argCaptor.getValue().preferredOrigins(), is(Optional.of("Origin-Y")));
    }

    @Test
    public void prefersRestrictedOrigins() {
        Origin origin = originWithId("localhost:234", "App-X", "Origin-Y");
        StyxHostHttpClient hostClient = mockHostClient(Flux.just(response(OK).build()));

        LoadBalancer loadBalancer = mockLoadBalancer(Optional.of(remoteHost(origin, toHandler(hostClient), hostClient)));

        StyxBackendServiceClient styxHttpClient = new StyxBackendServiceClient.Builder(backendService.id())
                .loadBalancer(loadBalancer)
                .metrics(metrics)
                .originsRestrictionCookieName("restrictedOrigin")
                .build();

        LiveHttpResponse response = Mono.from(styxHttpClient.sendRequest(
                get("/foo")
                        .cookies(requestCookie("restrictedOrigin", "Origin-Y"))
                        .build(),
                requestContext())).block();

        assertThat(response.status(), is(OK));

        ArgumentCaptor<LoadBalancer.Preferences> argCaptor = ArgumentCaptor.forClass(LoadBalancer.Preferences.class);
        verify(loadBalancer).choose(argCaptor.capture());
        assertThat(argCaptor.getValue().preferredOrigins(), is(Optional.of("Origin-Y")));
    }

    @Test
    public void prefersRestrictedOriginsOverStickyOriginsWhenBothAreConfigured() {
        Origin origin = originWithId("localhost:234", "App-X", "Origin-Y");
        StyxHostHttpClient hostClient = mockHostClient(Flux.just(response(OK).build()));
        LoadBalancer loadBalancer = mockLoadBalancer(Optional.of(remoteHost(origin, toHandler(hostClient), hostClient)));

        StyxBackendServiceClient styxHttpClient = new StyxBackendServiceClient.Builder(backendService.id())
                .originsRestrictionCookieName("restrictedOrigin")
                .loadBalancer(loadBalancer)
                .metrics(metrics)
                .build();

        LiveHttpResponse response = Mono.from(styxHttpClient.sendRequest(
                        get("/foo")
                                .cookies(
                                        requestCookie("restrictedOrigin", "Origin-Y"),
                                        requestCookie("styx_origin_" + Id.GENERIC_APP, "Origin-X")
                                )
                                .build(),
                        requestContext()))
                .block();

        assertThat(response.status(), is(OK));

        ArgumentCaptor<LoadBalancer.Preferences> argPreferences = ArgumentCaptor.forClass(LoadBalancer.Preferences.class);
        verify(loadBalancer).choose(argPreferences.capture());
        assertThat(argPreferences.getValue().preferredOrigins(), is(Optional.of("Origin-Y")));
    }

    @Test
    public void hostHeaderIsNotOverwrittenWhenOverrideHostHeaderIsFalse() {
        HttpInterceptor.Context requestContext = requestContext();
        StyxHostHttpClient hostClient = mock(StyxHostHttpClient.class);
        HttpHandler httpHandler = mock(HttpHandler.class);
        Origin origin = newOriginBuilder(incomingHostname, 9090).applicationId(GENERIC_APP).build();
        RemoteHost remoteHost = remoteHost(origin, httpHandler, hostClient);
        LoadBalancer loadBalancer = mockLoadBalancer(Optional.of(remoteHost));

        when(httpHandler.handle(any(), any())).thenReturn(Eventual.of(testResponse));

        StyxBackendServiceClient styxHttpClient = new StyxBackendServiceClient.Builder(backendService.id())
                .originStatsFactory(mock(OriginStatsFactory.class))
                .originsRestrictionCookieName("someCookie")
                .originIdHeader("origin-id")
                .loadBalancer(loadBalancer)
                .retryPolicy(new RetryNTimes(0))
                .metrics(metrics)
                .overrideHostHeader(false)
                .build();

        styxHttpClient.sendRequest(testRequest, requestContext);
        verify(httpHandler).handle(testRequest, requestContext);
    }

    @Test
    public void hostHeaderIsOverwrittenWhenOverrideHostHeaderIsTrue() {
        HttpInterceptor.Context requestContext = requestContext();
        StyxHostHttpClient hostClient = mock(StyxHostHttpClient.class);
        HttpHandler httpHandler = mock(HttpHandler.class);
        Origin origin = newOriginBuilder(updatedHostName, 9090).applicationId(GENERIC_APP).build();
        RemoteHost remoteHost = remoteHost(origin, httpHandler, hostClient);
        LoadBalancer loadBalancer = mockLoadBalancer(Optional.of(remoteHost));

        when(httpHandler.handle(any(), any())).thenReturn(Eventual.of(testResponse));

        StyxBackendServiceClient styxHttpClient = new StyxBackendServiceClient.Builder(backendService.id())
                .originStatsFactory(mock(OriginStatsFactory.class))
                .originsRestrictionCookieName("someCookie")
                .originIdHeader("origin-id")
                .loadBalancer(loadBalancer)
                .retryPolicy(new RetryNTimes(0))
                .metrics(metrics)
                .overrideHostHeader(true)
                .build();

        styxHttpClient.sendRequest(testRequest, requestContext);
        ArgumentCaptor<LiveHttpRequest> updatedRequest = ArgumentCaptor.forClass(LiveHttpRequest.class);
        verify(httpHandler).handle(updatedRequest.capture(), eq(requestContext));

        assertThat(updatedRequest.getValue().header(HttpHeaderNames.HOST).get(), is(updatedHostName));
    }

    @Test
    public void originalRequestIsPresentInResponseWhenOverrideHostHeaderIsFalse() {
        HttpInterceptor.Context requestContext = requestContext();
        StyxHostHttpClient hostClient = mock(StyxHostHttpClient.class);
        HttpHandler httpHandler = mock(HttpHandler.class);
        Origin origin = newOriginBuilder(incomingHostname, 9090).applicationId(GENERIC_APP).build();
        RemoteHost remoteHost = remoteHost(origin, httpHandler, hostClient);
        LoadBalancer loadBalancer = mockLoadBalancer(Optional.of(remoteHost));

        when(httpHandler.handle(any(), any())).thenReturn(Eventual.of(testResponse));

        StyxBackendServiceClient styxHttpClient = new StyxBackendServiceClient.Builder(backendService.id())
                .originStatsFactory(mock(OriginStatsFactory.class))
                .originsRestrictionCookieName("someCookie")
                .originIdHeader("origin-id")
                .loadBalancer(loadBalancer)
                .retryPolicy(new RetryNTimes(0))
                .metrics(metrics)
                .overrideHostHeader(false)
                .build();

        Publisher<LiveHttpResponse> responsePublisher = styxHttpClient.sendRequest(testRequest, requestContext);
        StepVerifier.create(responsePublisher)
                .expectNextMatches(it ->
                        it.request().header(HttpHeaderNames.HOST).isPresent() && it.request().header(HttpHeaderNames.HOST).get().equals(incomingHostname)
                ).verifyComplete();
    }

    @Test
    public void updatedRequestWithUpdatedHostHeaderIsPresentInResponseWhenOverrideHostHeaderIsTrue() {
        HttpInterceptor.Context requestContext = requestContext();
        StyxHostHttpClient hostClient = mock(StyxHostHttpClient.class);
        HttpHandler httpHandler = mock(HttpHandler.class);
        Origin origin = newOriginBuilder(updatedHostName, 9090).applicationId(GENERIC_APP).build();
        RemoteHost remoteHost = remoteHost(origin, httpHandler, hostClient);
        LoadBalancer loadBalancer = mockLoadBalancer(Optional.of(remoteHost));

        when(httpHandler.handle(any(), any())).thenReturn(Eventual.of(testResponse));

        StyxBackendServiceClient styxHttpClient = new StyxBackendServiceClient.Builder(backendService.id())
                .originStatsFactory(mock(OriginStatsFactory.class))
                .originsRestrictionCookieName("someCookie")
                .originIdHeader("origin-id")
                .loadBalancer(loadBalancer)
                .retryPolicy(new RetryNTimes(0))
                .metrics(metrics)
                .overrideHostHeader(true)
                .build();

        Publisher<LiveHttpResponse> responsePublisher = styxHttpClient.sendRequest(testRequest, requestContext);
        StepVerifier.create(responsePublisher)
                .expectNextMatches(it ->
                        it.request().header(HttpHeaderNames.HOST).isPresent() && it.request().header(HttpHeaderNames.HOST).get().equals(updatedHostName)
                ).verifyComplete();
    }

    private HttpHandler toHandler(StyxHostHttpClient hostClient) {
        return (request, ctx) -> new Eventual<>(hostClient.sendRequest(request, ctx));
    }

    private RetryPolicy mockRetryPolicy(Boolean first, Boolean... outcomes) {
        RetryPolicy retryPolicy = mock(RetryPolicy.class);
        RetryPolicy.Outcome retryOutcome = mock(RetryPolicy.Outcome.class);

        when(retryOutcome.shouldRetry()).thenReturn(first, outcomes);

        RetryPolicy.Outcome[] retryOutcomes = stream(outcomes).map(outcome -> retryOutcome).toArray(RetryPolicy.Outcome[]::new);

        when(retryPolicy.evaluate(any(RetryPolicy.Context.class), any(LoadBalancer.class), any(LoadBalancer.Preferences.class)))
                .thenReturn(retryOutcome)
                .thenReturn(retryOutcome, retryOutcomes);

        return retryPolicy;
    }

    private LoadBalancer mockLoadBalancer(Optional<RemoteHost> first) {
        LoadBalancer lbStategy = mock(LoadBalancer.class);
        when(lbStategy.choose(any(LoadBalancer.Preferences.class))).thenReturn(first);
        return lbStategy;
    }

    private LoadBalancer mockLoadBalancer(Optional<RemoteHost> first, Optional<RemoteHost>... remoteHostWrappers) {
        LoadBalancer lbStategy = mock(LoadBalancer.class);

        when(lbStategy.choose(any(LoadBalancer.Preferences.class))).thenReturn(first, remoteHostWrappers);
        return lbStategy;
    }

    private StyxHostHttpClient mockHostClient(Publisher<LiveHttpResponse> responsePublisher) {
        StyxHostHttpClient secondClient = mock(StyxHostHttpClient.class);
        when(secondClient.sendRequest(any(LiveHttpRequest.class), any(Context.class))).thenReturn(responsePublisher);
        return secondClient;
    }

    private static BackendService.Builder backendBuilderWithOrigins(int originPort) {
        return new BackendService.Builder()
                .origins(newOriginBuilder("localhost", originPort).build());
    }

    private static Origin originWithId(String host, String appId, String originId) {
        HostAndPort hap = HostAndPort.fromString(host);
        return newOriginBuilder(hap.getHost(), hap.getPort())
                .applicationId(appId)
                .id(originId)
                .build();
    }

}
