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
package com.hotels.styx.api.http.handlers;

import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import org.testng.annotations.Test;

import static com.hotels.styx.api.TestSupport.getFirst;
import static com.hotels.styx.api.HttpRequest.Builder.post;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class HttpMethodFilteringHandlerTest {
    @Test
    public void delegatesTheRequestIfRequestMethodIsSupported() {
        HttpHandler handler = mock(HttpHandler.class);
        HttpMethodFilteringHandler post = new HttpMethodFilteringHandler(POST, handler);

        HttpRequest request = post("/some-uri").build();
        post.handle(request);

        verify(handler).handle(request);
    }

    @Test
    public void failsIfRequestMethodIsNotSupported() {
        HttpHandler handler = mock(HttpHandler.class);
        HttpMethodFilteringHandler post = new HttpMethodFilteringHandler(GET, handler);

        HttpRequest request = post("/some-uri").build();
        HttpResponse response = getFirst(post.handle(request));

        assertThat(response.status(), is(METHOD_NOT_ALLOWED));
    }
}
