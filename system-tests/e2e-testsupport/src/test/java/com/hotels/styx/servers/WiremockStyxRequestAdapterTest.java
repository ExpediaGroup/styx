/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.servers;

import com.github.tomakehurst.wiremock.http.ContentTypeHeader;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.QueryParameter;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;

import static com.hotels.styx.api.HttpHeaderNames.CONNECTION;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.api.HttpRequest.Builder.post;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpHeaderNames.USER_AGENT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.testng.Assert.assertEquals;

public class WiremockStyxRequestAdapterTest {
    private WiremockStyxRequestAdapter adapter;
    private String content;

    @BeforeMethod
    public void setUp() throws Exception {
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

        adapter = new WiremockStyxRequestAdapter(
                post("/__admin/mappings/new?msg=6198.1")
                .header(CONTENT_LENGTH, "208")
                .header(CONTENT_TYPE, "application/json; charset=UTF-8")
                .header(HOST, "localhost")
                .header(CONNECTION, "Keep-Alive")
                .header(USER_AGENT, "Apache-HttpClient/4.3.5 (java 1.5)")
                .body(content)
                .build(), content);
    }

    @Test
    public void adaptsContainsHeader() throws Exception {
        assertThat(adapter.containsHeader("Foo-Bar"), is(false));
        assertThat(adapter.containsHeader("Host"), is(true));
        assertThat(adapter.containsHeader("host"), is(true));
    }

    @Test(enabled = false)
    public void adaptsGetAllHeaderKeys() throws Exception {
        assertEquals(adapter.getAllHeaderKeys(), ImmutableSet.of("Connection", "user-agent", "Content-Type", "Content-Length", "host"));
    }

    @Test
    public void adaptsGetUrl() throws Exception {
        assertThat(adapter.getUrl(), is("/__admin/mappings/new"));
    }

    @Test
    public void adaptsGetAbsoluteUrl() throws Exception {
        assertThat(adapter.getAbsoluteUrl(), is("http://localhost/__admin/mappings/new?msg=6198.1"));
    }

    @Test
    public void adaptsGetMethod() throws Exception {
        assertThat(adapter.getMethod(), is(RequestMethod.POST));
    }

    @Test
    public void adaptsGetHeader() throws Exception {
        assertThat(adapter.getHeader("Content-Length"), is("246"));
        assertThat(adapter.getHeader("Foo-Bar"), is(nullValue()));
    }

    @Test(enabled = false)
    public void adaptsGetHeaders() throws Exception {
        HttpHeaders headers = adapter.getHeaders();

        assertThat(headers, is(
                new com.github.tomakehurst.wiremock.http.HttpHeaders(
                        wireMockHeader("Content-Type", "application/json; charset=UTF-8"),
                        wireMockHeader("Host", "localhost"),
                        wireMockHeader("Connection", "Keep-Alive"),
                        wireMockHeader("User-Agent", "Apache-HttpClient/4.3.5 (java 1.5)"),
                        wireMockHeader("Content-Length", "208")
                )));
    }

    private HttpHeader wireMockHeader(String key, String value) {
        return new HttpHeader(key, value);
    }

    @Test
    public void adaptsGetBodyWhenAbsent() throws Exception {
        assertThat(adapter.getBody(), is(content.getBytes(UTF_8)));
    }

    @Test
    public void adaptsGetBodyWhenPresent() throws Exception {
        assertThat(adapter.getBody(), is(content.getBytes(UTF_8)));
    }

    @Test
    public void adaptsGetBodyAsStringWhenAbsent() throws Exception {
        assertThat(adapter.getBodyAsString(), is(content));
    }

    @Test
    public void adaptsGetBodyAsStringWhenPresent() throws Exception {
        assertThat(adapter.getBodyAsString(), is(content));
    }

    @Test
    public void adaptsContentTypeHeaderWhenPresent() throws Exception {
        ContentTypeHeader contentType = adapter.contentTypeHeader();

        assertThat(contentType.encodingPart(), is(Optional.of("UTF-8")));
        assertThat(contentType.mimeTypePart(), is("application/json"));
    }

    @Test
    public void adaptsQueryParameter() throws Exception {
        QueryParameter msg = adapter.queryParameter("msg");

        assertThat(msg.key(), is("msg"));
        assertThat(msg.values(), is(ImmutableList.of("6198.1")));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void adaptsNonExistantQueryParameterToNull() throws Exception {
        QueryParameter msg = adapter.queryParameter("foobar");

        assertThat(msg.key(), is("foobar"));
        assertThat(msg.values(), is(Collections.emptyList()));
    }

    @Test(enabled = false)
    public void adaptsContentTypeHeaderWhenAbsent() throws Exception {
        assertThat(adapter.contentTypeHeader(), is(ContentTypeHeader.absent()));
    }

    @Test
    public void adaptsIsBrowserProxyRequest() throws Exception {
        assertThat(adapter.isBrowserProxyRequest(), is(false));
    }

}