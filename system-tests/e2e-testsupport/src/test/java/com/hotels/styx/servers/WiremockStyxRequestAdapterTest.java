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
package com.hotels.styx.servers;

import com.github.tomakehurst.wiremock.http.ContentTypeHeader;
import com.github.tomakehurst.wiremock.http.QueryParameter;
import com.google.common.base.Optional;
import com.hotels.styx.api.FullHttpRequest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.http.RequestMethod.POST;
import static com.hotels.styx.api.HttpHeaderNames.CONNECTION;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpHeaderNames.USER_AGENT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.testng.Assert.assertEquals;


public class WiremockStyxRequestAdapterTest {
    private WiremockStyxRequestAdapter adapter;
    private String content;
    private FullHttpRequest.Builder styxRequestBuilder;

    @BeforeMethod
    public void setUp() {
        content = "{\n" +
                "    \"request\" : {\n" +
                "        \"urlPattern\" : \"/.*\",\n" +
                "        \"method\" : \"GET\"\n" +
                "    },\n" +
                "    \"response\" : {\n" +
                "        \"status\" : 200,\n" +
                "        \"body\" : \"Hello, World!\",\n" +
                "        \"headers\" : {\n" +
                "            \"Stub-Origin-Info\" : \"App TLS v1.1\"\n" +
                "        }\n" +
                "    }\n" +
                "}";

        styxRequestBuilder = FullHttpRequest.post("/__admin/mappings/new?msg=6198.1")
                .header(CONTENT_TYPE, "application/json; charset=UTF-8")
                .header(HOST, "localhost")
                .header(CONNECTION, "Keep-Alive")
                .header(USER_AGENT, "Apache-HttpClient/4.3.5 (java 1.5)")
                .body(content, UTF_8);

        adapter = new WiremockStyxRequestAdapter(styxRequestBuilder.build());
    }

    @Test
    public void adaptsContainsHeader() {
        assertThat(adapter.containsHeader("Foo-Bar"), is(false));
        assertThat(adapter.containsHeader("Host"), is(true));
        assertThat(adapter.containsHeader("host"), is(true));
    }


    // Disabled due to a failing test.
    // Looks like it is an underlying problem with Netty, which doesn't convert
    // HTTP header names from AsciiString to String when toArray() is called on
    // CharSequenceDelegatingStringSet.
    @Test(enabled = false)
    public void adaptsGetAllHeaderKeys() {
        assertEquals(adapter.getAllHeaderKeys(), contains("Connection", "user-agent", "Content-Type", "Content-Length", "host"));
    }

    @Test
    public void adaptsGetUrl() {
        assertThat(adapter.getUrl(), is("/__admin/mappings/new"));
    }

    @Test
    public void adaptsGetAbsoluteUrl() {
        assertThat(adapter.getAbsoluteUrl(), is("http://localhost/__admin/mappings/new?msg=6198.1"));
    }

    @Test
    public void adaptsGetMethod() {
        assertThat(adapter.getMethod(), is(POST));
    }

    @Test
    public void adaptsGetHeader() {
        assertThat(adapter.getHeader("Content-Length"), is("246"));
        assertThat(adapter.getHeader("Foo-Bar"), is(nullValue()));
    }

    @Test
    public void adaptsGetHeaders() {
        assertThat(adapter.getHeaders().keys(), containsInAnyOrder("Content-Type", "host", "Connection", "user-agent", "Content-Length"));

        assertThat(adapter.getHeader("Content-Type"), is("application/json; charset=UTF-8"));
        assertThat(adapter.getHeader("host"), is("localhost"));
        assertThat(adapter.getHeader("Connection"), is("Keep-Alive"));
        assertThat(adapter.getHeader("user-agent"), is("Apache-HttpClient/4.3.5 (java 1.5)"));
        assertThat(adapter.getHeader("Content-Length"), is("246"));
    }

    @Test
    public void adaptsGetBodyWhenAbsent() {
        assertThat(adapter.getBody(), is(content.getBytes(UTF_8)));
    }

    @Test
    public void adaptsGetBodyWhenPresent() {
        assertThat(adapter.getBody(), is(content.getBytes(UTF_8)));
    }

    @Test
    public void adaptsGetBodyAsStringWhenAbsent() {
        assertThat(adapter.getBodyAsString(), is(content));
    }

    @Test
    public void adaptsGetBodyAsStringWhenPresent() {
        assertThat(adapter.getBodyAsString(), is(content));
    }

    @Test
    public void adaptsContentTypeHeaderWhenPresent() {
        ContentTypeHeader contentType = adapter.contentTypeHeader();

        assertThat(contentType.encodingPart(), is(Optional.of("UTF-8")));
        assertThat(contentType.mimeTypePart(), is("application/json"));
    }

    @Test
    public void adaptsQueryParameter() {
        QueryParameter msg = adapter.queryParameter("msg");

        assertThat(msg.key(), is("msg"));
        assertThat(msg.values(), is(contains("6198.1")));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void adaptsNonExistantQueryParameterToNull() {
        QueryParameter msg = adapter.queryParameter("foobar");

        assertThat(msg.key(), is("foobar"));
        msg.values();
    }

    @Test
    public void adaptsContentTypeHeaderWhenAbsent() {
        adapter = new WiremockStyxRequestAdapter(
                styxRequestBuilder
                        .removeHeader(CONTENT_TYPE)
                        .build());

        // NOTE: We don't call actual.mimeTypePart() or encodingPart() methods.
        // WireMock will throw a NullPointerException when they are called on an
        // absent ContentTypeHeader. Possibly a bug.
        assertThat(adapter.contentTypeHeader().key(), is("Content-Type"));
    }

    @Test
    public void adaptsIsBrowserProxyRequest() {
        assertThat(adapter.isBrowserProxyRequest(), is(false));
    }

}