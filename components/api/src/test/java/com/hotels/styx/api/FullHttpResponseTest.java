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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import rx.observers.TestSubscriber;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static com.hotels.styx.api.FullHttpResponse.response;
import static com.hotels.styx.api.HttpHeader.header;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.ResponseCookie.responseCookie;
import static com.hotels.styx.api.matchers.HttpHeadersMatcher.isNotCacheable;
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
import static com.hotels.styx.api.HttpVersion.HTTP_1_1;
import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
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
    public void convertsToStreamingHttpResponse() throws Exception {
        FullHttpResponse response = response(CREATED)
                .version(HTTP_1_1)
                .header("HeaderName", "HeaderValue")
                .cookies(responseCookie("CookieName", "CookieValue").build())
                .body("message content", UTF_8)
                .build();

        HttpResponse streaming = response.toStreamingResponse();

        assertThat(streaming.version(), is(HTTP_1_1));
        assertThat(streaming.status(), is(CREATED));
        assertThat(streaming.headers(), containsInAnyOrder(
                header("Content-Length", "15"),
                header("HeaderName", "HeaderValue"),
                header("Set-Cookie", "CookieName=CookieValue")
        ));
        assertThat(streaming.cookies(), contains(responseCookie("CookieName", "CookieValue").build()));

        String body = streaming.toFullResponse(0x100000)
                .asCompletableFuture()
                .get()
                .bodyAs(UTF_8);

        assertThat(body, is("message content"));
    }

    @Test
    public void createsAResponseWithDefaultValues() {
        FullHttpResponse response = FullHttpResponse.response().build();
        assertThat(response.version(), is(HTTP_1_1));
        assertThat(response.cookies(), is(emptyIterable()));
        assertThat(response.headers(), is(emptyIterable()));
        assertThat(response.body().length, is(0));
    }

    @Test
    public void createsResponseWithMinimalInformation() {
        FullHttpResponse response = FullHttpResponse.response()
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
        FullHttpResponse response = FullHttpResponse.response()
                .cookies(responseCookie("user", "QSplbl9HX1VL").domain(".hotels.com").path("/").maxAge(3600).build())
                .build();

        assertThat(response.cookie("user"), isValue(responseCookie("user", "QSplbl9HX1VL").domain(".hotels.com").path("/").maxAge(3600).build()));
    }

    @Test
    public void setsMultipleOutboundCookies() {
        FullHttpResponse response = FullHttpResponse.response()
                .cookies(
                        responseCookie("a", "b").build(),
                        responseCookie("c", "d").build())
                .build();

        Set<ResponseCookie> cookies = response.cookies();

        assertThat(cookies, containsInAnyOrder(
                responseCookie("a", "b").build(),
                responseCookie("c", "d").build()
        ));
    }

    @Test
    public void getASingleCookieValue() {
        FullHttpResponse response = FullHttpResponse.response()
                .cookies(
                        responseCookie("a", "b").build(),
                        responseCookie("c", "d").build())
                .build();

        assertThat(response.cookie("c"), isValue(responseCookie("c", "d").build()));
    }

    @Test
    public void canRemoveAHeader() {
        Object headerValue = "b";
        FullHttpResponse response = FullHttpResponse.response()
                .header("a", headerValue)
                .addHeader("c", headerValue)
                .build();
        FullHttpResponse shouldRemoveHeader = response.newBuilder()
                .removeHeader("c")
                .build();

        assertThat(shouldRemoveHeader.headers(), contains(header("a", "b")));
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
        assertThat(FullHttpResponse.response().disableCaching().build().headers(), is(isNotCacheable()));
    }

    @Test
    public void shouldCreateAChunkedResponse() {
        assertThat(FullHttpResponse.response().build().chunked(), is(false));
        assertThat(FullHttpResponse.response().setChunked().build().chunked(), is(true));
    }

    @Test
    public void shouldRemoveContentLengthFromChunkedMessages() {
        FullHttpResponse response = FullHttpResponse.response().header(CONTENT_LENGTH, 5).build();
        FullHttpResponse chunkedResponse = response.newBuilder().setChunked().build();

        assertThat(chunkedResponse.chunked(), is(true));
        assertThat(chunkedResponse.header(CONTENT_LENGTH).isPresent(), is(false));
    }

    @Test
    public void shouldNotFailToRemoveNonExistentContentLength() {
        FullHttpResponse response = FullHttpResponse.response().build();
        FullHttpResponse chunkedResponse = response.newBuilder().setChunked().build();

        assertThat(chunkedResponse.chunked(), is(true));
        assertThat(chunkedResponse.header(CONTENT_LENGTH).isPresent(), is(false));
    }

    @Test
    public void addsHeaderValue() {
        FullHttpResponse response = FullHttpResponse.response()
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
        FullHttpResponse.response().cookies((ResponseCookie) null).build();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void rejectsNullCookieName() {
        FullHttpResponse.response().cookies(responseCookie(null, "value").build()).build();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void rejectsNullCookieValue() {
        FullHttpResponse.response().cookies(responseCookie("name", null).build()).build();
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
        FullHttpResponse.response()
                .addHeader(CONTENT_LENGTH, "15, 16")
                .ensureContentLengthIsValid()
                .build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsMultipleContentLength() {
        FullHttpResponse.response()
                .addHeader(CONTENT_LENGTH, "15")
                .addHeader(CONTENT_LENGTH, "16")
                .ensureContentLengthIsValid()
                .build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsInvalidContentLength() {
        FullHttpResponse.response()
                .addHeader(CONTENT_LENGTH, "foo")
                .ensureContentLengthIsValid()
                .build();
    }

    @Test
    public void allowsModificationOfHeadersBasedOnBody() {
        FullHttpResponse response = FullHttpResponse.response()
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
        FullHttpResponse response = FullHttpResponse.response()
                .body("foobar", UTF_8)
                .build();

        FullHttpResponse newResponse = response.newBuilder()
                .body(response.bodyAs(UTF_8) + "x", UTF_8)
                .build();

        assertThat(newResponse.bodyAs(UTF_8), is("foobarx"));
    }

    @Test
    public void overridesContent() {
        FullHttpResponse response = FullHttpResponse.response()
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

        ((StyxCoreObservable<ByteBuf>) streaming.body()).delegate().subscribe(subscriber);

        assertThat(subscriber.getOnNextEvents().size(), is(0));
        subscriber.assertCompleted();
    }

    // We want to ensure that these are all considered equivalent
    @DataProvider(name = "emptyBodyResponses")
    private Object[][] emptyBodyResponses() {
        return new Object[][]{
                {FullHttpResponse.response()
                        .build()},
                {FullHttpResponse.response()
                        .body(null, UTF_8)
                        .build()},
                {FullHttpResponse.response()
                        .body("", UTF_8)
                        .build()},
                {FullHttpResponse.response()
                        .body(null, UTF_8, true)
                        .build()},
                {FullHttpResponse.response()
                        .body("", UTF_8, true)
                        .build()},
                {FullHttpResponse.response()
                        .body(null, true)
                        .build()},
                {FullHttpResponse.response()
                        .body(new byte[0], true)
                        .build()},
        };
    }

    @Test
    public void encodesBodyWithGivenCharset() {
        FullHttpResponse response = FullHttpResponse.response()
                .body("Response content.", UTF_16, true)
                .build();

        assertThat(response.body().length, is(36));
    }

    @Test(expectedExceptions = NullPointerException.class, expectedExceptionsMessageRegExp = "Charset is not provided.")
    public void contentFromStringOnlyThrowsNPEWhenCharsetIsNull() {
        FullHttpResponse.response()
                .body("Response content.", null)
                .build();
    }

    @Test
    public void contentFromStringSetsContentLengthIfRequired() {
        FullHttpResponse response1 = FullHttpResponse.response()
                .body("Response content.", UTF_8, true)
                .build();

        assertThat(response1.header("Content-Length"), is(Optional.of("17")));

        FullHttpResponse response2 = FullHttpResponse.response()
                .body("Response content.", UTF_8, false)
                .build();

        assertThat(response2.header("Content-Length"), is(Optional.empty()));
    }

    @Test(expectedExceptions = NullPointerException.class, expectedExceptionsMessageRegExp = "Charset is not provided.")
    public void contentFromStringThrowsNPEWhenCharsetIsNull() {
        FullHttpResponse.response()
                .body("Response content.", null, false)
                .build();
    }

    @Test
    public void contentFromByteArraySetsContentLengthIfRequired() {
        FullHttpResponse response1 = FullHttpResponse.response()
                .body("Response content.".getBytes(UTF_16), true)
                .build();
        assertThat(response1.body(), is("Response content.".getBytes(UTF_16)));
        assertThat(response1.header("Content-Length"), is(Optional.of("36")));

        FullHttpResponse response2 = FullHttpResponse.response()
                .body("Response content.".getBytes(UTF_8), false)
                .build();

        assertThat(response2.body(), is("Response content.".getBytes(UTF_8)));
        assertThat(response2.header("Content-Length"), is(Optional.empty()));
    }

    @Test
    public void responseBodyIsImmutable() {
        FullHttpResponse response = response(OK)
                .body("Original body", UTF_8)
                .build();

        response.body()[0] = 'A';

        assertThat(response.bodyAs(UTF_8), is("Original body"));
    }

    @Test
    public void responseBodyCannotBeChangedViaStreamingMessage() {
        FullHttpResponse original = response(OK)
                .body("original", UTF_8)
                .build();

        ByteBuf byteBuf = ((StyxCoreObservable<ByteBuf>) original.toStreamingResponse().body())
                .delegate()
                .toBlocking()
                .first();

        byteBuf.array()[0] = 'A';

        assertThat(original.bodyAs(UTF_8), is("original"));
    }

    @Test(expectedExceptions = io.netty.util.IllegalReferenceCountException.class)
    public void toFullResponseReleasesOriginalRefCountedBuffers() throws ExecutionException, InterruptedException {
        ByteBuf content = Unpooled.copiedBuffer("original", UTF_8);

        HttpResponse original = HttpResponse.response(OK)
                .body(StyxObservable.of(content))
                .build();

        original.toFullResponse(100)
                .asCompletableFuture()
                .get();

        content.array()[0] = 'A';
    }

    @Test
    public void transformedBodyIsNewCopy() {
        FullHttpResponse request = response()
                .body("Original body", UTF_8)
                .build();

        FullHttpResponse newRequest = response()
                .body("New body", UTF_8)
                .build();

        assertThat(request.bodyAs(UTF_8), is("Original body"));
        assertThat(newRequest.bodyAs(UTF_8), is("New body"));
    }

    @Test
    public void addsCookies() {
        FullHttpResponse response = response()
                .addCookies(responseCookie("x", "x1").build(), responseCookie("y", "y1").build())
                .build();

        assertThat(response.cookies(), containsInAnyOrder(responseCookie("x", "x1").build(), responseCookie("y", "y1").build()));
    }

    @Test
    public void addsCookiesToExistingCookies() {
        FullHttpResponse response = response()
                .addCookies(responseCookie("z", "z1").build())
                .addCookies(responseCookie("x", "x1").build(), responseCookie("y", "y1").build())
                .build();

        assertThat(response.cookies(), containsInAnyOrder(responseCookie("x", "x1").build(), responseCookie("y", "y1").build(), responseCookie("z", "z1").build()));
    }

    @Test
    public void newCookiesWithDuplicateNamesOverridePreviousOnes() {
        FullHttpResponse r1 = response()
                .cookies(responseCookie("y", "y1").build())
                .build();

        FullHttpResponse r2 = r1.newBuilder().addCookies(
                responseCookie("y", "y2").build())
                .build();

        assertThat(r2.cookies(), containsInAnyOrder(responseCookie("y", "y2").build()));
    }

    @Test
    public void removesCookies() {
        FullHttpResponse r1 = response()
                .addCookies(responseCookie("x", "x1").build(), responseCookie("y", "y1").build())
                .build();

        FullHttpResponse r2 = r1.newBuilder()
                .removeCookies("x")
                .removeCookies("foo") // ensure that trying to remove a non-existent cookie does not cause Exception
                .build();

        assertThat(r2.cookies(), contains(responseCookie("y", "y1").build()));
    }

    @Test
    public void removesCookiesInSameBuilder() {
        FullHttpResponse r1 = response()
                .addCookies(responseCookie("x", "x1").build())
                .removeCookies("x")
                .build();

        assertThat(r1.cookie("x"), isAbsent());
    }
}