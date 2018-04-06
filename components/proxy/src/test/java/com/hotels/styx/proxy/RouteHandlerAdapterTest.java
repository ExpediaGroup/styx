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
package com.hotels.styx.proxy;

import com.hotels.styx.api.HttpHandler2;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.server.HttpInterceptorContext;
import com.hotels.styx.server.HttpRouter;
import org.testng.annotations.Test;

import java.util.Optional;

import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.HttpResponse.Builder.response;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static rx.Observable.just;

public class RouteHandlerAdapterTest {

    private HttpRequest request = get("/").build();
    private HttpResponse respOk = response(OK).build();

    @Test
    public void injectsToPipelineWhenRouteFound() throws Exception {
        HttpHandler2 pipeline = mock(HttpHandler2.class);
        when(pipeline.handle(any(HttpRequest.class), any(HttpInterceptor.Context.class))).thenReturn(just(respOk));

        HttpRouter router = mock(HttpRouter.class);
        when(router.route(any(HttpRequest.class))).thenReturn(Optional.of(pipeline));

        HttpResponse response = new RouteHandlerAdapter(router).handle(request, HttpInterceptorContext.create()).toBlocking().first();

        assertThat(response.status(), is(OK));
    }

}