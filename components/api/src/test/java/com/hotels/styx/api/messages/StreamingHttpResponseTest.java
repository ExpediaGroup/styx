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
package com.hotels.styx.api.messages;

import com.google.common.collect.Iterables;
import com.hotels.styx.api.HttpCookie;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import rx.Observable;

import java.util.stream.Stream;

import static com.hotels.styx.api.HttpCookie.cookie;
import static com.hotels.styx.api.HttpCookieAttribute.domain;
import static com.hotels.styx.api.HttpCookieAttribute.maxAge;
import static com.hotels.styx.api.HttpCookieAttribute.path;
import static com.hotels.styx.api.HttpHeader.header;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.LOCATION;
import static com.hotels.styx.api.matchers.HttpHeadersMatcher.isNotCacheable;
import static com.hotels.styx.api.messages.HttpResponseStatus.BAD_GATEWAY;
import static com.hotels.styx.api.messages.HttpResponseStatus.BAD_REQUEST;
import static com.hotels.styx.api.messages.HttpResponseStatus.CREATED;
import static com.hotels.styx.api.messages.HttpResponseStatus.GATEWAY_TIMEOUT;
import static com.hotels.styx.api.messages.HttpResponseStatus.MOVED_PERMANENTLY;
import static com.hotels.styx.api.messages.HttpResponseStatus.MULTIPLE_CHOICES;
import static com.hotels.styx.api.messages.HttpResponseStatus.NO_CONTENT;
import static com.hotels.styx.api.messages.HttpResponseStatus.OK;
import static com.hotels.styx.api.messages.HttpResponseStatus.SEE_OTHER;
import static com.hotels.styx.api.messages.HttpResponseStatus.TEMPORARY_REDIRECT;
import static com.hotels.styx.api.messages.HttpVersion.HTTP_1_0;
import static com.hotels.styx.api.messages.HttpVersion.HTTP_1_1;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

public class StreamingHttpResponseTest {
    @Test
    public void encodesToFullHttpResponse() {
        StreamingHttpResponse response = response(CREATED)
                .version(HTTP_1_0)
                .header("HeaderName", "HeaderValue")
                .addCookie("CookieName", "CookieValue")
                .body(body("foo", "bar"))
                .build();

        FullHttpResponse full = response.toFullHttpResponse(0x1000)
                .toBlocking()
                .single();

        assertThat(full.status(), is(CREATED));
        assertThat(full.version(), is(HTTP_1_0));
        assertThat(full.headers(), contains(header("HeaderName", "HeaderValue")));
        assertThat(full.cookies(), contains(cookie("CookieName", "CookieValue")));

        assertThat(full.body(), is(bytes("foobar")));
    }

    @Test(dataProvider = "emptyBodyResponses")
    public void encodesToFullHttpResponseWithEmptyBody(StreamingHttpResponse response) {
        FullHttpResponse full = response.toFullHttpResponse(0x1000)
                .toBlocking()
                .single();

        assertThat(full.body(), is(new byte[0]));
    }

    // We want to ensure that these are all considered equivalent
    @DataProvider(name = "emptyBodyResponses")
    private Object[][] emptyBodyResponses() {
        return new Object[][]{
                {response().build()},
                {response().body(Observable.empty()).build()},
        };
    }

    @Test
    public void createsAResponseWithDefaultValues() {
        StreamingHttpResponse response = response().build();
        assertThat(response.version(), is(HTTP_1_1));
        assertThat(response.cookies(), is(emptyIterable()));
        assertThat(response.headers(), is(emptyIterable()));
        assertThat(bytesToString(response.body()), is(""));
    }

    @Test
    public void createsResponseWithMinimalInformation() {
        StreamingHttpResponse response = response()
                .status(BAD_GATEWAY)
                .version(HTTP_1_0)
                .build();

        assertThat(response.status(), is(BAD_GATEWAY));
        assertThat(response.version(), is(HTTP_1_0));
        assertThat(response.cookies(), is(emptyIterable()));
        assertThat(response.headers(), is(emptyIterable()));
        assertThat(bytesToString(response.body()), is(""));
    }

    @Test
    public void setsASingleOutboundCookie() {
        StreamingHttpResponse response = response()
                .addCookie(cookie("user", "QSplbl9HX1VL", domain(".hotels.com"), path("/"), maxAge(3600)))
                .build();

        assertThat(response.cookie("user"), isValue(cookie("user", "QSplbl9HX1VL", domain(".hotels.com"), path("/"), maxAge(3600))));
    }

    @Test
    public void setsMultipleOutboundCookies() {
        StreamingHttpResponse response = response()
                .addCookie("a", "b")
                .addCookie("c", "d")
                .build();

        Iterable<HttpCookie> cookies = response.cookies();
        assertThat(Iterables.size(cookies), is(2));

        assertThat(Iterables.get(cookies, 0), is(cookie("a", "b")));
        assertThat(Iterables.get(cookies, 1), is(cookie("c", "d")));
    }

    @Test
    public void getASingleCookieValue() {
        StreamingHttpResponse response = response()
                .addCookie("a", "b")
                .addCookie("c", "d")
                .build();

        assertThat(response.cookie("c"), isValue(cookie("c", "d")));
    }

    @Test
    public void canRemoveAHeader() {
        Object headerValue = "b";
        StreamingHttpResponse response = response()
                .header("a", headerValue)
                .addHeader("c", headerValue)
                .build();
        StreamingHttpResponse shouldRemoveHeader = response.newBuilder()
                .removeHeader("c")
                .build();

        assertThat(shouldRemoveHeader.headers(), contains(header("a", "b")));
    }

    @Test
    public void removesACookie() {
        StreamingHttpResponse response = new StreamingHttpResponse.Builder(seeOther("/home"))
                .addCookie(cookie("a", "b"))
                .addCookie(cookie("c", "d"))
                .build();
        StreamingHttpResponse shouldClearCookie = response.newBuilder()
                .removeCookie("a")
                .build();

        assertThat(shouldClearCookie.cookies(), contains(cookie("c", "d")));
    }

    private static StreamingHttpResponse seeOther(String newLocation) {
        return response(SEE_OTHER)
                .header(LOCATION, newLocation)
                .build();
    }

    @Test
    public void canRemoveResponseBody() {
        StreamingHttpResponse response = response(NO_CONTENT)
                .body(body("shouldn't be here"))
                .build();

        StreamingHttpResponse shouldClearBody = response.newBuilder()
                .body(null)
                .build();

        assertThat(shouldClearBody.body(), is(nullValue()));
    }

    @Test
    public void supportsCaseInsensitiveHeaderNames() {
        StreamingHttpResponse response = response(OK).header("Content-Type", "text/plain").build();
        assertThat(response.header("content-type"), isValue("text/plain"));
    }

    @Test
    public void headerValuesAreCaseSensitive() {
        StreamingHttpResponse response = response(OK).header("Content-Type", "TEXT/PLAIN").build();
        assertThat(response.header("content-type"), not(isValue("text/plain")));
    }

    @Test
    public void createsANonCacheableResponse() {
        assertThat(response().disableCaching().build().headers(), is(isNotCacheable()));
    }

    @Test
    public void shouldCreateAChunkedResponse() {
        assertThat(response().build().chunked(), is(false));
        assertThat(response().setChunked().build().chunked(), is(true));
    }

    @Test
    public void shouldRemoveContentLengthFromChunkedMessages() {
        StreamingHttpResponse response = response().header(CONTENT_LENGTH, 5).build();
        StreamingHttpResponse chunkedResponse = response.newBuilder().setChunked().build();

        assertThat(chunkedResponse.chunked(), is(true));
        assertThat(chunkedResponse.header(CONTENT_LENGTH).isPresent(), is(false));
    }

    @Test
    public void shouldNotFailToRemoveNonExistentContentLength() {
        StreamingHttpResponse response = response().build();
        StreamingHttpResponse chunkedResponse = response.newBuilder().setChunked().build();

        assertThat(chunkedResponse.chunked(), is(true));
        assertThat(chunkedResponse.header(CONTENT_LENGTH).isPresent(), is(false));
    }

    @Test
    public void addsHeaderValue() {
        StreamingHttpResponse response = response()
                .header("name", "value1")
                .addHeader("name", "value2")
                .build();

        assertThat(response.headers(), hasItem(header("name", "value1")));
        assertThat(response.headers(), hasItem(header("name", "value2")));
    }

    @Test(dataProvider = "responses")
    public void shouldCheckIfCurrentResponseIsARedirectToOtherResource(HttpResponseStatus status, boolean isRedirect) {
        assertThat(response(status).build().isRedirect(), is(isRedirect));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void rejectsNullCookie() {
        response().addCookie(null).build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsNullCookieName() {
        response().addCookie(null, "value").build();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void rejectsNullCookieValue() {
        response().addCookie("name", null).build();
    }

    @DataProvider(name = "responses")
    public static Object[][] responses() {
        // format: {status, true if redirect}
        return new Object[][]{
                {SEE_OTHER, true},
                {TEMPORARY_REDIRECT, true},
                {MULTIPLE_CHOICES, true},
                {MOVED_PERMANENTLY, true},
                {TEMPORARY_REDIRECT, true},
                {OK, false},
                {BAD_REQUEST, false},
                {GATEWAY_TIMEOUT, false},
                {CREATED, false},
        };
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsMultipleContentLengthInSingleHeader() {
        response()
                .addHeader(CONTENT_LENGTH, "15, 16")
                .ensureContentLengthIsValid()
                .build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsMultipleContentLength() {
        response()
                .addHeader(CONTENT_LENGTH, "15")
                .addHeader(CONTENT_LENGTH, "16")
                .ensureContentLengthIsValid()
                .build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsInvalidContentLength() {
        response()
                .addHeader(CONTENT_LENGTH, "foo")
                .ensureContentLengthIsValid()
                .build();
    }

    private static StreamingHttpResponse.Builder response() {
        return StreamingHttpResponse.response();
    }

    private static StreamingHttpResponse.Builder response(HttpResponseStatus status) {
        return StreamingHttpResponse.response(status);
    }

    private static Observable<ByteBuf> body(String... contents) {
        return Observable.from(Stream.of(contents)
                .map(content -> Unpooled.copiedBuffer(content, UTF_8))
                .collect(toList()));
    }

    private static String bytesToString(Observable<ByteBuf> body) {
        return body.toList()
                .toBlocking()
                .single()
                .stream()
                .map(byteBuf -> byteBuf.toString(UTF_8))
                .collect(joining());
    }

    private static byte[] bytes(String s) {
        return s.getBytes(UTF_8);
    }
}