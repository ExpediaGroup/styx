/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx.server.netty.codec;

import com.hotels.styx.api.Url;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import org.testng.annotations.Test;

import java.util.Optional;

import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class UrlDecoderTest {
    @Test
    public void decodesOriginForm() {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/foo");
        request.headers().add(HOST, "example.com");

        Url url = UrlDecoder.decodeUrl(x -> x, request);

        assertThat(url.authority(), is(Optional.empty()));
        assertThat(url.path(), is("/foo"));
        assertThat(url.encodedUri(), is("/foo"));
        assertThat(url.isAbsolute(), is(false));
        assertThat(url.isRelative(), is(true));
        assertThat(url.host(), is(Optional.empty()));
        assertThat(url.query(), is(Optional.empty()));
        assertThat(url.scheme(), is(""));
    }

    @Test
    public void decodesOriginFormIssue391() {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "//www.abc.com//abc123Z");
        request.headers().add(HOST, "example.com");

        Url url = UrlDecoder.decodeUrl(x -> x, request);

        assertThat(url.authority(), is(Optional.empty()));
        assertThat(url.path(), is("//www.abc.com//abc123Z"));
        assertThat(url.encodedUri(), is("//www.abc.com//abc123Z"));
        assertThat(url.isAbsolute(), is(false));
        assertThat(url.isRelative(), is(true));
        assertThat(url.host(), is(Optional.empty()));
        assertThat(url.query(), is(Optional.empty()));
        assertThat(url.scheme(), is(""));
    }

    @Test
    public void decodesOriginFormWithLowercaseHostHeader() {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/foo");
        request.headers().add("host", "example.com");

        Url url = UrlDecoder.decodeUrl(x -> x, request);

        assertThat(url.authority(), is(Optional.empty()));
        assertThat(url.path(), is("/foo"));
        assertThat(url.encodedUri(), is("/foo"));
        assertThat(url.isAbsolute(), is(false));
        assertThat(url.isRelative(), is(true));
        assertThat(url.host(), is(Optional.empty()));
        assertThat(url.query(), is(Optional.empty()));
        assertThat(url.scheme(), is(""));
    }

    @Test
    public void decodesOriginFormWithUppercaseHostHeader() {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/foo");
        request.headers().add("host", "example.com");

        Url url = UrlDecoder.decodeUrl(x -> x, request);

        assertThat(url.authority(), is(Optional.empty()));
        assertThat(url.path(), is("/foo"));
        assertThat(url.encodedUri(), is("/foo"));
        assertThat(url.isAbsolute(), is(false));
        assertThat(url.isRelative(), is(true));
        assertThat(url.host(), is(Optional.empty()));
        assertThat(url.query(), is(Optional.empty()));
        assertThat(url.scheme(), is(""));
    }


    @Test
    public void decodesAbsoluteForm() {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "http://example.com/foo");

        Url url = UrlDecoder.decodeUrl(x -> x, request);

        assertThat(url.authority().isPresent(), is(true));
        assertThat(url.path(), is("/foo"));
        assertThat(url.encodedUri(), is("http://example.com/foo"));
        assertThat(url.isAbsolute(), is(true));
        assertThat(url.isRelative(), is(false));
        assertThat(url.host(), is(Optional.of("example.com")));
        assertThat(url.query(), is(Optional.empty()));
        assertThat(url.scheme(), is("http"));
    }


}