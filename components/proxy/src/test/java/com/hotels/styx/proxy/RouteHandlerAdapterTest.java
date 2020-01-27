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

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.server.HttpRouter;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static com.hotels.styx.support.Support.requestContext;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static com.hotels.styx.api.LiveHttpResponse.response;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RouteHandlerAdapterTest {

    private LiveHttpRequest request = get("/").build();
    private LiveHttpResponse respOk = response(OK).build();

    @Test
    public void injectsToPipelineWhenRouteFound() {
        HttpHandler pipeline = mock(HttpHandler.class);
        when(pipeline.handle(any(LiveHttpRequest.class), any(HttpInterceptor.Context.class))).thenReturn(Eventual.of(respOk));

        HttpRouter router = mock(HttpRouter.class);
        when(router.route(any(LiveHttpRequest.class), any(HttpInterceptor.Context.class))).thenReturn(Optional.of(pipeline));

        LiveHttpResponse response = Mono.from(new RouteHandlerAdapter(router).handle(request, requestContext())).block();

        assertThat(response.status(), is(OK));
    }

}