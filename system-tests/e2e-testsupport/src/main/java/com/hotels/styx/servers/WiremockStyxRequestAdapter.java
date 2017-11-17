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
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.hotels.styx.api.HttpRequest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;

public class WiremockStyxRequestAdapter implements Request {
    private final HttpRequest styxRequest;
    private final String body;

    public WiremockStyxRequestAdapter(HttpRequest styxRequest, String body) {
        this.styxRequest = styxRequest;
        this.body = body;
    }

    @Override
    public String getUrl() {
        return styxRequest.path();
    }

    @Override
    public String getAbsoluteUrl() {
        String host = styxRequest.header(HOST).orElse("");
        String protocol = styxRequest.isSecure() ? "https" : "http";

        return protocol + "://" + host + styxRequest.url().toURI().toString();
    }

    @Override
    public RequestMethod getMethod() {
        return RequestMethod.fromString(styxRequest.method().name());
    }

    @Override
    public String getHeader(String key) {
        return styxRequest.header(key).orElse(null);
    }

    @Override
    public HttpHeader header(String key) {
        ImmutableList<String> values = styxRequest.headers(key);
        return HttpHeader.httpHeader(key, values.toArray(new String[values.size()]));
    }

    @Override
    public ContentTypeHeader contentTypeHeader() {
        return styxRequest.header(CONTENT_TYPE)
                .map(ContentTypeHeader::new)
                .orElse(ContentTypeHeader.absent());
    }

    @Override
    public HttpHeaders getHeaders() {
        List<HttpHeader> list = StreamSupport.stream(styxRequest.headers().spliterator(), false)
                .map(styxHeader -> HttpHeader.httpHeader(styxHeader.name(), styxHeader.value()))
                .collect(Collectors.toList());

        return new HttpHeaders(list);
    }

    @Override
    public boolean containsHeader(String key) {
        return styxRequest.headers().contains(key);
    }

    @Override
    public Set<String> getAllHeaderKeys() {
        return new HashSet<>(styxRequest.headers().names());
    }

    @Override
    public QueryParameter queryParameter(String key) {
        return styxRequest.queryParam(key)
                .map(value -> new QueryParameter(key, ImmutableList.of(value)))
                .orElse(QueryParameter.absent(key));
    }

    @Override
    public byte[] getBody() {
        return body.getBytes();
    }

    @Override
    public String getBodyAsString() {
        return body;
    }

    @Override
    public boolean isBrowserProxyRequest() {
        return false;
    }
}
