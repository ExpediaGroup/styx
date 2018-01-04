/**
 * Copyright (C) 2013-2018 Expedia Inc.
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
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import rx.Observable;
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
import static com.hotels.styx.api.HttpMessageBody.utf8String;
import static com.hotels.styx.api.matchers.HttpHeadersMatcher.isNotCacheable;
import static com.hotels.styx.api.messages.FullHttpRequest.get;
import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
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
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class FullHttpResponseTest {
    @Test
    public void convertsToStreamingHttpResponse() {
        FullHttpResponse response = response(CREATED)
                .version(HTTP_1_1)
                .header("HeaderName", "HeaderValue")
                .addCookie("CookieName", "CookieValue")
                .body("message content", UTF_8)
                .build();

        HttpResponse streaming = response.toStreamingResponse();

        assertThat(streaming.status(), is(CREATED));
        assertThat(streaming.version(), is(HTTP_1_1));
        assertThat(streaming.headers(), containsInAnyOrder(
                header("Content-Length", "15"),
                header("HeaderName", "HeaderValue")));
        assertThat(streaming.cookies(), contains(cookie("CookieName", "CookieValue")));

        String body = streaming.body()
                .decode(utf8String(), 0x100000)
                .toBlocking()
                .single();

        assertThat(body, is("message content"));
    }

    @Test
    public void createsAResponseWithDefaultValues() {
        FullHttpResponse response = response().build();
        assertThat(response.version(), is(HTTP_1_1));
        assertThat(response.cookies(), is(emptyIterable()));
        assertThat(response.headers(), is(emptyIterable()));
        assertThat(response.body().length, is(0));
    }

    @Test
    public void createsResponseWithMinimalInformation() {
        FullHttpResponse response = response()
                .status(BAD_GATEWAY)
                .version(HTTP_1_1)
                .build();

        assertThat(response.status(), is(BAD_GATEWAY));
        assertThat(response.version(), is(HTTP_1_1));
        assertThat(response.cookies(), is(emptyIterable()));
        assertThat(response.headers(), is(emptyIterable()));
        assertThat(response.body().length, is(0));
    }

    @Test
    public void setsASingleOutboundCookie() {
        FullHttpResponse response = response()
                .addCookie(cookie("user", "QSplbl9HX1VL", domain(".hotels.com"), path("/"), maxAge(3600)))
                .build();

        assertThat(response.cookie("user"), isValue(cookie("user", "QSplbl9HX1VL", domain(".hotels.com"), path("/"), maxAge(3600))));
    }

    @Test
    public void setsMultipleOutboundCookies() {
        FullHttpResponse response = response()
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
        FullHttpResponse response = response()
                .addCookie("a", "b")
                .addCookie("c", "d")
                .build();

        assertThat(response.cookie("c"), isValue(cookie("c", "d")));
    }

    @Test
    public void canRemoveAHeader() {
        Object headerValue = "b";
        FullHttpResponse response = response()
                .header("a", headerValue)
                .addHeader("c", headerValue)
                .build();
        FullHttpResponse shouldRemoveHeader = response.newBuilder()
                .removeHeader("c")
                .build();

        assertThat(shouldRemoveHeader.headers(), contains(header("a", "b")));
    }

    @Test
    public void removesACookie() {
        FullHttpResponse response = new FullHttpResponse.Builder(seeOther("/home"))
                .addCookie(cookie("a", "b"))
                .addCookie(cookie("c", "d"))
                .build();
        FullHttpResponse shouldClearCookie = response.newBuilder()
                .removeCookie("a")
                .build();

        assertThat(shouldClearCookie.cookies(), contains(cookie("c", "d")));
    }

    private static FullHttpResponse seeOther(String newLocation) {
        return response(SEE_OTHER)
                .header(LOCATION, newLocation)
                .build();
    }

    @Test
    public void canRemoveResponseBody() {
        FullHttpResponse response = response(NO_CONTENT)
                .body("shouldn't be here", UTF_8)
                .build();

        FullHttpResponse shouldClearBody = response.newBuilder()
                .body("", UTF_8)
                .build();

        assertThat(shouldClearBody.bodyAs(UTF_8), is(""));
    }

    @Test
    public void supportsCaseInsensitiveHeaderNames() {
        FullHttpResponse response = response(OK).header("Content-Type", "text/plain").build();
        assertThat(response.header("content-type"), isValue("text/plain"));
    }

    @Test
    public void headerValuesAreCaseSensitive() {
        FullHttpResponse response = response(OK).header("Content-Type", "TEXT/PLAIN").build();
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
        FullHttpResponse response = response().header(CONTENT_LENGTH, 5).build();
        FullHttpResponse chunkedResponse = response.newBuilder().setChunked().build();

        assertThat(chunkedResponse.chunked(), is(true));
        assertThat(chunkedResponse.header(CONTENT_LENGTH).isPresent(), is(false));
    }

    @Test
    public void shouldNotFailToRemoveNonExistentContentLength() {
        FullHttpResponse response = response().build();
        FullHttpResponse chunkedResponse = response.newBuilder().setChunked().build();

        assertThat(chunkedResponse.chunked(), is(true));
        assertThat(chunkedResponse.header(CONTENT_LENGTH).isPresent(), is(false));
    }

    @Test
    public void addsHeaderValue() {
        FullHttpResponse response = response()
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

    @Test
    public void allowsModificationOfHeadersBasedOnBody() {
        FullHttpResponse response = response()
                .body("foobar", UTF_8)
                .build();

        assertThat(response.header("some-header"), isAbsent());

        FullHttpResponse newResponse = response.newBuilder()
                .header("some-header", response.body().length)
                .build();

        assertThat(newResponse.header("some-header"), isValue("6"));
        assertThat(newResponse.bodyAs(UTF_8), is("foobar"));
    }

    @Test
    public void allowsModificationOfBodyBasedOnExistingBody() {
        FullHttpResponse response = response()
                .body("foobar", UTF_8)
                .build();

        FullHttpResponse newResponse = response.newBuilder()
                .body(response.bodyAs(UTF_8) + "x", UTF_8)
                .build();

        assertThat(newResponse.bodyAs(UTF_8), is("foobarx"));
    }

    @Test
    public void overridesContent() {
        FullHttpResponse response = response()
                .body("Response content.", UTF_8)
                .body(" ", UTF_8)
                .body("Extra content", UTF_8)
                .build();

        assertThat(response.bodyAs(UTF_8), is("Extra content"));
    }

    @Test(dataProvider = "emptyBodyResponses")
    public void convertsToStreamingHttpResponseWithEmptyBody(FullHttpResponse response) {
        HttpResponse streaming = response.toStreamingResponse();

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
                        .body(null, UTF_8)
                        .build()},
                {response()
                        .body("", UTF_8)
                        .build()},
                {response()
                        .body(null, UTF_8, true)
                        .build()},
                {response()
                        .body("", UTF_8, true)
                        .build()},
                {response()
                        .body(null, true)
                        .build()},
                {response()
                        .body(new byte[0], true)
                        .build()},
        };
    }

    @Test
    public void encodesBodyWithGivenCharset() {
        FullHttpResponse response = response()
                .body("Response content.", UTF_16, true)
                .build();

        assertThat(response.body().length, is(36));
    }

    @Test(expectedExceptions = NullPointerException.class, expectedExceptionsMessageRegExp = "Charset is not provided.")
    public void contentFromStringOnlyThrowsNPEWhenCharsetIsNull() {
        response()
                .body("Response content.", null)
                .build();
    }

    @Test
    public void contentFromStringSetsContentLengthIfRequired() {
        FullHttpResponse response1 = response()
                .body("Response content.", UTF_8, true)
                .build();

        assertThat(response1.header("Content-Length"), is(Optional.of("17")));

        FullHttpResponse response2 = response()
                .body("Response content.", UTF_8, false)
                .build();

        assertThat(response2.header("Content-Length"), is(Optional.empty()));
    }

    @Test(expectedExceptions = NullPointerException.class, expectedExceptionsMessageRegExp = "Charset is not provided.")
    public void contentFromStringThrowsNPEWhenCharsetIsNull() {
        response()
                .body("Response content.", null, false)
                .build();
    }

    @Test
    public void contentFromByteArraySetsContentLengthIfRequired() {
        FullHttpResponse response1 = response()
                .body("Response content.".getBytes(UTF_16), true)
                .build();
        assertThat(response1.body(), is("Response content.".getBytes(UTF_16)));
        assertThat(response1.header("Content-Length"), is(Optional.of("36")));

        FullHttpResponse response2 = response()
                .body("Response content.".getBytes(UTF_8), false)
                .build();

        assertThat(response2.body(), is("Response content.".getBytes(UTF_8)));
        assertThat(response2.header("Content-Length"), is(Optional.empty()));
    }

    @Test
    public void responseBodyIsImmutable() {
        FullHttpResponse response = FullHttpResponse.response(OK)
                .body("Original body", UTF_8)
                .build();

        response.body()[0] = 'A';

        assertThat(response.bodyAs(UTF_8), is("Original body"));
    }

    @Test
    public void responseBodyCannotBeChangedViaStreamingMessage() {
        FullHttpResponse original = FullHttpResponse.response(OK)
                .body("original", UTF_8)
                .build();

        ByteBuf byteBuf = original.toStreamingResponse()
                .body()
                .content()
                .toBlocking()
                .first();

        byteBuf.array()[0] = 'A';

        assertThat(original.bodyAs(UTF_8), is("original"));
    }

    @Test
    public void responseBodyCannotBeChangedViaStreamingMessage2() {
        ByteBuf content = Unpooled.copiedBuffer("original", UTF_8);

        HttpResponse original = HttpResponse.Builder.response(OK)
                .body(content)
                .build();

        FullHttpResponse fullResponse = original.toFullResponse(100)
                .toBlocking()
                .first();

        content.array()[0] = 'A';

        assertThat(fullResponse.bodyAs(UTF_8), is("original"));
    }

    @Test
    public void requestBodyCannotBeChangedViaStreamingRequest3() {
        ByteBuf content = Unpooled.copiedBuffer("original", UTF_8);

        HttpResponse original = HttpResponse.Builder.response(OK)
                .body(Observable.just(content))
                .build();

        FullHttpResponse fullResponse = original.toFullResponse(100)
                .toBlocking()
                .first();

        content.array()[0] = 'A';

        assertThat(fullResponse.bodyAs(UTF_8), is("original"));
    }

    @Test
    public void transformedBodyIsNewCopy() {
        FullHttpRequest request = get("/foo")
                .body("Original body", UTF_8)
                .build();

        FullHttpRequest newRequest = request.newBuilder()
                .body("New body", UTF_8)
                .build();

        assertThat(request.bodyAs(UTF_8), is("Original body"));
        assertThat(newRequest.bodyAs(UTF_8), is("New body"));
    }

    private static FullHttpResponse.Builder response() {
        return FullHttpResponse.response();
    }

    private static FullHttpResponse.Builder response(HttpResponseStatus status) {
        return FullHttpResponse.response(status);
    }

}