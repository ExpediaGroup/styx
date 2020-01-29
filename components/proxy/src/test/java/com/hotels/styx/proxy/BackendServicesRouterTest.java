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
package com.hotels.styx.proxy;

import com.hotels.styx.Environment;
import com.hotels.styx.NettyExecutor;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.Registry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.client.BackendServiceClient;
import com.hotels.styx.client.OriginStatsFactory;
import com.hotels.styx.client.OriginsInventory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static com.hotels.styx.support.Support.requestContext;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static com.hotels.styx.api.LiveHttpResponse.response;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.extension.service.BackendService.newBackendServiceBuilder;
import static com.hotels.styx.client.StyxHeaderConfig.ORIGIN_ID_DEFAULT;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BackendServicesRouterTest {
    private static final String APP_A = "appA";
    private static final String APP_B = "appB";

    private final BackendServiceClientFactory serviceClientFactory =
            (backendService, originsInventory, originStatsFactory) -> (request, context) -> responseWithOriginIdHeader(backendService);
    private HttpInterceptor.Context context = requestContext();

    private Environment environment;

    private NettyExecutor executor = NettyExecutor.create("x", 1);

    @AfterAll
    public void tearDown() {
        executor.shut();
    }

    @BeforeEach
    public void before() {
        environment = new Environment.Builder().build();
    }

    @Test
    public void registersAllRoutes() {
        Registry.Changes<BackendService> changes = added(
                appA().newCopy().path("/headers").build(),
                appB().newCopy().path("/badheaders").build(),
                appB().newCopy().id("appB-03").path("/cookies").build());

        BackendServicesRouter router = new BackendServicesRouter(serviceClientFactory, environment, executor);
        router.onChange(changes);

        assertThat(router.routes().keySet(), contains("/badheaders", "/cookies", "/headers"));
    }

    @Test
    public void selectsServiceBasedOnPath() throws Exception {
        Registry.Changes<BackendService> changes = added(
                appA().newCopy().path("/").build(),
                appB().newCopy().path("/appB/hotel/details.html").build());

        BackendServicesRouter router = new BackendServicesRouter(serviceClientFactory, environment, executor);
        router.onChange(changes);

        LiveHttpRequest request = get("/appB/hotel/details.html").build();
        Optional<HttpHandler> route = router.route(request, context);

        assertThat(proxyTo(route, request).header(ORIGIN_ID_DEFAULT), isValue(APP_B));
    }

    @Test
    public void selectsApplicationBasedOnPathIfAppsAreProvidedInOppositeOrder() throws Exception {
        Registry.Changes<BackendService> changes = added(
                appB().newCopy().path("/appB/hotel/details.html").build(),
                appA().newCopy().path("/").build());

        BackendServicesRouter router = new BackendServicesRouter(serviceClientFactory, environment, executor);
        router.onChange(changes);

        LiveHttpRequest request = get("/appB/hotel/details.html").build();
        Optional<HttpHandler> route = router.route(request, context);

        assertThat(proxyTo(route, request).header(ORIGIN_ID_DEFAULT), isValue(APP_B));
    }


    @Test
    public void selectsUsingSingleSlashPath() throws Exception {
        Registry.Changes<BackendService> changes = added(
                appA().newCopy().path("/").build(),
                appB().newCopy().path("/appB/hotel/details.html").build());

        BackendServicesRouter router = new BackendServicesRouter(serviceClientFactory, environment, executor);
        router.onChange(changes);

        LiveHttpRequest request = get("/").build();
        Optional<HttpHandler> route = router.route(request, context);

        assertThat(proxyTo(route, request).header(ORIGIN_ID_DEFAULT), isValue(APP_A));
    }

    @Test
    public void selectsUsingSingleSlashPathIfAppsAreProvidedInOppositeOrder() throws Exception {
        Registry.Changes<BackendService> changes = added(
                appB().newCopy().path("/appB/hotel/details.html").build(),
                appA().newCopy().path("/").build());

        BackendServicesRouter router = new BackendServicesRouter(serviceClientFactory, environment, executor);
        router.onChange(changes);

        LiveHttpRequest request = get("/").build();
        Optional<HttpHandler> route = router.route(request, context);

        assertThat(proxyTo(route, request).header(ORIGIN_ID_DEFAULT), isValue(APP_A));
    }

    @Test
    public void selectsUsingPathWithNoSubsequentCharacters() throws Exception {
        Registry.Changes<BackendService> changes = added(
                appA().newCopy().path("/").build(),
                appB().newCopy().path("/appB/").build());

        BackendServicesRouter router = new BackendServicesRouter(serviceClientFactory, environment, executor);
        router.onChange(changes);

        LiveHttpRequest request = get("/appB/").build();
        Optional<HttpHandler> route = router.route(request, context);

        assertThat(proxyTo(route, request).header(ORIGIN_ID_DEFAULT), isValue(APP_B));
    }

    @Test
    public void doesNotMatchRequestIfFinalSlashIsMissing() {
        BackendServicesRouter router = new BackendServicesRouter(serviceClientFactory, environment, executor);
        router.onChange(added(appB().newCopy().path("/appB/hotel/details.html").build()));

        LiveHttpRequest request = get("/ba/").build();
        Optional<HttpHandler> route = router.route(request, context);

        assertThat(route, is(Optional.empty()));
    }

    @Test
    public void throwsExceptionWhenNoApplicationMatches() {
        BackendServicesRouter router = new BackendServicesRouter(serviceClientFactory, environment, executor);
        router.onChange(added(appB().newCopy().path("/appB/hotel/details.html").build()));

        LiveHttpRequest request = get("/qwertyuiop").build();
        assertThat(router.route(request, context), is(Optional.empty()));
    }

    @Test
    public void removesExistingServicesBeforeAddingNewOnes() throws Exception {
        BackendServicesRouter router = new BackendServicesRouter(serviceClientFactory, environment, executor);
        router.onChange(added(appB()));

        router.onChange(new Registry.Changes.Builder<BackendService>()
                .added(newBackendServiceBuilder(appB()).id("X").build())
                .removed(appB())
                .build());

        LiveHttpRequest request = get("/appB/").build();
        Optional<HttpHandler> route = router.route(request, context);
        assertThat(proxyTo(route, request).header(ORIGIN_ID_DEFAULT), isValue("X"));
    }

    @Test
    public void updatesRoutesOnBackendServicesChange() throws Exception {
        BackendServicesRouter router = new BackendServicesRouter(serviceClientFactory, environment, executor);

        LiveHttpRequest request = get("/appB/").build();

        router.onChange(added(appB()));

        Optional<HttpHandler> route = router.route(request, context);
        assertThat(proxyTo(route, request).header(ORIGIN_ID_DEFAULT), isValue(APP_B));

        router.onChange(new Registry.Changes.Builder<BackendService>().build());

        Optional<HttpHandler> route2 = router.route(request, context);
        assertThat(proxyTo(route2, request).header(ORIGIN_ID_DEFAULT), isValue(APP_B));
    }

    private LiveHttpResponse proxyTo(Optional<HttpHandler> pipeline, LiveHttpRequest request) {
        return Mono.from(pipeline.get().handle(request, context)).block();
    }

    @Test
    public void closesClientWhenBackendServicesAreUpdated() {
        BackendServiceClient firstClient = mock(BackendServiceClient.class);
        BackendServiceClient secondClient = mock(BackendServiceClient.class);

        BackendServiceClientFactory clientFactory = mock(BackendServiceClientFactory.class);
        when(clientFactory.createClient(any(BackendService.class), any(OriginsInventory.class), any(OriginStatsFactory.class)))
                .thenReturn(firstClient)
                .thenReturn(secondClient);

        BackendServicesRouter router = new BackendServicesRouter(clientFactory, environment, executor);

        BackendService bookingApp = appB();
        router.onChange(added(bookingApp));

        ArgumentCaptor<OriginsInventory> originsInventory = forClass(OriginsInventory.class);

        verify(clientFactory).createClient(eq(bookingApp), originsInventory.capture(), any(OriginStatsFactory.class));

        BackendService bookingAppMinusOneOrigin = bookingAppMinusOneOrigin();

        router.onChange(updated(bookingAppMinusOneOrigin));

        assertThat(originsInventory.getValue().closed(), is(true));
        verify(clientFactory).createClient(eq(bookingAppMinusOneOrigin), any(OriginsInventory.class), any(OriginStatsFactory.class));
    }

    @Test
    public void closesClientWhenBackendServicesAreRemoved() {
        BackendServiceClient firstClient = mock(BackendServiceClient.class);
        BackendServiceClient secondClient = mock(BackendServiceClient.class);

        ArgumentCaptor<OriginsInventory> originsInventory = forClass(OriginsInventory.class);
        BackendServiceClientFactory clientFactory = mock(BackendServiceClientFactory.class);
        when(clientFactory.createClient(any(BackendService.class), any(OriginsInventory.class), any(OriginStatsFactory.class)))
                .thenReturn(firstClient)
                .thenReturn(secondClient);

        BackendServicesRouter router = new BackendServicesRouter(clientFactory, environment, executor);

        BackendService bookingApp = appB();
        router.onChange(added(bookingApp));

        verify(clientFactory).createClient(eq(bookingApp), originsInventory.capture(), any(OriginStatsFactory.class));

        router.onChange(removed(bookingApp));

        assertThat(originsInventory.getValue().closed(), is(true));
    }

    // This test exists due to a real bug we had when reloading in prod
    @Test
    public void deregistersAndReregistersMetricsAppropriately() {
        CodaHaleMetricRegistry metrics = new CodaHaleMetricRegistry();

        Environment environment = new Environment.Builder()
                .metricRegistry(metrics)
                .build();
        BackendServicesRouter router = new BackendServicesRouter(
                new StyxBackendServiceClientFactory(environment), environment, executor);

        router.onChange(added(backendService(APP_B, "/appB/", 9094, "appB-01", 9095, "appB-02")));

        assertThat(metrics.getGauges().get("origins.appB.appB-01.status").getValue(), is(1));
        assertThat(metrics.getGauges().get("origins.appB.appB-02.status").getValue(), is(1));

        BackendService appMinusOneOrigin = backendService(APP_B, "/appB/", 9094, "appB-01");

        router.onChange(updated(appMinusOneOrigin));

        assertThat(metrics.getGauges().get("origins.appB.appB-01.status").getValue(), is(1));
        assertThat(metrics.getGauges().get("origins.appB.appB-02.status"), is(nullValue()));
    }

    private static Registry.Changes<BackendService> added(BackendService... backendServices) {
        return new Registry.Changes.Builder<BackendService>().added(backendServices).build();
    }

    private static Registry.Changes<BackendService> updated(BackendService... backendServices) {
        return new Registry.Changes.Builder<BackendService>().updated(backendServices).build();
    }

    private static Registry.Changes<BackendService> removed(BackendService... backendServices) {
        return new Registry.Changes.Builder<BackendService>().removed(backendServices).build();
    }

    private static BackendService appA() {
        return newBackendServiceBuilder()
                .id(APP_A)
                .path("/")
                .origins(newOriginBuilder("localhost", 9090).applicationId(APP_A).id("appA-01").build())
                .build();
    }

    private static BackendService appB() {
        return newBackendServiceBuilder()
                .id(APP_B)
                .path("/appB/")
                .origins(
                        newOriginBuilder("localhost", 9094).applicationId(APP_B).id("appB-01").build(),
                        newOriginBuilder("localhost", 9095).applicationId(APP_B).id("appB-02").build())
                .build();
    }

    private static BackendService backendService(String id, String path, int originPort1, String originId1, int originPort2, String originId2) {
        return newBackendServiceBuilder()
                .id(id)
                .path(path)
                .origins(
                        newOriginBuilder("localhost", originPort1).applicationId(id).id(originId1).build(),
                        newOriginBuilder("localhost", originPort2).applicationId(id).id(originId2).build())
                .build();
    }

    private static BackendService bookingAppMinusOneOrigin() {
        return newBackendServiceBuilder()
                .id(APP_B)
                .path("/appB/")
                .origins(newOriginBuilder("localhost", 9094).applicationId(APP_B).id("appB-01").build())
                .build();
    }

    private static BackendService backendService(String id, String path, int originPort, String originId) {
        return newBackendServiceBuilder()
                .id(id)
                .path(path)
                .origins(newOriginBuilder("localhost", originPort).applicationId(id).id(originId).build())
                .build();
    }

    private static Flux<LiveHttpResponse> responseWithOriginIdHeader(BackendService backendService) {
        return Flux.just(response(OK)
                .header(ORIGIN_ID_DEFAULT, backendService.id())
                .build());
    }
}