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
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;

import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpHeaderNames.VIA;
import static com.hotels.styx.api.HttpVersion.HTTP_1_0;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static com.hotels.styx.api.LiveHttpRequest.post;
import static com.hotels.styx.api.LiveHttpResponse.response;
import static com.hotels.styx.proxy.interceptors.RequestRecordingChain.requestRecordingChain;
import static com.hotels.styx.proxy.interceptors.ReturnResponseChain.returnsResponse;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class ViaHeaderAppendingInterceptorTest {
    final ReturnResponseChain ANY_RESPONSE_HANDLER = returnsResponse(response().build());
    final HttpInterceptor interceptor = new ViaHeaderAppendingInterceptor();

    private LiveHttpRequest interceptRequest(LiveHttpRequest request) {
        RequestRecordingChain recording = requestRecordingChain(ANY_RESPONSE_HANDLER);
        interceptor.intercept(request, recording);
        return recording.recordedRequest();
    }

    @Test
    public void addsViaHeaderToRequestIfNotAlreadyPresent() throws Exception {
        LiveHttpRequest request = post("/foo")
                .header(HOST, "www.example.com:8000")
                .build();

        LiveHttpRequest interceptedRequest = interceptRequest(request);
        assertThat(interceptedRequest.headers().get(VIA), isValue("1.1 styx"));
    }

    @Test
    public void addsViaHeaderToRequestWhenItIsPresentButEmpty() throws Exception {
        LiveHttpRequest request = post("/foo")
                .header(VIA, "")
                .build();

        LiveHttpRequest interceptedRequest = interceptRequest(request);
        assertThat(interceptedRequest.headers().get(VIA), isValue("1.1 styx"));
    }

    @Test
    public void appendsHttp10RequestVersionInRequestViaHeader() throws Exception {
        LiveHttpRequest request = post("/foo")
                .version(HTTP_1_0)
                .header(VIA, "")
                .build();

        LiveHttpRequest interceptedRequest = interceptRequest(request);
        assertThat(interceptedRequest.headers().get(VIA), isValue("1.0 styx"));
    }

    @Test
    public void appendsHttp10RequestVersionInResponseViaHeader() throws Exception {
        LiveHttpResponse response = Mono.from(interceptor.intercept(get("/foo").build(), ANY_RESPONSE_HANDLER)).block();
        assertThat(response.headers().get(VIA), isValue("1.1 styx"));
    }

    @Test
    public void appendsViaHeaderValueAtEndOfTheViaList() throws Exception {
        LiveHttpRequest request = post("/foo")
                .header(VIA, "1.0 ricky, 1.1 mertz, 1.0 lucy")
                .build();

        LiveHttpRequest interceptedRequest = interceptRequest(request);
        assertThat(interceptedRequest.headers().get(VIA), isValue("1.0 ricky, 1.1 mertz, 1.0 lucy, 1.1 styx"));
    }

    @Test
    public void appendsViaHeaderValueAtEndOfListInResponse() throws Exception {
        LiveHttpResponse response = Mono.from(interceptor.intercept(get("/foo").build(), returnsResponse(response()
                        .header(VIA, "1.0 ricky, 1.1 mertz, 1.0 lucy")
                        .build()))).block();

        assertThat(response.headers().get(VIA), isValue("1.0 ricky, 1.1 mertz, 1.0 lucy, 1.1 styx"));
    }
}