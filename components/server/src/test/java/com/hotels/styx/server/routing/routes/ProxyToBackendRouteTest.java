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
package com.hotels.styx.server.routing.routes;

import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.server.HttpInterceptorContext;
import org.testng.annotations.Test;
import rx.Observable;

import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.hotels.styx.api.HttpRequest;

public class ProxyToBackendRouteTest {
    @Test
    public void proxiesUsingClient() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.sendRequest(any(HttpRequest.class))).thenReturn(Observable.just(response(OK).build()));

        ProxyToBackendRoute proxy = ProxyToBackendRoute.proxyToBackend(client);

        HttpResponse response = proxy.handle(get("/foo").build(), HttpInterceptorContext.create()).asCompletableFuture().get();
        assertThat(response.status(), is(OK));
    }
}