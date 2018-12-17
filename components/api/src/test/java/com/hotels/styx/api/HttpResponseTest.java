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

import io.netty.buffer.Unpooled;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static com.hotels.styx.api.HttpHeader.header;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpResponse.response;
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
import static com.hotels.styx.api.ResponseCookie.responseCookie;
import static com.hotels.styx.api.matchers.HttpHeadersMatcher.isNotCacheable;
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

public class HttpResponseTest {
    @Test
    public void convertsToStreamingHttpResponse() throws Exception {
        HttpResponse response = response(CREATED)
                .version(HTTP_1_1)
                .header("HeaderName", "HeaderValue")
                .cookies(responseCookie("CookieName", "CookieValue").build())
                .body("message content", UTF_8)
                .build();

        LiveHttpResponse streaming = response.stream();

        assertThat(streaming.version(), is(HTTP_1_1));
        assertThat(streaming.status(), is(CREATED));
        assertThat(streaming.headers(), containsInAnyOrder(
                header("Content-Length", "15"),
                header("HeaderName", "HeaderValue"),
                header("Set-Cookie", "CookieName=CookieValue")
        ));
        assertThat(streaming.cookies(), contains(responseCookie("CookieName", "CookieValue").build()));

        StepVerifier.create(streaming.aggregate(0x100000).map(it -> it.bodyAs(UTF_8)))
                .expectNext("message content")
                .verifyComplete();
    }

    @Test
    public void createsAResponseWithDefaultValues() {
        HttpResponse response = HttpResponse.response().build();
        assertThat(response.version(), is(HTTP_1_1));
        assertThat(response.cookies(), is(emptyIterable()));
        assertThat(response.headers(), is(emptyIterable()));
        assertThat(response.body().length, is(0));
    }

    @Test
    public void createsResponseWithMinimalInformation() {
        HttpResponse response = HttpResponse.response()
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
        HttpResponse response = HttpResponse.response()
                .cookies(responseCookie("user", "QSplbl9HX1VL").domain(".hotels.com").path("/").maxAge(3600).build())
                .build();

        assertThat(response.cookie("user"), isValue(responseCookie("user", "QSplbl9HX1VL").domain(".hotels.com").path("/").maxAge(3600).build()));
    }

    @Test
    public void setsMultipleOutboundCookies() {
        HttpResponse response = HttpResponse.response()
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
        HttpResponse response = HttpResponse.response()
                .cookies(
                        responseCookie("a", "b").build(),
                        responseCookie("c", "d").build())
                .build();

        assertThat(response.cookie("c"), isValue(responseCookie("c", "d").build()));
    }

    @Test
    public void canRemoveAHeader() {
        Object headerValue = "b";
        HttpResponse response = HttpResponse.response()
                .header("a", headerValue)
                .addHeader("c", headerValue)
                .build();
        HttpResponse shouldRemoveHeader = response.newBuilder()
                .removeHeader("c")
                .build();

        assertThat(shouldRemoveHeader.headers(), contains(header("a", "b")));
    }

    @Test
    public void canRemoveResponseBody() {
        HttpResponse response = response(NO_CONTENT)
                .body("shouldn't be here", UTF_8)
                .build();

        HttpResponse shouldClearBody = response.newBuilder()
                .body("", UTF_8)
                .build();

        assertThat(shouldClearBody.bodyAs(UTF_8), is(""));
    }

    @Test
    public void supportsCaseInsensitiveHeaderNames() {
        HttpResponse response = response(OK).header("Content-Type", "text/plain").build();
        assertThat(response.header("content-type"), isValue("text/plain"));
    }

    @Test
    public void headerValuesAreCaseSensitive() {
        HttpResponse response = response(OK).header("Content-Type", "TEXT/PLAIN").build();
        assertThat(response.header("content-type"), not(isValue("text/plain")));
    }

    @Test
    public void createsANonCacheableResponse() {
        assertThat(HttpResponse.response().disableCaching().build().headers(), is(isNotCacheable()));
    }

    @Test
    public void shouldCreateAChunkedResponse() {
        assertThat(HttpResponse.response().build().chunked(), is(false));
        assertThat(HttpResponse.response().setChunked().build().chunked(), is(true));
    }

    @Test
    public void shouldRemoveContentLengthFromChunkedMessages() {
        HttpResponse response = HttpResponse.response().header(CONTENT_LENGTH, 5).build();
        HttpResponse chunkedResponse = response.newBuilder().setChunked().build();

        assertThat(chunkedResponse.chunked(), is(true));
        assertThat(chunkedResponse.header(CONTENT_LENGTH).isPresent(), is(false));
    }

    @Test
    public void shouldNotFailToRemoveNonExistentContentLength() {
        HttpResponse response = HttpResponse.response().build();
        HttpResponse chunkedResponse = response.newBuilder().setChunked().build();

        assertThat(chunkedResponse.chunked(), is(true));
        assertThat(chunkedResponse.header(CONTENT_LENGTH).isPresent(), is(false));
    }

    @Test
    public void addsHeaderValue() {
        HttpResponse response = HttpResponse.response()
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
        HttpResponse.response().cookies((ResponseCookie) null).build();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void rejectsNullCookieName() {
        HttpResponse.response().cookies(responseCookie(null, "value").build()).build();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void rejectsNullCookieValue() {
        HttpResponse.response().cookies(responseCookie("name", null).build()).build();
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
        HttpResponse.response()
                .addHeader(CONTENT_LENGTH, "15, 16")
                .ensureContentLengthIsValid()
                .build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsMultipleContentLength() {
        HttpResponse.response()
                .addHeader(CONTENT_LENGTH, "15")
                .addHeader(CONTENT_LENGTH, "16")
                .ensureContentLengthIsValid()
                .build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsInvalidContentLength() {
        HttpResponse.response()
                .addHeader(CONTENT_LENGTH, "foo")
                .ensureContentLengthIsValid()
                .build();
    }

    @Test
    public void allowsModificationOfHeadersBasedOnBody() {
        HttpResponse response = HttpResponse.response()
                .body("foobar", UTF_8)
                .build();

        assertThat(response.header("some-header"), isAbsent());

        HttpResponse newResponse = response.newBuilder()
                .header("some-header", response.body().length)
                .build();

        assertThat(newResponse.header("some-header"), isValue("6"));
        assertThat(newResponse.bodyAs(UTF_8), is("foobar"));
    }

    @Test
    public void allowsModificationOfBodyBasedOnExistingBody() {
        HttpResponse response = HttpResponse.response()
                .body("foobar", UTF_8)
                .build();

        HttpResponse newResponse = response.newBuilder()
                .body(response.bodyAs(UTF_8) + "x", UTF_8)
                .build();

        assertThat(newResponse.bodyAs(UTF_8), is("foobarx"));
    }

    @Test
    public void overridesContent() {
        HttpResponse response = HttpResponse.response()
                .body("Response content.", UTF_8)
                .body(" ", UTF_8)
                .body("Extra content", UTF_8)
                .build();

        assertThat(response.bodyAs(UTF_8), is("Extra content"));
    }

    @Test(dataProvider = "emptyBodyResponses")
    public void convertsToStreamingHttpResponseWithEmptyBody(HttpResponse response) throws ExecutionException, InterruptedException {
        LiveHttpResponse streaming = response.stream();

        byte[] result = streaming.body().aggregate(1000)
                .get()
                .content();

        assertThat(result.length, is(0));
    }

    // We want to ensure that these are all considered equivalent
    @DataProvider(name = "emptyBodyResponses")
    private Object[][] emptyBodyResponses() {
        return new Object[][]{
                {HttpResponse.response()
                        .build()},
                {HttpResponse.response()
                        .body(null, UTF_8)
                        .build()},
                {HttpResponse.response()
                        .body("", UTF_8)
                        .build()},
                {HttpResponse.response()
                        .body(null, UTF_8, true)
                        .build()},
                {HttpResponse.response()
                        .body("", UTF_8, true)
                        .build()},
                {HttpResponse.response()
                        .body(null, true)
                        .build()},
                {HttpResponse.response()
                        .body(new byte[0], true)
                        .build()},
        };
    }

    @Test
    public void encodesBodyWithGivenCharset() {
        HttpResponse response = HttpResponse.response()
                .body("Response content.", UTF_16, true)
                .build();

        assertThat(response.body().length, is(36));
    }

    @Test(expectedExceptions = NullPointerException.class, expectedExceptionsMessageRegExp = "Charset is not provided.")
    public void contentFromStringOnlyThrowsNPEWhenCharsetIsNull() {
        HttpResponse.response()
                .body("Response content.", null)
                .build();
    }

    @Test
    public void contentFromStringSetsContentLengthIfRequired() {
        HttpResponse response1 = HttpResponse.response()
                .body("Response content.", UTF_8, true)
                .build();

        assertThat(response1.header("Content-Length"), is(Optional.of("17")));

        HttpResponse response2 = HttpResponse.response()
                .body("Response content.", UTF_8, false)
                .build();

        assertThat(response2.header("Content-Length"), is(Optional.empty()));
    }

    @Test(expectedExceptions = NullPointerException.class, expectedExceptionsMessageRegExp = "Charset is not provided.")
    public void contentFromStringThrowsNPEWhenCharsetIsNull() {
        HttpResponse.response()
                .body("Response content.", null, false)
                .build();
    }

    @Test
    public void contentFromByteArraySetsContentLengthIfRequired() {
        HttpResponse response1 = HttpResponse.response()
                .body("Response content.".getBytes(UTF_16), true)
                .build();
        assertThat(response1.body(), is("Response content.".getBytes(UTF_16)));
        assertThat(response1.header("Content-Length"), is(Optional.of("36")));

        HttpResponse response2 = HttpResponse.response()
                .body("Response content.".getBytes(UTF_8), false)
                .build();

        assertThat(response2.body(), is("Response content.".getBytes(UTF_8)));
        assertThat(response2.header("Content-Length"), is(Optional.empty()));
    }

    @Test
    public void responseBodyIsImmutable() {
        HttpResponse response = response(OK)
                .body("Original body", UTF_8)
                .build();

        response.body()[0] = 'A';

        assertThat(response.bodyAs(UTF_8), is("Original body"));
    }

    @Test
    public void responseBodyCannotBeChangedViaStreamingMessage() {
        HttpResponse original = response(OK)
                .body("original", UTF_8)
                .build();

        Flux.from(original.stream()
                .body()
                .map(buf -> {
                    buf.delegate().array()[0] = 'A';
                    return buf;
                }))
                .subscribe();

        assertThat(original.bodyAs(UTF_8), is("original"));
    }

    @Test
    public void toFullResponseReleasesOriginalRefCountedBuffers() throws ExecutionException, InterruptedException {
        Buffer content = new Buffer(Unpooled.copiedBuffer("original", UTF_8));

        LiveHttpResponse original = LiveHttpResponse.response(OK)
                .body(new ByteStream(Flux.just(content)))
                .build();

        StepVerifier.create(original.aggregate(100))
                .expectNextCount(1)
                .then(() -> assertThat(content.delegate().refCnt(), is(0)))
                .verifyComplete();
    }

    @Test
    public void transformedBodyIsNewCopy() {
        HttpResponse request = response()
                .body("Original body", UTF_8)
                .build();

        HttpResponse newRequest = response()
                .body("New body", UTF_8)
                .build();

        assertThat(request.bodyAs(UTF_8), is("Original body"));
        assertThat(newRequest.bodyAs(UTF_8), is("New body"));
    }

    @Test
    public void addsCookies() {
        HttpResponse response = response()
                .addCookies(responseCookie("x", "x1").build(), responseCookie("y", "y1").build())
                .build();

        assertThat(response.cookies(), containsInAnyOrder(responseCookie("x", "x1").build(), responseCookie("y", "y1").build()));
    }

    @Test
    public void addsCookiesToExistingCookies() {
        HttpResponse response = response()
                .addCookies(responseCookie("z", "z1").build())
                .addCookies(responseCookie("x", "x1").build(), responseCookie("y", "y1").build())
                .build();

        assertThat(response.cookies(), containsInAnyOrder(responseCookie("x", "x1").build(), responseCookie("y", "y1").build(), responseCookie("z", "z1").build()));
    }

    @Test
    public void newCookiesWithDuplicateNamesOverridePreviousOnes() {
        HttpResponse r1 = response()
                .cookies(responseCookie("y", "y1").build())
                .build();

        HttpResponse r2 = r1.newBuilder().addCookies(
                responseCookie("y", "y2").build())
                .build();

        assertThat(r2.cookies(), containsInAnyOrder(responseCookie("y", "y2").build()));
    }

    @Test
    public void removesCookies() {
        HttpResponse r1 = response()
                .addCookies(responseCookie("x", "x1").build(), responseCookie("y", "y1").build())
                .build();

        HttpResponse r2 = r1.newBuilder()
                .removeCookies("x")
                .removeCookies("foo") // ensure that trying to remove a non-existent cookie does not cause Exception
                .build();

        assertThat(r2.cookies(), contains(responseCookie("y", "y1").build()));
    }

    @Test
    public void removesCookiesInSameBuilder() {
        HttpResponse r1 = response()
                .addCookies(responseCookie("x", "x1").build())
                .removeCookies("x")
                .build();

        assertThat(r1.cookie("x"), isAbsent());
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Invalid Content-Length found. -3")
    public void ensuresContentLengthIsPositive() {
        response()
                .header("Content-Length", -3)
                .build();
    }
}