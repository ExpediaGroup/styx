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

import com.hotels.styx.api.HttpInterceptor.Chain;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.client.StyxHeaderConfig;
import org.testng.annotations.Test;

import static com.google.common.net.HttpHeaders.X_FORWARDED_FOR;
import static com.google.common.net.HttpHeaders.X_FORWARDED_PROTO;
import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.client.StyxHeaderConfig.REQUEST_ID_DEFAULT;
import static com.hotels.styx.proxy.interceptors.RequestRecordingChain.requestRecordingChain;
import static com.hotels.styx.proxy.interceptors.ReturnResponseChain.returnsResponse;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class RequestEnrichingInterceptorTest {
    private final Chain anyResponseHandler = returnsResponse(response().build());
    private final RequestEnrichingInterceptor requestEnrichingInterceptor = new RequestEnrichingInterceptor(new StyxHeaderConfig());

    @Test
    public void addsRequestIdToTheHeaders() {
        RequestRecordingChain recording = intercept(get("/some-uri"));

        assertThat(recording.recordedRequest().header(REQUEST_ID_DEFAULT), is(notNullValue()));
    }

    @Test
    public void setsTheRemoteAddressToTheForwardedForList() {
        RequestRecordingChain recording = intercept(get(""));

        assertThat(recording.recordedRequest().header(X_FORWARDED_FOR), isValue("127.0.0.1"));
    }

    @Test
    public void appendsTheRemoteAddressToTheForwardedForList() {
        RequestRecordingChain recording = intercept(get("/some")
                .header(X_FORWARDED_FOR, "172.21.175.59"));

        assertThat(recording.recordedRequest().header(X_FORWARDED_FOR), isValue("172.21.175.59, 127.0.0.1"));
    }

    @Test
    public void addsXForwardedProtoToHttpWhenAbsent() throws Exception {
        RequestRecordingChain recording = intercept(get("/some").secure(false));

        assertThat(recording.recordedRequest().header(X_FORWARDED_PROTO), isValue("http"));
    }

    @Test
    public void addsXForwardedProtoToHttpsWhenAbsent() throws Exception {
        RequestRecordingChain recording = intercept(get("/some").secure(true));

        assertThat(recording.recordedRequest().header(X_FORWARDED_PROTO), isValue("https"));
    }

    @Test
    public void retainsXForwardedProtoWhenPresentInHttpMessage() throws Exception {
        RequestRecordingChain recording = intercept(get("/some").secure(false).addHeader(X_FORWARDED_PROTO, "https"));
        assertThat(recording.recordedRequest().header(X_FORWARDED_PROTO), isValue("https"));

        recording = intercept(get("/some").secure(false).addHeader(X_FORWARDED_PROTO, "http"));
        assertThat(recording.recordedRequest().header(X_FORWARDED_PROTO), isValue("http"));
    }


    @Test
    public void retainsXForwardedProtoWhenPresentInHttpsMessage() throws Exception {
        RequestRecordingChain recording = intercept(get("/some").secure(true).addHeader(X_FORWARDED_PROTO, "http"));
        assertThat(recording.recordedRequest().header(X_FORWARDED_PROTO), isValue("http"));

        recording = intercept(get("/some").secure(true).addHeader(X_FORWARDED_PROTO, "https"));
        assertThat(recording.recordedRequest().header(X_FORWARDED_PROTO), isValue("https"));
    }

    private RequestRecordingChain intercept(HttpRequest.Builder builder) {
        return intercept(builder.build());
    }

    private RequestRecordingChain intercept(HttpRequest request) {
        RequestRecordingChain recording = requestRecordingChain(anyResponseHandler);
        requestEnrichingInterceptor.intercept(request, recording);
        return recording;
    }
}
