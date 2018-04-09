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
package com.hotels.styx.api;

import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.hotels.styx.api.messages.FullHttpResponse;
import com.hotels.styx.api.messages.HttpResponseStatus;
import com.hotels.styx.api.messages.HttpVersion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import rx.Observable;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.net.MediaType.ANY_AUDIO_TYPE;
import static com.hotels.styx.api.HttpCookie.cookie;
import static com.hotels.styx.api.HttpCookieAttribute.domain;
import static com.hotels.styx.api.HttpCookieAttribute.maxAge;
import static com.hotels.styx.api.HttpCookieAttribute.path;
import static com.hotels.styx.api.HttpHeader.header;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.LOCATION;
import static com.hotels.styx.api.HttpMessageBody.NO_BODY;
import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.HttpResponse.Builder.newBuilder;
import static com.hotels.styx.api.HttpResponse.Builder.response;
import static com.hotels.styx.api.TestSupport.bodyAsString;
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
import static java.lang.String.valueOf;
import static java.time.ZoneOffset.UTC;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static rx.Observable.empty;
import static rx.Observable.just;

public class HttpResponseTest {
    @Test
    public void createsAResponseWithDefaultValues() {
        HttpResponse response = response().build();
        assertThat(response.version(), is(HTTP_1_1));
        assertThat(response.cookies(), is(emptyIterable()));
        assertThat(response.headers(), is(emptyIterable()));
        assertThat(response.body(), is(NO_BODY));
    }

    @Test
    public void createsResponseWithMinimalInformation() {
        HttpResponse response = response()
                .status(BAD_GATEWAY)
                .version(HTTP_1_0)
                .request(get("/home").id("id").build())
                .build();

        assertThat(response.status(), is(BAD_GATEWAY));
        assertThat(response.version(), is(HTTP_1_0));
        assertThat(response.cookies(), is(emptyIterable()));
        assertThat(response.headers(), is(emptyIterable()));
        assertThat(response.body(), is(NO_BODY));
        assertThat(response.request().toString(), is("HttpRequest{version=HTTP/1.1, method=GET, uri=/home, headers=[], cookies=[], id=id, clientAddress=127.0.0.1:0}"));
    }

    @Test
    public void setsTheContentType() {
        assertThat(response().contentType(ANY_AUDIO_TYPE).build().contentType().get(), is(ANY_AUDIO_TYPE.toString()));
    }

    @Test
    public void setsASingleOutboundCookie() throws Exception {
        HttpResponse response = response()
                .addCookie(cookie("user", "QSplbl9HX1VL", domain(".hotels.com"), path("/"), maxAge(3600)))
                .build();

        assertThat(response.cookie("user").get(), is(cookie("user", "QSplbl9HX1VL", domain(".hotels.com"), path("/"), maxAge(3600))));
    }

    @Test
    public void setsMultipleOutboundCookies() {
        HttpResponse response = response()
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
        HttpResponse response = response()
                .addCookie("a", "b")
                .addCookie("c", "d")
                .build();

        assertThat(response.cookie("c").get(), is(cookie("c", "d")));
    }

    @Test
    public void canRemoveAHeader() {
        Object headerValue = "b";
        HttpResponse response = response()
                .header("a", headerValue)
                .addHeader("c", headerValue)
                .build();
        HttpResponse shouldRemoveHeader = response.newBuilder()
                .removeHeader("c")
                .build();

        assertThat(shouldRemoveHeader.headers(), contains(header("a", "b")));
    }

    @Test
    public void removesACookie() {
        HttpResponse response = newBuilder(seeOther("/home"))
                .addCookie(cookie("a", "b"))
                .addCookie(cookie("c", "d"))
                .build();
        HttpResponse shouldClearCookie = response.newBuilder()
                .removeCookie("a")
                .build();

        assertThat(shouldClearCookie.cookies(), contains(cookie("c", "d")));
    }

    private static HttpResponse seeOther(String newLocation) {
        return response(SEE_OTHER)
                .header(LOCATION, newLocation)
                .build();
    }

    @Test
    public void canRemoveResponseBody() {
        HttpResponse response = response(NO_CONTENT)
                .body("shouldn't be here")
                .build();

        HttpResponse shouldClearBody = response.newBuilder()
                .removeBody()
                .build();

        String reply = shouldClearBody
                .body()
                .aggregate(18)
                .map(bytebuf -> bytebuf.toString(UTF_8))
                .toBlocking()
                .first();

        assertThat(reply, is(""));
    }

    @Test
    public void supportsCaseInsensitiveHeaderNames() {
        HttpResponse response = response(OK).header("Content-Type", "text/plain").build();
        assertThat(response.header("content-type").get(), is("text/plain"));
    }

    @Test
    public void headerValuesAreCaseSensitive() {
        HttpResponse response = response(OK).header("Content-Type", "TEXT/PLAIN").build();
        assertThat(response.header("content-type").get(), is(not("text/plain")));
    }

    @Test
    public void setsDateHeaderValuesInRfc1123Format() {
        Instant date = ZonedDateTime.of(2005, 3, 26, 12, 0, 0, 0, UTC).toInstant();

        HttpResponse response = response(OK)
                .header("date", date)
                .build();

        assertThat(response.header("date").get(), is("Sat, 26 Mar 2005 12:00:00 GMT"));
    }

    @Test
    public void createsANonCacheableResponse() {
        assertThat(response().disableCaching().build().headers(), is(isNotCacheable()));
    }

    @Test
    public void shouldCreateAChunkedResponse() {
        assertThat(response().build().chunked(), is(false));
        assertThat(response().chunked().build().chunked(), is(true));
    }

    @Test
    public void shouldRemoveContentLengthFromChunkedMessages() {
        HttpResponse response = response().header(CONTENT_LENGTH, 5).build();
        HttpResponse chunkedResponse = response.newBuilder().chunked().build();

        assertThat(chunkedResponse.chunked(), is(true));
        assertThat(chunkedResponse.header(CONTENT_LENGTH).isPresent(), is(false));
    }

    @Test
    public void shouldNotFailToRemoveNonExistentContentLength() {
        HttpResponse response = response().build();
        HttpResponse chunkedResponse = response.newBuilder().chunked().build();

        assertThat(chunkedResponse.chunked(), is(true));
        assertThat(chunkedResponse.header(CONTENT_LENGTH).isPresent(), is(false));
    }

    @Test
    public void shouldSetsContentLengthForNonStreamingBodyMessage() throws Exception {
        String helloLength = valueOf(bytes("Hello").length);

        assertThat(contentLength(response().body("")), is("0"));
        assertThat(contentLength(response().body("Hello")), is(helloLength));
        assertThat(contentLength(response().body(bytes("Hello"))), is(helloLength));
        assertThat(contentLength(response().body(ByteBuffer.wrap(bytes("Hello")))), is(helloLength));
        assertThat(response().body(just(copiedBuffer("Hello", UTF_8))).build().header(CONTENT_LENGTH).isPresent(), is(false));
    }

    private static String contentLength(HttpResponse.Builder builder) {
        return builder.build().header(CONTENT_LENGTH).get();
    }

    @Test
    public void overridesContent() {
        HttpResponse response = response()
                .body("Response content.")
                .body(" ")
                .body("Extra content")
                .build();

        assertThat(bodyAsString(response.body()), is("Extra content"));
        assertThat(response.contentLength().get(), is(contentLength("Extra content")));
    }

    @Test
    public void addsHeaderValue() {
        HttpResponse response = response()
                .header("name", "value1")
                .addHeader("name", "value2")
                .build();

        assertThat(response.headers(), hasItem(header("name", "value1")));
        assertThat(response.headers(), hasItem(header("name", "value2")));
    }

    @Test
    public void canSetObservableAsContent() {
        Observable<ByteBuf> content = just(buf("One"), buf("Two"), buf("Three"));
        HttpResponse response = response()
                .body(content)
                .build();

        assertThat(bodyAsString(response.body()), is("OneTwoThree"));
    }

    @Test(dataProvider = "responses")
    public void shouldCheckIfCurrentResponseIsARedirectToOtherResource(io.netty.handler.codec.http.HttpResponseStatus status, boolean isRedirect) {
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
                .validateContentLength()
                .build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsMultipleContentLength() throws Exception {
        response()
                .addHeader(CONTENT_LENGTH, "15")
                .addHeader(CONTENT_LENGTH, "16")
                .validateContentLength()
                .build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsInvalidContentLength() throws Exception {
        response()
                .addHeader(CONTENT_LENGTH, "foo")
                .validateContentLength()
                .build();

    }

    @Test
    public void allowsModificationOfHeadersBasedOnBody() {
        HttpResponse response = response()
                .body(just(buf("foo"), buf("bar")))
                .build();

        assertThat(response.header(CONTENT_LENGTH), isAbsent());

        Observable<HttpResponse> newResponseObservable = response.decode(utf8Decoder, 100)
                .map(aggregatedResponse -> aggregatedResponse.responseBuilder()
                        .header(CONTENT_LENGTH, aggregatedResponse.body().length())
                        .body(aggregatedResponse.body())
                        .build());

        HttpResponse newResponse = newResponseObservable.toBlocking().first();

        assertThat(newResponse.header(CONTENT_LENGTH), isValue("6"));
        assertThat(bodyAsString(newResponse.body()), is("foobar"));
    }

    @Test
    public void allowsModificationOfBodyBasedOnExistingBody() {
        HttpResponse response = response()
                .body(just(buf("foo"), buf("bar")))
                .build();

        Observable<HttpResponse> newResponseObservable = response.decode(utf8Decoder, 100)
                .map(aggregatedResponse -> aggregatedResponse.responseBuilder()
                        .body(aggregatedResponse.body() + "x")
                        .build());

        HttpResponse newResponse = newResponseObservable.toBlocking().first();

        assertThat(bodyAsString(newResponse.body()), is("foobarx"));
    }


    @Test
    public void decodesToFullHttpResponse() throws Exception {
        HttpResponse request = response(CREATED)
                .version(HTTP_1_0)
                .header("HeaderName", "HeaderValue")
                .addCookie("CookieName", "CookieValue")
                .body("foobar")
                .body(stream("foo", "bar", "baz"))
                .build();

        FullHttpResponse full = request.toFullResponse(0x100000)
                .toBlocking()
                .single();

        assertThat(full.version(), is(HttpVersion.HTTP_1_0));
        assertThat(full.status(), is(HttpResponseStatus.CREATED));
        assertThat(full.headers(), hasItem(header("HeaderName", "HeaderValue")));
        assertThat(full.cookies(), contains(cookie("CookieName", "CookieValue")));
        assertThat(full.bodyAs(UTF_8), is("foobarbaz"));
    }

    @Test
    public void decodesToFullHttpResponseWithEmptyBody() {
        HttpResponse request = response(CREATED)
                .body(empty())
                .build();

        FullHttpResponse full = request.toFullResponse(0x100000)
                .toBlocking()
                .single();

        assertThat(full.status(), is(HttpResponseStatus.CREATED));
        assertThat(full.bodyAs(UTF_8), is(""));
    }

    @Test
    public void decodingToFullHttpResponseDefaultsToUTF8() {
        HttpResponse request = response(CREATED)
                .body(stream("foo", "bar", "baz"))
                .build();

        FullHttpResponse full = request.toFullResponse(0x100000)
                .toBlocking()
                .single();

        assertThat(full.status(), is(HttpResponseStatus.CREATED));
        assertThat(full.bodyAs(UTF_8), is("foobarbaz"));
    }

    private static Observable<ByteBuf> stream(String... strings) {
        return Observable.from(Stream.of(strings)
                .map(string -> Unpooled.copiedBuffer(string, StandardCharsets.UTF_8))
                .collect(toList()));
    }

    private static Function<ByteBuf, String> toStringDecoder(Charset charset) {
        return byteBuf -> byteBuf.toString(charset);
    }

    private final Function<ByteBuf, String> utf8Decoder = toStringDecoder(Charsets.UTF_8);

    private byte[] bytes(String content) {
        return content.getBytes(UTF_8);
    }

    private static ByteBuf buf(String string) {
        return copiedBuffer(string, UTF_8);
    }

    private static int contentLength(String content) {
        return content.getBytes().length;
    }
}
