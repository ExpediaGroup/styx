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
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.hotels.styx.api.Collections.listOf;
import static com.hotels.styx.api.HttpHeader.header;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpMethod.DELETE;
import static com.hotels.styx.api.HttpMethod.GET;
import static com.hotels.styx.api.HttpMethod.POST;
import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpRequest.patch;
import static com.hotels.styx.api.HttpRequest.put;
import static com.hotels.styx.api.HttpVersion.HTTP_1_0;
import static com.hotels.styx.api.HttpVersion.HTTP_1_1;
import static com.hotels.styx.api.RequestCookie.requestCookie;
import static com.hotels.styx.api.Url.Builder.url;
import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static com.hotels.styx.support.matchers.MapMatcher.isMap;
import static java.lang.String.valueOf;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class HttpRequestTest {
    @Test
    public void convertsToStreamingHttpRequest() throws Exception {
        HttpRequest fullRequest = new HttpRequest.Builder(POST, "/foo/bar").body("foobar", UTF_8)
                .version(HTTP_1_1)
                .header("HeaderName", "HeaderValue")
                .cookies(requestCookie("CookieName", "CookieValue"))
                .build();

        LiveHttpRequest streaming = fullRequest.stream();

        assertThat(streaming.method(), is(HttpMethod.POST));
        assertThat(streaming.url(), is(url("/foo/bar").build()));
        assertThat(streaming.version(), is(HTTP_1_1));
        assertThat(streaming.headers(), containsInAnyOrder(
                header("Content-Length", "6"),
                header("HeaderName", "HeaderValue"),
                header("Cookie", "CookieName=CookieValue")));
        assertThat(streaming.cookies(), contains(requestCookie("CookieName", "CookieValue")));

        StepVerifier.create(streaming.aggregate(0x10000).map(it -> it.bodyAs(UTF_8)))
                .expectNext("foobar")
                .verifyComplete();
    }

    @ParameterizedTest
    @MethodSource("emptyBodyRequests")
    public void convertsToStreamingHttpRequestWithEmptyBody(HttpRequest fullRequest) {
        LiveHttpRequest streaming = fullRequest.stream();

        StepVerifier.create(streaming.body())
                .expectComplete()
                .verify();
    }

    // We want to ensure that these are all considered equivalent
    private static Stream<Arguments> emptyBodyRequests() {
        return Stream.of(
                Arguments.of(get("/foo/bar").build()),
                Arguments.of(new HttpRequest.Builder(POST, "/foo/bar").body(null, UTF_8).build()),
                Arguments.of(new HttpRequest.Builder(POST, "/foo/bar").body("", UTF_8).build()),
                Arguments.of(new HttpRequest.Builder(POST, "/foo/bar").body(null, UTF_8, true).build()),
                Arguments.of(new HttpRequest.Builder(POST, "/foo/bar").body("", UTF_8, true).build()),
                Arguments.of(new HttpRequest.Builder(POST, "/foo/bar").body(null, true).build()),
                Arguments.of(new HttpRequest.Builder(POST, "/foo/bar").body(new byte[0], true).build())
        );
    }

    @Test
    public void createsARequestWithDefaultValues() {
        HttpRequest request = get("/index").build();
        assertThat(request.version(), is(HTTP_1_1));
        assertThat(request.url().toString(), is("/index"));
        assertThat(request.path(), is("/index"));
        assertThat(request.id(), is(notNullValue()));
        assertThat(request.cookies(), is(emptyIterable()));
        assertThat(request.headers(), is(emptyIterable()));
        assertThat(request.headers("any"), is(emptyIterable()));

        assertThat(request.body().length, is(0));
        assertThat(request.cookie("any"), isAbsent());
        assertThat(request.header("any"), isAbsent());
        assertThat(request.keepAlive(), is(true));
        assertThat(request.method(), is(GET));
        assertThat(request.queryParam("any"), isAbsent());
        assertThat(request.queryParams("any"), is(emptyIterable()));
    }

    @Test
    public void canUseBuilderToSetRequestProperties() {
        HttpRequest request = patch("https://hotels.com")
                .version(HTTP_1_1)
                .id("id")
                .header("headerName", "a")
                .cookies(requestCookie("cfoo", "bar"))
                .build();

        assertThat(request.toString(), is("{version=HTTP/1.1, method=PATCH, uri=https://hotels.com, id=id}"));
        assertThat(request.headers().toString(), is("[headerName=a, Cookie=cfoo=bar, Host=hotels.com]"));
    }

    @Test
    public void transformsRequest() {
        HttpRequest request = get("/foo")
                .header("remove", "remove")
                .build();

        HttpRequest newRequest = request.newBuilder()
                .method(DELETE)
                .uri("/home")
                .header("remove", "notanymore")
                .build();

        assertThat(newRequest.method(), is(DELETE));
        assertThat(newRequest.url().path(), is("/home"));
        assertThat(newRequest.headers(), hasItem(header("remove", "notanymore")));
    }

    @Test
    public void contentFromStringOnlySetsContentLength() {
        HttpRequest request = HttpRequest.get("/")
                .body("Response content.", UTF_16)
                .build();

        assertThat(request.body(), is("Response content.".getBytes(UTF_16)));
        assertThat(request.header("Content-Length"), is(Optional.of("36")));
    }

    @Test
    public void contentFromStringOnlyThrowsNPEWhenCharsetIsNull() {
        Exception e = assertThrows(NullPointerException.class, () -> get("/")
                .body("Response content.", null)
                .build());
        assertEquals("Charset is not provided.", e.getMessage());
    }

    @Test
    public void contentFromStringSetsContentLengthIfRequired() {
        HttpRequest request1 = HttpRequest.get("/")
                .body("Response content.", UTF_8, true)
                .build();

        assertThat(request1.header("Content-Length"), is(Optional.of("17")));

        HttpRequest request2 = HttpRequest.get("/")
                .body("Response content.", UTF_8, false)
                .build();

        assertThat(request2.header("Content-Length"), is(Optional.empty()));
    }

    @Test
    public void contentFromStringThrowsNPEWhenCharsetIsNull() {
        Exception e = assertThrows(NullPointerException.class, () -> HttpRequest.get("/")
                .body("Response content.", null, false)
                .build());
        assertEquals("Charset is not provided.", e.getMessage());
    }

    @Test
    public void contentFromByteArraySetsContentLengthIfRequired() {
        HttpRequest response1 = HttpRequest.get("/")
                .body("Response content.".getBytes(UTF_16), true)
                .build();
        assertThat(response1.body(), is("Response content.".getBytes(UTF_16)));
        assertThat(response1.header("Content-Length"), is(Optional.of("36")));

        HttpRequest response2 = HttpRequest.get("/")
                .body("Response content.".getBytes(UTF_8), false)
                .build();

        assertThat(response2.body(), is("Response content.".getBytes(UTF_8)));
        assertThat(response2.header("Content-Length"), is(Optional.empty()));
    }


    @Test
    public void requestBodyIsImmutable() {
        HttpRequest request = get("/foo")
                .body("Original body", UTF_8)
                .build();

        request.body()[0] = 'A';

        assertThat(request.bodyAs(UTF_8), is("Original body"));
    }

    @Test
    public void requestBodyCannotBeChangedViaStreamingRequest() {
        HttpRequest original = HttpRequest.get("/foo")
                .body("original", UTF_8)
                .build();

        Flux.from(original.stream()
                .body()
                .map(buffer -> {
                    buffer.delegate().array()[0] = 'A';
                    return buffer;
                }))
                .subscribe();

        assertThat(original.bodyAs(UTF_8), is("original"));
    }

    @Test
    public void transformedBodyIsNewCopy() {
        HttpRequest request = get("/foo")
                .body("Original body", UTF_8)
                .build();

        HttpRequest newRequest = request.newBuilder()
                .body("New body", UTF_8)
                .build();

        assertThat(request.bodyAs(UTF_8), is("Original body"));
        assertThat(newRequest.bodyAs(UTF_8), is("New body"));
    }

    @Test
    public void decodesQueryParams() {
        HttpRequest request = get("http://example.com/?foo=bar").build();
        assertThat(request.queryParam("foo"), isValue("bar"));
    }

    @Test
    public void decodesQueryParamsContainingEncodedEquals() {
        HttpRequest request = get("http://example.com/?foo=a%2Bb%3Dc")
                .build();
        assertThat(request.queryParam("foo"), isValue("a+b=c"));
    }

    @Test
    public void createsRequestBuilderFromRequest() {
        HttpRequest originalRequest = get("/home")
                .cookies(requestCookie("fred", "blogs"))
                .header("some", "header")
                .build();

        HttpRequest clonedRequest = originalRequest.newBuilder().build();

        assertThat(clonedRequest.method(), is(originalRequest.method()));
        assertThat(clonedRequest.url(), is(originalRequest.url()));
        assertThat(clonedRequest.headers().toString(), is(originalRequest.headers().toString()));
        assertThat(clonedRequest.body(), is(originalRequest.body()));
    }

    @Test
    public void extractsSingleQueryParameter() {
        HttpRequest req = get("http://host.com:8080/path?fish=cod&fruit=orange")
                .build();
        assertThat(req.queryParam("fish"), isValue("cod"));
    }

    @Test
    public void extractsMultipleQueryParameterValues() {
        HttpRequest req = get("http://host.com:8080/path?fish=cod&fruit=orange&fish=smørflyndre").build();
        assertThat(req.queryParams("fish"), contains("cod", "smørflyndre"));
    }

    @Test
    public void extractsMultipleQueryParams() {
        HttpRequest req = get("http://example.com?foo=bar&foo=hello&abc=def")
                .build();

        assertThat(req.queryParamNames(), containsInAnyOrder("foo", "abc"));

        Map<String, List<String>> expected = new HashMap<>();
        expected.put("foo", listOf("bar", "hello"));
        expected.put("abc", listOf("def"));
        assertThat(req.queryParams(), isMap(expected));
    }

    @Test
    public void alwaysReturnsEmptyListWhenThereIsNoQueryString() {
        HttpRequest req = get("http://host.com:8080/path").build();
        assertThat(req.queryParams("fish"), is(emptyIterable()));
        assertThat(req.queryParam("fish"), isAbsent());
    }

    @Test
    public void returnsEmptyListWhenThereIsNoSuchParameter() {
        HttpRequest req = get("http://host.com:8080/path?poisson=cabillaud").build();
        assertThat(req.queryParams("fish"), is(emptyIterable()));
        assertThat(req.queryParam("fish"), isAbsent());
    }

    @Test
    public void canExtractCookies() {
        HttpRequest request = get("/")
                .cookies(
                        requestCookie("cookie1", "foo"),
                        requestCookie("cookie3", "baz"),
                        requestCookie("cookie2", "bar"))
                .build();

        assertThat(request.cookie("cookie1"), isValue(requestCookie("cookie1", "foo")));
        assertThat(request.cookie("cookie2"), isValue(requestCookie("cookie2", "bar")));
        assertThat(request.cookie("cookie3"), isValue(requestCookie("cookie3", "baz")));
    }

    @Test
    public void cannotExtractNonExistentCookie() {
        HttpRequest request = get("/")
                .cookies(
                        requestCookie("cookie1", "foo"),
                        requestCookie("cookie3", "baz"),
                        requestCookie("cookie2", "bar"))
                .build();

        assertThat(request.cookie("cookie4"), isAbsent());
    }

    @Test
    public void extractsAllCookies() {
        HttpRequest request = get("/")
                .cookies(
                        requestCookie("cookie1", "foo"),
                        requestCookie("cookie3", "baz"),
                        requestCookie("cookie2", "bar"))
                .build();

        assertThat(request.cookies(), containsInAnyOrder(
                requestCookie("cookie1", "foo"),
                requestCookie("cookie2", "bar"),
                requestCookie("cookie3", "baz")));
    }

    @Test
    public void extractsEmptyIterableIfCookieHeaderNotSet() {
        HttpRequest request = get("/").build();
        assertThat(request.cookies(), is(emptyIterable()));
    }

    @Test
    public void canRemoveAHeader() {
        Object hdValue = "b";
        HttpRequest request = get("/")
                .header("a", hdValue)
                .addHeader("c", hdValue)
                .build();
        HttpRequest shouldRemoveHeader = request.newBuilder()
                .removeHeader("c")
                .build();

        assertThat(shouldRemoveHeader.headers(), contains(header("a", "b")));
    }

    @Test
    public void shouldSetsContentLengthForNonStreamingBodyMessage() {
        assertThat(put("/home").body("", UTF_8).build().header(CONTENT_LENGTH), isValue("0"));
        assertThat(put("/home").body("Hello", UTF_8).build().header(CONTENT_LENGTH), isValue(valueOf(bytes("Hello").length)));
    }

    @Test
    public void shouldDetectKeepAlive() {
        assertThat(get("/index").build().keepAlive(), is(true));
        assertThat(get("/index").version(HTTP_1_0).enableKeepAlive().build().keepAlive(), is(true));
        assertThat(get("/index").version(HTTP_1_0).build().keepAlive(), is(false));
    }

    private static byte[] bytes(String content) {
        return content.getBytes(UTF_8);
    }

    @Test
    public void rejectsNullCookie() {
        assertThrows(NullPointerException.class, () -> get("/").cookies((RequestCookie) null));
    }

    @Test
    public void rejectsNullCookieName() {
        assertThrows(IllegalArgumentException.class, () -> get("/").cookies(requestCookie(null, "value")).build());
    }

    @Test
    public void rejectsNullCookieValue() {
        assertThrows(NullPointerException.class, () -> get("/").cookies(requestCookie("name", null)).build());
    }

    @Test
    public void rejectsMultipleContentLengthInSingleHeader() {
        assertThrows(IllegalArgumentException.class, () -> get("/foo")
                .addHeader(CONTENT_LENGTH, "15, 16")
                .build());
    }

    @Test
    public void rejectsMultipleContentLengthHeaders() {
        assertThrows(IllegalArgumentException.class, () -> get("/foo")
                .addHeader(CONTENT_LENGTH, "15")
                .addHeader(CONTENT_LENGTH, "16")
                .build());
    }

    @Test
    public void rejectsInvalidContentLength() {
        assertThrows(IllegalArgumentException.class, () -> get("/foo")
                .addHeader(CONTENT_LENGTH, "foo")
                .build());
    }

    @Test
    public void createARequestWithFullUrl() {
        HttpRequest request = get("http://www.hotels.com").build();

        assertThat(request.url(), is(url("http://www.hotels.com").build()));
    }

    @Test
    public void setsHostHeaderFromAuthorityIfSet() {
        HttpRequest request = get("http://www.hotels.com").build();

        assertThat(request.header(HOST), isValue("www.hotels.com"));
    }

    @Test
    public void createsANewRequestWithSameVersionAsBefore() {
        HttpRequest v10Request = get("/foo/bar").version(HTTP_1_0).build();

        HttpRequest newRequest = v10Request.newBuilder().uri("/blah/blah").build();

        assertThat(newRequest.version(), is(HTTP_1_0));
    }

    @Test
    public void addsCookies() {
        HttpRequest request = HttpRequest.get("/")
                .addCookies(requestCookie("x", "x1"), requestCookie("y", "y1"))
                .build();

        assertThat(request.cookies(), containsInAnyOrder(requestCookie("x", "x1"), requestCookie("y", "y1")));
    }

    @Test
    public void addsCookiesToExistingCookies() {
        HttpRequest request = HttpRequest.get("/")
                .addCookies(requestCookie("z", "z1"))
                .addCookies(requestCookie("x", "x1"), requestCookie("y", "y1"))
                .build();

        assertThat(request.cookies(), containsInAnyOrder(requestCookie("x", "x1"), requestCookie("y", "y1"), requestCookie("z", "z1")));
    }

    @Test
    public void newCookiesWithDuplicateNamesOverridePreviousOnes() {
        HttpRequest r1 = HttpRequest.get("/")
                .cookies(requestCookie("y", "y1"))
                .build();

        HttpRequest r2 = r1.newBuilder().addCookies(
                requestCookie("y", "y2"))
                .build();

        assertThat(r2.cookies(), containsInAnyOrder(requestCookie("y", "y2")));
    }

    @Test
    public void removesCookies() {
        HttpRequest r1 = HttpRequest.get("/")
                .addCookies(requestCookie("x", "x1"), requestCookie("y", "y1"))
                .build();

        HttpRequest r2 = r1.newBuilder()
                .removeCookies("x")
                .removeCookies("foo") // ensure that trying to remove a non-existent cookie does not cause Exception
                .build();

        assertThat(r2.cookies(), contains(requestCookie("y", "y1")));
    }

    @Test
    public void removesCookiesInSameBuilder() {
        HttpRequest r1 = HttpRequest.get("/")
                .addCookies(requestCookie("x", "x1"))
                .removeCookies("x")
                .build();

        assertThat(r1.cookie("x"), isAbsent());
    }

    @Test
    public void ensuresContentLengthIsPositive() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> HttpRequest.post("/y")
                .header("Content-Length", -3)
                .build());
        assertEquals("Invalid Content-Length found. -3", e.getMessage());
    }
}
