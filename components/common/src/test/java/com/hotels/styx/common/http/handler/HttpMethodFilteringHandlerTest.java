/*
  Copyright (C) 2013-2023 Expedia Inc.

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
package com.hotels.styx.common.http.handler;

import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.WebServiceHandler;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static com.hotels.styx.support.Support.requestContext;
import static com.hotels.styx.api.HttpMethod.GET;
import static com.hotels.styx.api.HttpMethod.POST;
import static com.hotels.styx.api.HttpRequest.post;
import static com.hotels.styx.api.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class HttpMethodFilteringHandlerTest {
    @Test
    public void delegatesTheRequestIfRequestMethodIsSupported() {
        WebServiceHandler handler = mock(WebServiceHandler.class);
        HttpMethodFilteringHandler post = new HttpMethodFilteringHandler(POST, handler);

        HttpRequest request = post("/some-uri").build();
        post.handle(request, mock(HttpInterceptor.Context.class));

        verify(handler).handle(eq(request), any(HttpInterceptor.Context.class));
    }

    @Test
    public void failsIfRequestMethodIsNotSupported() throws Exception {
        WebServiceHandler handler = mock(WebServiceHandler.class);
        HttpMethodFilteringHandler post = new HttpMethodFilteringHandler(GET, handler);

        HttpRequest request = post("/some-uri").build();
        HttpResponse response = Mono.from(post.handle(request, requestContext())).block();

        assertThat(response.status(), is(METHOD_NOT_ALLOWED));
    }
}
