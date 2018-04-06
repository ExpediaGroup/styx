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
package com.hotels.styx.client;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.client.UrlConnectionHttpClient;
import com.hotels.styx.support.server.FakeHttpServer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.HTML_UTF_8;
import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.HttpRequest.Builder.post;
import static com.hotels.styx.support.api.matchers.HttpResponseBodyMatcher.hasBody;
import static com.hotels.styx.support.api.matchers.HttpResponseStatusMatcher.hasStatus;
import static com.hotels.styx.api.support.HostAndPorts.freePort;
import static com.hotels.styx.support.server.UrlMatchingStrategies.urlEndingWith;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;

public class UrlConnectionHttpClientTest {
    int port = freePort();
    FakeHttpServer server = new FakeHttpServer(port);

    String host = format("http://localhost:%d", port);
    HttpClient client = new UrlConnectionHttpClient(1000, 3000);

    @BeforeClass
    public void startServer() {
        this.server.start();
    }

    @AfterClass
    public void stopServer() {
        this.server.stop();
    }

    @Test
    public void handlesGetRequests() throws Exception {
        server.stub(urlEndingWith("/get"), aResponse()
                .withHeader(CONTENT_TYPE, HTML_UTF_8.toString())
                .withBody(GET.name()));
        HttpResponse response = doRequest(request("/get"));
        assertThat(response, allOf(hasStatus(OK), hasBody(GET.name())));
        assertThat(response.contentType().get(), is("text/html; charset=utf-8"));
    }

    private HttpResponse doRequest(HttpRequest request) {
        return this.client.sendRequest(request).toBlocking().first();
    }

    @Test
    public void handlesPostRequests() throws IOException {
        server.stub(WireMock.post(urlEndingWith("/post")), aResponse().withBody(POST.name()));
        HttpRequest request = post(this.host + "/post").build();
        assertThat(doRequest(request), allOf(hasStatus(OK), hasBody(POST.name())));
    }

    @Test
    public void handlesErrorResponses() throws IOException {
        server.stub(urlEndingWith("/error"), aResponse()
                .withStatus(METHOD_NOT_ALLOWED.code())
                .withBody(METHOD_NOT_ALLOWED.toString()));

        assertThat(doRequest(request("/error")), allOf(hasStatus(METHOD_NOT_ALLOWED), hasBody(METHOD_NOT_ALLOWED.toString())));
    }

    private HttpRequest request(String path) {
        return get(this.host + path).build();
    }
}
