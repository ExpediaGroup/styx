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
package com.hotels.styx.proxy.interceptors;

import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import org.testng.annotations.Test;

import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpHeaderNames.VIA;
import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpRequest.post;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpVersion.HTTP_1_0;
import static com.hotels.styx.proxy.interceptors.RequestRecordingChain.requestRecordingChain;
import static com.hotels.styx.proxy.interceptors.ReturnResponseChain.returnsResponse;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class ViaHeaderAppendingInterceptorTest {
    final ReturnResponseChain ANY_RESPONSE_HANDLER = returnsResponse(response().build());
    final HttpInterceptor interceptor = new ViaHeaderAppendingInterceptor();

    private HttpRequest interceptRequest(HttpRequest request) {
        RequestRecordingChain recording = requestRecordingChain(ANY_RESPONSE_HANDLER);
        interceptor.intercept(request, recording);
        return recording.recordedRequest();
    }

    @Test
    public void addsViaHeaderToRequestIfNotAlreadyPresent() throws Exception {
        HttpRequest request = post("/foo")
                .header(HOST, "www.example.com:8000")
                .build();

        HttpRequest interceptedRequest = interceptRequest(request);
        assertThat(interceptedRequest.headers().get(VIA), isValue("1.1 styx"));
    }

    @Test
    public void addsViaHeaderToRequestWhenItIsPresentButEmpty() throws Exception {
        HttpRequest request = post("/foo")
                .header(VIA, "")
                .build();

        HttpRequest interceptedRequest = interceptRequest(request);
        assertThat(interceptedRequest.headers().get(VIA), isValue("1.1 styx"));
    }

    @Test
    public void appendsHttp10RequestVersionInRequestViaHeader() throws Exception {
        HttpRequest request = post("/foo")
                .version(HTTP_1_0)
                .header(VIA, "")
                .build();

        HttpRequest interceptedRequest = interceptRequest(request);
        assertThat(interceptedRequest.headers().get(VIA), isValue("1.0 styx"));
    }

    @Test
    public void appendsHttp10RequestVersionInResponseViaHeader() throws Exception {
        HttpResponse response = interceptor.intercept(get("/foo").build(), ANY_RESPONSE_HANDLER).asCompletableFuture().get();
        assertThat(response.headers().get(VIA), isValue("1.1 styx"));
    }

    @Test
    public void appendsViaHeaderValueAtEndOfTheViaList() throws Exception {
        HttpRequest request = post("/foo")
                .header(VIA, "1.0 ricky, 1.1 mertz, 1.0 lucy")
                .build();

        HttpRequest interceptedRequest = interceptRequest(request);
        assertThat(interceptedRequest.headers().get(VIA), isValue("1.0 ricky, 1.1 mertz, 1.0 lucy, 1.1 styx"));
    }

    @Test
    public void appendsViaHeaderValueAtEndOfListInResponse() throws Exception {
        HttpResponse response = interceptor.intercept(get("/foo").build(), returnsResponse(response()
                        .header(VIA, "1.0 ricky, 1.1 mertz, 1.0 lucy")
                        .build())
        ).asCompletableFuture().get();

        assertThat(response.headers().get(VIA), isValue("1.0 ricky, 1.1 mertz, 1.0 lucy, 1.1 styx"));
    }
}