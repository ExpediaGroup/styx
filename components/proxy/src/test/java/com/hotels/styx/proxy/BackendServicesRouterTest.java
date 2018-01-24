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
package com.hotels.styx.proxy;

import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.HttpHandler2;
import com.hotels.styx.api.HttpInterceptor.Context;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.Id;
import com.hotels.styx.client.OriginsInventory;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.infrastructure.Registry;
import com.hotels.styx.proxy.backends.CommonBackendServiceRegistry.StyxBackendService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observable;

import java.util.Optional;

import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.HttpResponse.Builder.response;
import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static com.hotels.styx.client.StyxHeaderConfig.ORIGIN_ID_DEFAULT;
import static com.hotels.styx.client.applications.BackendService.newBackendServiceBuilder;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static rx.Observable.just;

public class BackendServicesRouterTest {
    private static final String APP_A = "appA";
    private static final String APP_B = "appB";

    private HttpClient clientA;
    private HttpClient clientB;

    @BeforeMethod
    public void before() {
        clientA = mock(HttpClient.class);
        when(clientA.sendRequest(any(HttpRequest.class))).thenReturn(just(response(OK).header(ORIGIN_ID_DEFAULT, APP_A).build()));

        clientB = mock(HttpClient.class);
        when(clientB.sendRequest(any(HttpRequest.class))).thenReturn(just(response(OK).header(ORIGIN_ID_DEFAULT, APP_B).build()));
    }

    @Test
    public void registersAllRoutes() {
        Registry.Changes<StyxBackendService> changes = added(
                styxBackendService(id("appA"), "/headers", clientA),
                styxBackendService(id("appB"), "/badheaders", clientB),
                styxBackendService(id("appB"), "/cookies", clientB));

        BackendServicesRouter router = new BackendServicesRouter();
        router.onChange(changes);

        assertThat(router.routes().keySet(), contains("/badheaders", "/cookies", "/headers"));
    }

    @Test
    public void selectsServiceBasedOnPath() {
        Registry.Changes<StyxBackendService> changes = added(
                styxBackendService(id("appA"), "/", clientA),
                styxBackendService(id("appB"), "/appB/hotel/details.html", clientB)
        );

        BackendServicesRouter router = new BackendServicesRouter();
        router.onChange(changes);

        HttpRequest request = get("/appB/hotel/details.html").build();
        Optional<HttpHandler2> route = router.route(request);

        assertThat(proxyTo(route, request).header(ORIGIN_ID_DEFAULT), isValue(APP_B));
    }

    @Test
    public void selectsApplicationBasedOnPathIfAppsAreProvidedInOppositeOrder() {
        Registry.Changes<StyxBackendService> changes = added(
                styxBackendService(id("appB"), "/appB/hotel/details.html", clientB),
                styxBackendService(id("appA"), "/", clientA));

        BackendServicesRouter router = new BackendServicesRouter();
        router.onChange(changes);

        HttpRequest request = get("/appB/hotel/details.html").build();
        Optional<HttpHandler2> route = router.route(request);

        assertThat(proxyTo(route, request).header(ORIGIN_ID_DEFAULT), isValue(APP_B));
    }

    @Test
    public void selectsUsingSingleSlashPath() {
        Registry.Changes<StyxBackendService> changes = added(
                styxBackendService(id("appA"), "/", clientA),
                styxBackendService(id("appB"), "/appB/hotel/details.html", clientB));

        BackendServicesRouter router = new BackendServicesRouter();
        router.onChange(changes);

        HttpRequest request = get("/").build();
        Optional<HttpHandler2> route = router.route(request);

        assertThat(proxyTo(route, request).header(ORIGIN_ID_DEFAULT), isValue(APP_A));
    }

    @Test
    public void selectsUsingSingleSlashPathIfAppsAreProvidedInOppositeOrder() {
        Registry.Changes<StyxBackendService> changes = added(
                styxBackendService(id("appA"), "/appB/hotel/details.html", clientA),
                styxBackendService(id("appB"), "/", clientB));

        BackendServicesRouter router = new BackendServicesRouter();
        router.onChange(changes);

        HttpRequest request = get("/").build();
        Optional<HttpHandler2> route = router.route(request);

        assertThat(proxyTo(route, request).header(ORIGIN_ID_DEFAULT), isValue(APP_B));
    }

    @Test
    public void selectsUsingPathWithNoSubsequentCharacters() {
        Registry.Changes<StyxBackendService> changes = added(
                styxBackendService(id("appA"), "/", clientA),
                styxBackendService(id("appB"), "/appB/", clientB));

        BackendServicesRouter router = new BackendServicesRouter();
        router.onChange(changes);

        HttpRequest request = get("/appB/").build();
        Optional<HttpHandler2> route = router.route(request);

        assertThat(proxyTo(route, request).header(ORIGIN_ID_DEFAULT), isValue(APP_B));
    }

    @Test
    public void doesNotMatchRequestIfFinalSlashIsMissing() {
        BackendServicesRouter router = new BackendServicesRouter();
        router.onChange(added(styxBackendService(id("appA"), "/appB/hotel/details.html", clientA)));

        HttpRequest request = get("/ba/").build();
        Optional<HttpHandler2> route = router.route(request);
        System.out.println("route: " + route);

        assertThat(route, is(Optional.empty()));
    }

    @Test
    public void throwsExceptionWhenNoApplicationMatches() {
        BackendServicesRouter router = new BackendServicesRouter();
        router.onChange(added(styxBackendService(id("appA"), "/appB/hotel/details.html", clientA)));

        HttpRequest request = get("/qwertyuiop").build();
        assertThat(router.route(request), is(Optional.empty()));
    }

    @Test
    public void updatesRoutesOnBackendServicesChange() {
        BackendServicesRouter router = new BackendServicesRouter();

        HttpRequest request = get("/appB/").build();

        router.onChange(added(styxBackendService(id("appB"), "/", clientB)));

        Optional<HttpHandler2> route = router.route(request);
        assertThat(proxyTo(route, request).header(ORIGIN_ID_DEFAULT), isValue(APP_B));

        router.onChange(new Registry.Changes.Builder<StyxBackendService>().build());

        Optional<HttpHandler2> route2 = router.route(request);
        assertThat(proxyTo(route2, request).header(ORIGIN_ID_DEFAULT), isValue(APP_B));
    }


    // TODO: Mikko: Add tests for updated & removed backend services!

    private static StyxBackendService styxBackendService(Id id, String path, HttpClient client) {
        return new StyxBackendService(mock(OriginsInventory.class), client, newBackendServiceBuilder()
                .id(id)
                .path(path)
                .origins(newOriginBuilder("localhost", 9090).applicationId(id).id(id + "-01").build())
                .build());
    }

    private HttpResponse proxyTo(Optional<HttpHandler2> pipeline, HttpRequest request) {
        return pipeline.get().handle(request, mock(Context.class)).toBlocking().first();
    }

    private static Registry.Changes<StyxBackendService> added(StyxBackendService... backendServices) {
        return new Registry.Changes.Builder<StyxBackendService>().added(backendServices).build();
    }

    private static Registry.Changes<StyxBackendService> updated(StyxBackendService... backendServices) {
        return new Registry.Changes.Builder<StyxBackendService>().updated(backendServices).build();
    }

    private static Registry.Changes<StyxBackendService> removed(StyxBackendService... backendServices) {
        return new Registry.Changes.Builder<StyxBackendService>().removed(backendServices).build();
    }

    private static Observable<HttpResponse> responseWithOriginIdHeader(BackendService backendService) {
        return just(response(OK)
                .header(ORIGIN_ID_DEFAULT, backendService.id())
                .build());
    }
}