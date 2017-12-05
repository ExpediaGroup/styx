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
package com.hotels.styx.api.messages;

import com.google.common.collect.Iterables;
import com.hotels.styx.api.HttpCookie;
import com.hotels.styx.api.HttpResponse;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import rx.observers.TestSubscriber;

import java.util.Optional;

import static com.google.common.net.MediaType.ANY_AUDIO_TYPE;
import static com.hotels.styx.api.HttpCookie.cookie;
import static com.hotels.styx.api.HttpCookieAttribute.domain;
import static com.hotels.styx.api.HttpCookieAttribute.maxAge;
import static com.hotels.styx.api.HttpCookieAttribute.path;
import static com.hotels.styx.api.HttpHeader.header;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.LOCATION;
import static com.hotels.styx.api.matchers.HttpHeadersMatcher.isNotCacheable;
import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_GATEWAY;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CREATED;
import static io.netty.handler.codec.http.HttpResponseStatus.GATEWAY_TIMEOUT;
import static io.netty.handler.codec.http.HttpResponseStatus.MOVED_PERMANENTLY;
import static io.netty.handler.codec.http.HttpResponseStatus.MULTIPLE_CHOICES;
import static io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.SEE_OTHER;
import static io.netty.handler.codec.http.HttpResponseStatus.TEMPORARY_REDIRECT;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_0;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

public class FullHttpResponseTest {
    @Test
    public void encodesToStreamingHttpResponse() {
        FullHttpResponse<String> response = response(CREATED)
                .version(HTTP_1_0)
                .header("HeaderName", "HeaderValue")
                .addCookie("CookieName", "CookieValue")
                .body("foobar")
                .build();

        HttpResponse streaming = response.toStreamingHttpResponse(string -> copiedBuffer(string, UTF_8));

        assertThat(streaming.status(), is(CREATED));
        assertThat(streaming.version(), is(HTTP_1_0));
        assertThat(streaming.headers(), contains(header("HeaderName", "HeaderValue")));
        assertThat(streaming.cookies(), contains(cookie("CookieName", "CookieValue")));

        String body = streaming.body()
                .decode(byteBuf -> byteBuf.toString(UTF_8), 0x100000)
                .toBlocking()
                .single();

        assertThat(body, is("foobar"));
    }

    @Test(dataProvider = "emptyBodyResponses")
    public void encodesToStreamingHttpResponseWithEmptyBody(FullHttpResponse<String> response) {
        HttpResponse streaming = response.toStreamingHttpResponse(string -> copiedBuffer(string, UTF_8));

        TestSubscriber<ByteBuf> subscriber = TestSubscriber.create(0);
        subscriber.requestMore(1);

        streaming.body().content().subscribe(subscriber);

        assertThat(subscriber.getOnNextEvents().size(), is(0));
        subscriber.assertCompleted();
    }

    // We want to ensure that these are all considered equivalent
    @DataProvider(name = "emptyBodyResponses")
    private Object[][] emptyBodyResponses() {
        return new Object[][]{
                {response()
                        .build()},
                {response()
                        .body(null)
                        .build()},
                {response()
                        .body("")
                        .build()},
        };
    }

    @Test
    public void encodingToStreamingHttpResponseDefaultsToUTF8() {
        FullHttpResponse<String> response = response()
                .body("foobar")
                .build();

        HttpResponse streaming = FullHttpResponse.toStreamingHttpResponse(response);

        TestSubscriber<ByteBuf> subscriber = TestSubscriber.create(0);
        subscriber.requestMore(1);

        streaming.body().content().subscribe(subscriber);

        assertThat(subscriber.getOnNextEvents().size(), is(1));
        ByteBuf buf = subscriber.getOnNextEvents().get(0);
        assertThat(buf.toString(UTF_8), is("foobar"));
    }

    @Test
    public void createsAResponseWithDefaultValues() {
        FullHttpResponse<String> response = response().build();
        assertThat(response.version(), is(HTTP_1_1));
        assertThat(response.cookies(), is(emptyIterable()));
        assertThat(response.headers(), is(emptyIterable()));
        assertThat(response.body(), is(nullValue()));
    }

    @Test
    public void createsResponseWithMinimalInformation() {
        FullHttpResponse<String> response = response()
                .status(BAD_GATEWAY)
                .version(HTTP_1_0)
                .build();

        assertThat(response.status(), is(BAD_GATEWAY));
        assertThat(response.version(), is(HTTP_1_0));
        assertThat(response.cookies(), is(emptyIterable()));
        assertThat(response.headers(), is(emptyIterable()));
        assertThat(response.body(), is(nullValue()));
    }

    @Test
    public void setsTheContentType() {
        assertThat(response().contentType(ANY_AUDIO_TYPE).build().contentType(), isValue(ANY_AUDIO_TYPE.toString()));
    }

    @Test
    public void setsASingleOutboundCookie() {
        FullHttpResponse<String> response = response()
                .addCookie(cookie("user", "QSplbl9HX1VL", domain(".hotels.com"), path("/"), maxAge(3600)))
                .build();

        assertThat(response.cookie("user"), isValue(cookie("user", "QSplbl9HX1VL", domain(".hotels.com"), path("/"), maxAge(3600))));
    }

    @Test
    public void setsMultipleOutboundCookies() {
        FullHttpResponse<String> response = response()
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
        FullHttpResponse<String> response = response()
                .addCookie("a", "b")
                .addCookie("c", "d")
                .build();

        assertThat(response.cookie("c"), isValue(cookie("c", "d")));
    }

    @Test
    public void canRemoveAHeader() {
        Object headerValue = "b";
        FullHttpResponse<String> response = response()
                .header("a", headerValue)
                .addHeader("c", headerValue)
                .build();
        FullHttpResponse<String> shouldRemoveHeader = response.newBuilder()
                .removeHeader("c")
                .build();

        assertThat(shouldRemoveHeader.headers(), contains(header("a", "b")));
    }

    @Test
    public void removesACookie() {
        FullHttpResponse<String> response = new FullHttpResponse.Builder<>(seeOther("/home"))
                .addCookie(cookie("a", "b"))
                .addCookie(cookie("c", "d"))
                .build();
        FullHttpResponse<String> shouldClearCookie = response.newBuilder()
                .removeCookie("a")
                .build();

        assertThat(shouldClearCookie.cookies(), contains(cookie("c", "d")));
    }

    private static FullHttpResponse<String> seeOther(String newLocation) {
        return response(SEE_OTHER)
                .header(LOCATION, newLocation)
                .build();
    }

    @Test
    public void canRemoveResponseBody() {
        FullHttpResponse<String> response = response(NO_CONTENT)
                .body("shouldn't be here")
                .build();

        FullHttpResponse<String> shouldClearBody = response.newBuilder()
                .body(null)
                .build();

        assertThat(shouldClearBody.body(), is(nullValue()));
    }

    @Test
    public void supportsCaseInsensitiveHeaderNames() {
        FullHttpResponse<String> response = response(OK).header("Content-Type", "text/plain").build();
        assertThat(response.header("content-type"), isValue("text/plain"));
    }

    @Test
    public void headerValuesAreCaseSensitive() {
        FullHttpResponse<String> response = response(OK).header("Content-Type", "TEXT/PLAIN").build();
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
        FullHttpResponse<String> response = response().header(CONTENT_LENGTH, 5).build();
        FullHttpResponse<String> chunkedResponse = response.newBuilder().setChunked().build();

        assertThat(chunkedResponse.chunked(), is(true));
        assertThat(chunkedResponse.header(CONTENT_LENGTH).isPresent(), is(false));
    }

    @Test
    public void shouldNotFailToRemoveNonExistentContentLength() {
        FullHttpResponse<String> response = response().build();
        FullHttpResponse<String> chunkedResponse = response.newBuilder().setChunked().build();

        assertThat(chunkedResponse.chunked(), is(true));
        assertThat(chunkedResponse.header(CONTENT_LENGTH).isPresent(), is(false));
    }

    @Test
    public void overridesContent() {
        FullHttpResponse<String> response = response()
                .body("Response content.")
                .body(" ")
                .body("Extra content")
                .build();

        assertThat(response.body(), is("Extra content"));
    }

    @Test
    public void addsHeaderValue() {
        FullHttpResponse<String> response = response()
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
    public void rejectsMultipleContentLengthInSingleHeader() throws Exception {
        response()
                .addHeader(CONTENT_LENGTH, "15, 16")
                .ensureContentLengthIsValid()
                .build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsMultipleContentLength() throws Exception {
        response()
                .addHeader(CONTENT_LENGTH, "15")
                .addHeader(CONTENT_LENGTH, "16")
                .ensureContentLengthIsValid()
                .build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsInvalidContentLength() throws Exception {
        response()
                .addHeader(CONTENT_LENGTH, "foo")
                .ensureContentLengthIsValid()
                .build();
    }

    @Test
    public void allowsModificationOfHeadersBasedOnBody() {
        FullHttpResponse<String> response = response()
                .body("foobar")
                .build();

        assertThat(response.header("some-header"), isAbsent());

        FullHttpResponse<String> newResponse = response.newBuilder()
                .header("some-header", contentLength(response.body()))
                .build();

        assertThat(newResponse.header("some-header"), isValue("6"));
        assertThat(newResponse.body(), is("foobar"));
    }

    @Test
    public void allowsModificationOfBodyBasedOnExistingBody() {
        FullHttpResponse<String> response = response()
                .body("foobar")
                .build();

        FullHttpResponse<String> newResponse = response.newBuilder()
                .body(response.body() + "x")
                .build();

        assertThat(newResponse.body(), is("foobarx"));
    }

    private static FullHttpResponse.Builder<String> response() {
        return FullHttpResponse.response();
    }

    private static FullHttpResponse.Builder<String> response(HttpResponseStatus status) {
        return FullHttpResponse.response(status);
    }

    private static int contentLength(String content) {
        return content.getBytes(UTF_8).length;
    }

    private static Optional<String> contentLength(FullHttpResponse.Builder<?> builder) {
        return builder.build().header(CONTENT_LENGTH);
    }
}