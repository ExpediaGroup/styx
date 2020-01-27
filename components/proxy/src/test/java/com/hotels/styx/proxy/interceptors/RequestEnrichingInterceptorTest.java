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
package com.hotels.styx.proxy.interceptors;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpInterceptor.Chain;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.client.StyxHeaderConfig;
import org.junit.jupiter.api.Test;

import static com.google.common.net.HttpHeaders.X_FORWARDED_FOR;
import static com.google.common.net.HttpHeaders.X_FORWARDED_PROTO;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static com.hotels.styx.api.LiveHttpResponse.response;
import static com.hotels.styx.client.StyxHeaderConfig.REQUEST_ID_DEFAULT;
import static com.hotels.styx.proxy.interceptors.RequestRecordingChain.requestRecordingChain;
import static com.hotels.styx.support.Support.requestContext;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class RequestEnrichingInterceptorTest {
    private final RequestRecordingChain recording = requestRecordingChain(new TestChain(false));
    private final RequestRecordingChain recordingSecure = requestRecordingChain(new TestChain(true));

    private final RequestEnrichingInterceptor requestEnrichingInterceptor = new RequestEnrichingInterceptor(new StyxHeaderConfig());

    @Test
    public void addsRequestIdToTheHeaders() {
        requestEnrichingInterceptor.intercept(get("/some-uri").build(), recording);

        assertThat(recording.recordedRequest().header(REQUEST_ID_DEFAULT), is(notNullValue()));
    }

    @Test
    public void setsTheRemoteAddressToTheForwardedForList() {
        requestEnrichingInterceptor.intercept(get("").build(), recording);

        assertThat(recording.recordedRequest().header(X_FORWARDED_FOR), isValue("127.0.0.1"));
    }

    @Test
    public void appendsTheRemoteAddressToTheForwardedForList() {
        requestEnrichingInterceptor.intercept(get("/some")
                .header(X_FORWARDED_FOR, "172.21.175.59").build(), recording);

        assertThat(recording.recordedRequest().header(X_FORWARDED_FOR), isValue("172.21.175.59, 127.0.0.1"));
    }

    @Test
    public void addsXForwardedProtoToHttpWhenAbsent() {
        requestEnrichingInterceptor.intercept(get("/some").build(), recording);

        assertThat(recording.recordedRequest().header(X_FORWARDED_PROTO), isValue("http"));
    }

    @Test
    public void addsXForwardedProtoToHttpsWhenAbsent() {
        requestEnrichingInterceptor.intercept(get("/some").build(), recordingSecure);

        assertThat(recordingSecure.recordedRequest().header(X_FORWARDED_PROTO), isValue("https"));
    }

    @Test
    public void retainsXForwardedProtoWhenPresentInHttpMessage() {
        requestEnrichingInterceptor.intercept(get("/some").addHeader(X_FORWARDED_PROTO, "https").build(), recording);

        assertThat(recording.recordedRequest().header(X_FORWARDED_PROTO), isValue("https"));

        requestEnrichingInterceptor.intercept(get("/some").addHeader(X_FORWARDED_PROTO, "http").build(), recording);
        assertThat(recording.recordedRequest().header(X_FORWARDED_PROTO), isValue("http"));
    }

    @Test
    public void retainsXForwardedProtoWhenPresentInHttpsMessage() {
        requestEnrichingInterceptor.intercept(get("/some").addHeader(X_FORWARDED_PROTO, "http").build(), recording);

        assertThat(recording.recordedRequest().header(X_FORWARDED_PROTO), isValue("http"));

        requestEnrichingInterceptor.intercept(get("/some").addHeader(X_FORWARDED_PROTO, "https").build(), recording);
        assertThat(recording.recordedRequest().header(X_FORWARDED_PROTO), isValue("https"));
    }

    private static class TestChain implements Chain {
        private final boolean secure;
        TestChain(boolean secure) {
            this.secure = secure;
        }

        @Override
        public Eventual<LiveHttpResponse> proceed(LiveHttpRequest request) {
            return Eventual.of(response().build());
        }

        @Override
        public HttpInterceptor.Context context() {
            return requestContext(secure);
        }
    }
}
