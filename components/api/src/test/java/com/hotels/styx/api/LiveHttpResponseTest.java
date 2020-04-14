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
package com.hotels.styx.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static com.hotels.styx.api.Collections.listOf;
import static com.hotels.styx.api.HttpHeader.header;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY;
import static com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST;
import static com.hotels.styx.api.HttpResponseStatus.CREATED;
import static com.hotels.styx.api.HttpResponseStatus.GATEWAY_TIMEOUT;
import static com.hotels.styx.api.HttpResponseStatus.MOVED_PERMANENTLY;
import static com.hotels.styx.api.HttpResponseStatus.MULTIPLE_CHOICES;
import static com.hotels.styx.api.HttpResponseStatus.NO_CONTENT;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.HttpResponseStatus.SEE_OTHER;
import static com.hotels.styx.api.HttpResponseStatus.TEMPORARY_REDIRECT;
import static com.hotels.styx.api.HttpVersion.HTTP_1_0;
import static com.hotels.styx.api.HttpVersion.HTTP_1_1;
import static com.hotels.styx.api.ResponseCookie.responseCookie;
import static com.hotels.styx.api.matchers.HttpHeadersMatcher.isNotCacheable;
import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static io.netty.buffer.Unpooled.copiedBuffer;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LiveHttpResponseTest {

    @Test
    public void encodesToFullHttpResponse() {
        LiveHttpResponse response = response(CREATED)
                .version(HTTP_1_0)
                .header("HeaderName", "HeaderValue")
                .cookies(responseCookie("CookieName", "CookieValue").build())
                .body(new ByteStream(Flux.just("foo", "bar").map(it -> new Buffer(copiedBuffer(it, UTF_8)))))
                .build();

        HttpResponse full = Mono.from(response.aggregate(0x1000)).block();

        assertThat(full.status(), is(CREATED));
        assertThat(full.version(), is(HTTP_1_0));
        assertThat(full.headers(), containsInAnyOrder(header("HeaderName", "HeaderValue"), header("Set-Cookie", "CookieName=CookieValue")));
        assertThat(full.cookies(), contains(responseCookie("CookieName", "CookieValue").build()));

        assertThat(full.body(), is(bytes("foobar")));
    }

    @ParameterizedTest
    @MethodSource("emptyBodyResponses")
    public void encodesToFullHttpResponseWithEmptyBody(LiveHttpResponse response) throws Exception {
        HttpResponse full = Mono.from(response.aggregate(0x1000)).block();
        assertThat(full.body(), is(new byte[0]));
    }

    // We want to ensure that these are all considered equivalent
    private static Stream<Arguments> emptyBodyResponses() {
        return Stream.of(
                Arguments.of(response().build()),
                Arguments.of(response().body(new ByteStream(Flux.empty())).build())
        );
    }

    @Test
    public void createsAResponseWithDefaultValues() throws Exception {
        LiveHttpResponse response = response().build();
        assertThat(response.version(), is(HTTP_1_1));
        assertThat(response.cookies(), is(emptyIterable()));
        assertThat(response.headers(), is(emptyIterable()));
        assertThat(bytesToString(response.body()), is(""));
    }
    @Test
    public void badSetCookieHeaderDoesNotNpe() throws Exception {
        LiveHttpResponse response = response().header("Set-cookie","").build();
        assertThat(response.cookies().size(), is(0));
    }

    @Test
    public void createsResponseWithMinimalInformation() throws Exception {
        LiveHttpResponse response = response()
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
        LiveHttpResponse response = response()
                .cookies(responseCookie("user", "QSplbl9HX1VL").domain(".hotels.com").path("/").maxAge(3600).build())
                .build();

        assertThat(response.cookie("user"), isValue(responseCookie("user", "QSplbl9HX1VL").domain(".hotels.com").path("/").maxAge(3600).build()));
    }

    @Test
    public void setsMultipleOutboundCookies() {
        LiveHttpResponse response = response()
                .cookies(
                        responseCookie("a", "b").build(),
                        responseCookie("c", "d").build())
                .build();

        Set<ResponseCookie> cookies = response.cookies();

        assertThat(cookies, containsInAnyOrder(
                responseCookie("a", "b").build(),
                responseCookie("c", "d").build()));
    }

    @Test
    public void getASingleCookieValue() {
        LiveHttpResponse response = response()
                .cookies(
                        responseCookie("a", "b").build(),
                        responseCookie("c", "d").build())
                .build();

        assertThat(response.cookie("c"), isValue(responseCookie("c", "d").build()));
    }

    @Test
    public void canRemoveAHeader() {
        Object headerValue = "b";
        LiveHttpResponse response = response()
                .header("a", headerValue)
                .addHeader("c", headerValue)
                .build();
        LiveHttpResponse shouldRemoveHeader = response.newBuilder()
                .removeHeader("c")
                .build();

        assertThat(shouldRemoveHeader.headers(), contains(header("a", "b")));
    }

    @Test
    public void supportsCaseInsensitiveHeaderNames() {
        LiveHttpResponse response = response(OK).header("Content-Type", "text/plain").build();
        assertThat(response.header("content-type"), isValue("text/plain"));
    }

    @Test
    public void headerValuesAreCaseSensitive() {
        LiveHttpResponse response = response(OK).header("Content-Type", "TEXT/PLAIN").build();
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
        LiveHttpResponse response = response().header(CONTENT_LENGTH, 5).build();
        LiveHttpResponse chunkedResponse = response.newBuilder().setChunked().build();

        assertThat(chunkedResponse.chunked(), is(true));
        assertThat(chunkedResponse.header(CONTENT_LENGTH).isPresent(), is(false));
    }

    @Test
    public void shouldNotFailToRemoveNonExistentContentLength() {
        LiveHttpResponse response = response().build();
        LiveHttpResponse chunkedResponse = response.newBuilder().setChunked().build();

        assertThat(chunkedResponse.chunked(), is(true));
        assertThat(chunkedResponse.header(CONTENT_LENGTH).isPresent(), is(false));
    }

    @Test
    public void addsHeaderValue() {
        LiveHttpResponse response = response()
                .header("name", "value1")
                .addHeader("name", "value2")
                .build();

        assertThat(response.headers(), hasItem(header("name", "value1")));
        assertThat(response.headers(), hasItem(header("name", "value2")));
    }

    @ParameterizedTest
    @MethodSource("responses")
    public void shouldCheckIfCurrentResponseIsARedirectToOtherResource(HttpResponseStatus status, boolean isRedirect) {
        assertThat(response(status).build().isRedirect(), is(isRedirect));
    }

    @Test
    public void rejectsNullCookie() {
        assertThrows(NullPointerException.class, () -> response().cookies((ResponseCookie) null).build());
    }

    @Test
    public void rejectsNullCookieName() {
        assertThrows(NullPointerException.class, () -> response().cookies(responseCookie(null, "value").build()).build());
    }

    @Test
    public void rejectsNullCookieValue() {
        assertThrows(NullPointerException.class, () -> response().cookies(responseCookie("name", null).build()).build());
    }

    private static Stream<Arguments> responses() {
        // format: {status, true if redirect}
        return Stream.of(
                Arguments.of(SEE_OTHER, true),
                Arguments.of(TEMPORARY_REDIRECT, true),
                Arguments.of(MULTIPLE_CHOICES, true),
                Arguments.of(MOVED_PERMANENTLY, true),
                Arguments.of(TEMPORARY_REDIRECT, true),
                Arguments.of(OK, false),
                Arguments.of(BAD_REQUEST, false),
                Arguments.of(GATEWAY_TIMEOUT, false),
                Arguments.of(CREATED, false)
        );
    }

    @Test
    public void rejectsMultipleContentLengthInSingleHeader() {
        assertThrows(IllegalArgumentException.class, () -> response()
                .addHeader(CONTENT_LENGTH, "15, 16")
                .ensureContentLengthIsValid()
                .build());
    }

    @Test
    public void rejectsMultipleContentLength() {
        assertThrows(IllegalArgumentException.class, () -> response()
                .addHeader(CONTENT_LENGTH, "15")
                .addHeader(CONTENT_LENGTH, "16")
                .ensureContentLengthIsValid()
                .build());
    }

    @Test
    public void rejectsInvalidContentLength() {
        assertThrows(IllegalArgumentException.class, () -> response()
                .addHeader(CONTENT_LENGTH, "foo")
                .ensureContentLengthIsValid()
                .build());
    }

    @Test
    public void addsCookies() {
        LiveHttpResponse response = response()
                .addCookies(responseCookie("x", "x1").build(), responseCookie("y", "y1").build())
                .build();

        assertThat(response.cookies(), containsInAnyOrder(responseCookie("x", "x1").build(), responseCookie("y", "y1").build()));
    }

    @Test
    public void addsCookiesToExistingCookies() {
        LiveHttpResponse response = response()
                .addCookies(responseCookie("z", "z1").build())
                .addCookies(responseCookie("x", "x1").build(), responseCookie("y", "y1").build())
                .build();

        assertThat(response.cookies(), containsInAnyOrder(responseCookie("x", "x1").build(), responseCookie("y", "y1").build(), responseCookie("z", "z1").build()));
    }

    @Test
    public void newCookiesWithDuplicateNamesOverridePreviousOnes() {
        LiveHttpResponse r1 = response()
                .cookies(responseCookie("y", "y1").build())
                .build();

        LiveHttpResponse r2 = r1.newBuilder().addCookies(
                responseCookie("y", "y2").build())
                .build();

        assertThat(r2.cookies(), containsInAnyOrder(responseCookie("y", "y2").build()));
    }

    @Test
    public void removesCookies() {
        LiveHttpResponse r1 = response()
                .addCookies(responseCookie("x", "x1").build(), responseCookie("y", "y1").build())
                .build();

        LiveHttpResponse r2 = r1.newBuilder()
                .removeCookies("x")
                .removeCookies("foo") // ensure that trying to remove a non-existent cookie does not cause Exception
                .build();

        assertThat(r2.cookies(), contains(responseCookie("y", "y1").build()));
    }

    @Test
    public void removesCookiesInSameBuilder() {
        LiveHttpResponse r1 = response()
                .addCookies(responseCookie("x", "x1").build())
                .removeCookies("x")
                .build();

        assertThat(r1.cookie("x"), isAbsent());
    }

    @Test
    public void consumesBody() {
        Buffer buf1 = new Buffer("foo", UTF_8);
        Buffer buf2 = new Buffer("bar", UTF_8);

        LiveHttpResponse response = response()
                .body(new ByteStream(Flux.just(buf1, buf2)))
                .build();

        response.consume();

        assertEquals(buf1.delegate().refCnt(), 0);
        assertEquals(buf2.delegate().refCnt(), 0);
    }

    @Test
    public void transformsStatus() {
        LiveHttpResponse response = response(OK).build()
                .newBuilder()
                .status(MOVED_PERMANENTLY)
                .build();

        assertEquals(response.status(), MOVED_PERMANENTLY);
    }

    @Test
    public void transformsCookies() {
        LiveHttpResponse response = response().build()
                .newBuilder()
                .cookies(responseCookie("x", "y").build())
                .build();

        assertEquals(response.cookie("x"), Optional.of(responseCookie("x", "y").build()));
    }

    @Test
    public void transformsWithCookieList() {
        LiveHttpResponse response = response().build()
                .newBuilder()
                .cookies(listOf(responseCookie("x", "y").build()))
                .build();

        assertEquals(response.cookie("x"), Optional.of(responseCookie("x", "y").build()));
    }

    @Test
    public void transformerAddsCookies() {
        LiveHttpResponse response = response().build()
                .newBuilder()
                .addCookies(responseCookie("x", "y").build())
                .build();

        assertEquals(response.cookie("x"), Optional.of(responseCookie("x", "y").build()));
    }

    @Test
    public void transformerAddsCookiesList() {
        LiveHttpResponse response = response().build()
                .newBuilder()
                .addCookies(listOf(responseCookie("x", "y").build()))
                .build();

        assertEquals(response.cookie("x"), Optional.of(responseCookie("x", "y").build()));
    }

    @Test
    public void transformerRemovesCookies() {
        LiveHttpResponse response = response()
                .addCookies(listOf(responseCookie("x", "y").build()))
                .build()
                .newBuilder()
                .removeCookies("x")
                .build();

        assertEquals(response.cookie("x"), Optional.empty());
    }

    @Test
    public void transformerRemovesCookiesWithList() {
        LiveHttpResponse response = response()
                .addCookies(listOf(responseCookie("x", "y").build()))
                .build()
                .newBuilder()
                .removeCookies(listOf("x"))
                .build();

        assertEquals(response.cookie("x"), Optional.empty());
    }

    @Test
    public void transformerAddsHeaders() {
        LiveHttpResponse response = response().build()
                .newBuilder()
                .addHeader("X-Styx-ID", "y")
                .build();

        assertEquals(response.header("X-Styx-ID"), Optional.of("y"));
    }

    @Test
    public void transformerRemovesHeaders() {
        LiveHttpResponse response = response().addHeader("X-Styx-ID", "y").build()
                .newBuilder()
                .removeHeader("X-Styx-ID")
                .build();

        assertEquals(response.header("X-Styx-ID"), Optional.empty());
    }

    @Test
    public void transformerSetsHeaders() {
        LiveHttpResponse response = response().build()
                .newBuilder()
                .headers(new HttpHeaders.Builder().add("X-Styx-ID", "z").build())
                .build();

        assertEquals(response.header("X-Styx-ID"), Optional.of("z"));
    }

    @Test
    public void transformsBody() throws ExecutionException, InterruptedException {
        Buffer buffer = new Buffer("I'm going to get removed.", UTF_8);

        LiveHttpResponse response = response(NO_CONTENT)
                .body(new ByteStream(Flux.just(buffer)))
                .build();

        HttpResponse fullResponse = Mono.from(response.newBuilder()
                .body(ByteStream::drop)
                .build()
                .aggregate(1000)).block();

        assertThat(fullResponse.body().length, is(0));
        assertThat(buffer.delegate().refCnt(), is(0));
    }

    @Test
    public void transformerReplacesBody() {
        Buffer buf1 = new Buffer("chunk 1, ", UTF_8);
        Buffer buf2 = new Buffer("chunk 2.", UTF_8);

        LiveHttpResponse response1 = response(NO_CONTENT)
                .body(new ByteStream(Flux.just(buf1, buf2)))
                .build()
                .newBuilder()
                .body(body -> body.replaceWith(ByteStream.from("replacement", UTF_8)))
                .build();

        HttpResponse response2 = Mono.from(response1.aggregate(100)).block();

        assertEquals(response2.bodyAs(UTF_8), "replacement");
        assertEquals(buf1.delegate().refCnt(), 0);
        assertEquals(buf2.delegate().refCnt(), 0);
    }

    @Test
    public void ensuresContentLengthIsPositive() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> response(OK)
                .header("Content-Length", -3)
                .build());
        assertEquals("Invalid Content-Length found. -3", e.getMessage());
    }

    private static LiveHttpResponse.Builder response() {
        return LiveHttpResponse.response();
    }

    private static LiveHttpResponse.Builder response(HttpResponseStatus status) {
        return LiveHttpResponse.response(status);
    }

    private static ByteStream body(String... contents) {
        return new ByteStream(Flux.fromIterable(
                Stream.of(contents)
                        .map(content -> new Buffer(copiedBuffer(content, UTF_8)))
                        .collect(toList())));
    }

    private static String bytesToString(ByteStream body) throws Exception {
        return new String(body.aggregate(100000).get().content(), UTF_8);
    }

    private static byte[] bytes(String s) {
        return s.getBytes(UTF_8);
    }
}