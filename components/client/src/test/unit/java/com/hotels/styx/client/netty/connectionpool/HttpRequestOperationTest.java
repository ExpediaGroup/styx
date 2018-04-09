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
package com.hotels.styx.client.netty.connectionpool;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.client.netty.connectionpool.HttpRequestOperation;
import io.netty.handler.codec.http.DefaultHttpRequest;
import org.testng.annotations.Test;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;

public class HttpRequestOperationTest {
    @Test
    public void shouldTransformStyxRequestToNettyRequestWithAllRelevantInformation() {
        HttpRequest request = new HttpRequest.Builder(GET)
                .header("X-Forwarded-Proto", "https")
                .addCookie("HASESSION_V3", "asdasdasd")
                .addCookie("has", "123456789")
                .uri("https://www.example.com/foo%2Cbar?foo,baf=2")
                .build();

        DefaultHttpRequest nettyRequest = HttpRequestOperation.toNettyRequest(request);
        assertThat(nettyRequest.getMethod(), is(GET));
        assertThat(nettyRequest.getUri(), is("https://www.example.com/foo%2Cbar?foo%2Cbaf=2"));
        assertThat(nettyRequest.headers().get("X-Forwarded-Proto"), is("https"));
        assertThat(nettyRequest.headers().getAll("Cookie"), contains("HASESSION_V3=asdasdasd; has=123456789"));
    }

    @Test
    public void shouldTransformUrlQueryParametersToNettyRequest() {
        HttpRequest request = new HttpRequest.Builder(GET)
                .header("X-Forwarded-Proto", "https")
                .uri("https://www.example.com/foo?some=value&blah=blah")
                .build();

        HttpRequest.Builder builder = request.newBuilder();

        HttpRequest newRequest = builder.url(
                request.url().newBuilder()
                        .addQueryParam("format", "json")
                        .build())
                .build();

        DefaultHttpRequest nettyRequest = HttpRequestOperation.toNettyRequest(newRequest);
        assertThat(nettyRequest.getMethod(), is(GET));
        assertThat(nettyRequest.getUri(), is("https://www.example.com/foo?some=value&blah=blah&format=json"));
        assertThat(nettyRequest.headers().get("X-Forwarded-Proto"), is("https"));
    }
}