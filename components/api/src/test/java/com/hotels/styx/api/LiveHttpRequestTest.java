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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static com.hotels.styx.api.Collections.listOf;
import static com.hotels.styx.api.HttpHeader.header;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpMethod.GET;
import static com.hotels.styx.api.HttpMethod.POST;
import static com.hotels.styx.api.HttpVersion.HTTP_1_0;
import static com.hotels.styx.api.HttpVersion.HTTP_1_1;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static com.hotels.styx.api.LiveHttpRequest.patch;
import static com.hotels.styx.api.LiveHttpRequest.post;
import static com.hotels.styx.api.LiveHttpRequest.put;
import static com.hotels.styx.api.RequestCookie.requestCookie;
import static com.hotels.styx.api.Url.Builder.url;
import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static com.hotels.styx.support.matchers.MapMatcher.isMap;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LiveHttpRequestTest {
    @Test
    public void decodesToFullHttpRequest() throws Exception {
        LiveHttpRequest streamingRequest = post("/foo/bar", body("foo", "bar"))
                .version(HTTP_1_0)
                .header("HeaderName", "HeaderValue")
                .cookies(requestCookie("CookieName", "CookieValue"))
                .build();



        HttpRequest full = Mono.from(streamingRequest.aggregate(0x1000)).block();

        assertThat(full.method(), is(POST));
        assertThat(full.url(), is(url("/foo/bar").build()));
        assertThat(full.version(), is(HTTP_1_0));
        assertThat(full.headers(), containsInAnyOrder(
                header("HeaderName", "HeaderValue"),
                header("Cookie", "CookieName=CookieValue")));
        assertThat(full.cookies(), contains(requestCookie("CookieName", "CookieValue")));
        assertThat(full.body(), is(bytes("foobar")));
    }

    @Test
    public void toFullRequestReleasesOriginalReferenceCountedBuffers() throws ExecutionException, InterruptedException {
        Buffer content = new Buffer("original", UTF_8);

        LiveHttpRequest original = LiveHttpRequest.get("/foo")
                .body(new ByteStream(Flux.just(content)))
                .build();

        HttpRequest fullRequest = Mono.from(original.aggregate(100)).block();

        assertThat(content.delegate().refCnt(), is(0));

        assertThat(fullRequest.bodyAs(UTF_8), is("original"));
    }

    @ParameterizedTest
    @MethodSource("emptyBodyRequests")
    public void encodesToStreamingHttpRequestWithEmptyBody(LiveHttpRequest streamingRequest) throws Exception {
        HttpRequest full = Mono.from(streamingRequest.aggregate(0x1000)).block();
        assertThat(full.body(), is(new byte[0]));
    }

    // We want to ensure that these are all considered equivalent
    private static Stream<Arguments> emptyBodyRequests() {
        return Stream.of(
                Arguments.of(get("/foo/bar").build()),
                Arguments.of(post("/foo/bar", new ByteStream(Flux.empty())).build())
        );
    }

    @Test
    public void createsARequestWithDefaultValues() {
        LiveHttpRequest request = get("/index").build();
        assertThat(request.version(), is(HTTP_1_1));
        assertThat(request.url().toString(), is("/index"));
        assertThat(request.path(), is("/index"));
        assertThat(request.id(), is(notNullValue()));
        assertThat(request.cookies(), is(emptyIterable()));
        assertThat(request.headers(), is(emptyIterable()));
        assertThat(request.headers("any"), is(emptyIterable()));

        assertThat(bytesToString(request.body()), is(""));
        assertThat(request.cookie("any"), isAbsent());
        assertThat(request.header("any"), isAbsent());
        assertThat(request.keepAlive(), is(true));
        assertThat(request.method(), is(GET));
        assertThat(request.queryParam("any"), isAbsent());
        assertThat(request.queryParams("any"), is(emptyIterable()));
    }

    @Test
    public void canUseBuilderToSetRequestProperties() {
        LiveHttpRequest request = patch("https://hotels.com")
                .version(HTTP_1_0)
                .id("id")
                .header("headerName", "a")
                .cookies(requestCookie("cfoo", "bar"))
                .build();

        assertThat(request.toString(), is("{version=HTTP/1.0, method=PATCH, uri=https://hotels.com, id=id}"));
        assertThat(request.headers().toString(), is("[headerName=a, Cookie=cfoo=bar, Host=hotels.com]"));
    }

    @Test
    public void canModifyPreviouslyCreatedRequest() {
        LiveHttpRequest request = get("/foo")
                .header("remove", "remove")
                .build();

        LiveHttpRequest newRequest = request.newBuilder()
                .uri("/home")
                .header("remove", "notanymore")
                .build();

        assertThat(newRequest.url().path(), is("/home"));
        assertThat(newRequest.headers(), hasItem(header("remove", "notanymore")));
    }

    @Test
    public void decodesQueryParams() {
        LiveHttpRequest request = get("http://example.com/?foo=bar")
                .build();
        assertThat(request.queryParam("foo"), isValue("bar"));
    }

    @Test
    public void decodesQueryParamsContainingEncodedEquals() {
        LiveHttpRequest request = get("http://example.com/?foo=a%2Bb%3Dc")
                .build();
        assertThat(request.queryParam("foo"), isValue("a+b=c"));
    }

    @Test
    public void createsRequestBuilderFromRequest() {
        LiveHttpRequest originalRequest = get("/home")
                .cookies(requestCookie("fred", "blogs"))
                .header("some", "header")
                .build();

        LiveHttpRequest clonedRequest = originalRequest.newBuilder().build();

        assertThat(clonedRequest.method(), is(originalRequest.method()));
        assertThat(clonedRequest.url(), is(originalRequest.url()));
        assertThat(clonedRequest.headers().toString(), is(originalRequest.headers().toString()));
        assertThat(clonedRequest.body(), is(originalRequest.body()));
    }

    @Test
    public void extractsSingleQueryParameter() {
        LiveHttpRequest req = get("http://host.com:8080/path?fish=cod&fruit=orange")
                .build();
        assertThat(req.queryParam("fish"), isValue("cod"));
    }

    @Test
    public void extractsMultipleQueryParameterValues() {
        LiveHttpRequest req = get("http://host.com:8080/path?fish=cod&fruit=orange&fish=smørflyndre").build();
        assertThat(req.queryParams("fish"), contains("cod", "smørflyndre"));
    }

    @Test
    public void extractsMultipleQueryParams() {
        LiveHttpRequest req = get("http://example.com?foo=bar&foo=hello&abc=def")
                .build();

        assertThat(req.queryParamNames(), containsInAnyOrder("foo", "abc"));

        Map<String, List<String>> expected = new HashMap<>();
        expected.put("foo", listOf("bar", "hello"));
        expected.put("abc", listOf("def"));
        assertThat(req.queryParams(), isMap(expected));
    }

    @Test
    public void alwaysReturnsEmptyListWhenThereIsNoQueryString() {
        LiveHttpRequest req = get("http://host.com:8080/path").build();
        assertThat(req.queryParams("fish"), is(emptyIterable()));
        assertThat(req.queryParam("fish"), isAbsent());
    }

    @Test
    public void returnsEmptyListWhenThereIsNoSuchParameter() {
        LiveHttpRequest req = get("http://host.com:8080/path?poisson=cabillaud").build();
        assertThat(req.queryParams("fish"), is(emptyIterable()));
        assertThat(req.queryParam("fish"), isAbsent());
    }

    @Test
    public void canExtractCookies() {
        LiveHttpRequest request = get("/")
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
        LiveHttpRequest request = get("/")
                .cookies(
                        requestCookie("cookie1", "foo"),
                        requestCookie("cookie3", "baz"),
                        requestCookie("cookie2", "bar"))
                .build();

        assertThat(request.cookie("cookie4"), isAbsent());
    }

    @Test
    public void extractsAllCookies() {
        LiveHttpRequest request = get("/")
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
        LiveHttpRequest request = get("/").build();
        assertThat(request.cookies(), is(emptyIterable()));
    }

    @Test
    public void canRemoveAHeader() {
        Object hdValue = "b";
        LiveHttpRequest request = get("/")
                .header("a", hdValue)
                .addHeader("c", hdValue)
                .build();
        LiveHttpRequest shouldRemoveHeader = request.newBuilder()
                .removeHeader("c")
                .build();

        assertThat(shouldRemoveHeader.headers(), contains(header("a", "b")));
    }

    @Test
    public void shouldSetsContentLengthForNonStreamingBodyMessage() {
        assertThat(put("/home").body(new ByteStream(Flux.just(new Buffer("Hello", UTF_8)))).build().header(CONTENT_LENGTH), isAbsent());
    }

    @Test
    public void shouldDetectKeepAlive() {
        assertThat(get("/index").build().keepAlive(), is(true));
        assertThat(get("/index").version(HTTP_1_0).enableKeepAlive().build().keepAlive(), is(true));
        assertThat(get("/index").version(HTTP_1_0).build().keepAlive(), is(false));
    }

    @Test
    public void builderSetsRequestContent() throws Exception {
        LiveHttpRequest request = post("/foo/bar", body("Foo bar")).build();

        assertThat(bytesToString(request.body()), is("Foo bar"));
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
    public void createARequestWithStreamingUrl() {
        LiveHttpRequest request = get("http://www.hotels.com").build();

        assertThat(request.url(), is(url("http://www.hotels.com").build()));
    }

    @Test
    public void setsHostHeaderFromAuthorityIfSet() {
        LiveHttpRequest request = get("http://www.hotels.com").build();

        assertThat(request.header(HOST), isValue("www.hotels.com"));
    }

    @Test
    public void createsANewRequestWithSameVersionAsBefore() {
        LiveHttpRequest v10Request = get("/foo/bar").version(HTTP_1_0).build();

        LiveHttpRequest newRequest = v10Request.newBuilder().uri("/blah/blah").build();

        assertThat(newRequest.version(), is(HTTP_1_0));
    }

    @Test
    public void addsCookies() {
        LiveHttpRequest request = LiveHttpRequest.get("/")
                .addCookies(requestCookie("x", "x1"), requestCookie("y", "y1"))
                .build();

        assertThat(request.cookies(), containsInAnyOrder(requestCookie("x", "x1"), requestCookie("y", "y1")));
    }

    @Test
    public void addsCookiesToExistingCookies() {
        LiveHttpRequest request = LiveHttpRequest.get("/")
                .addCookies(requestCookie("z", "z1"))
                .addCookies(requestCookie("x", "x1"), requestCookie("y", "y1"))
                .build();

        assertThat(request.cookies(), containsInAnyOrder(requestCookie("x", "x1"), requestCookie("y", "y1"), requestCookie("z", "z1")));
    }

    @Test
    public void newCookiesWithDuplicateNamesOverridePreviousOnes() {
        LiveHttpRequest r1 = LiveHttpRequest.get("/")
                .cookies(requestCookie("y", "y1"))
                .build();

        LiveHttpRequest r2 = r1.newBuilder().addCookies(
                requestCookie("y", "y2"))
                .build();

        assertThat(r2.cookies(), contains(requestCookie("y", "y2")));
    }

    @Test
    public void removesCookies() {
        LiveHttpRequest r1 = LiveHttpRequest.get("/")
                .addCookies(requestCookie("x", "x1"), requestCookie("y", "y1"))
                .build();

        LiveHttpRequest r2 = r1.newBuilder()
                .removeCookies("x")
                .removeCookies("foo") // ensure that trying to remove a non-existent cookie does not cause Exception
                .build();

        assertThat(r2.cookies(), contains(requestCookie("y", "y1")));
    }

    @Test
    public void removesCookiesInSameBuilder() {
        LiveHttpRequest r1 = LiveHttpRequest.get("/")
                .addCookies(requestCookie("x", "x1"))
                .removeCookies("x")
                .build();

        assertThat(r1.cookie("x"), isAbsent());
    }

    @Test
    public void transformsUri() {
        LiveHttpRequest request = LiveHttpRequest.get("/x").build()
                .newBuilder()
                .uri("/y")
                .build();

        assertEquals(request.url().path(), "/y");
    }

    @Test
    public void transformsId() {
        LiveHttpRequest request = LiveHttpRequest.get("/").id("abc").build()
                .newBuilder()
                .id("xyz")
                .build();

        assertEquals(request.id(), "xyz");
    }

    @Test
    public void transformsHeader() {
        LiveHttpRequest request = LiveHttpRequest.get("/").header("X-Styx-ID", "test").build()
                .newBuilder()
                .header("X-Styx-ID", "bar")
                .build();

        assertEquals(request.header("X-Styx-ID"), Optional.of("bar"));
    }

    @Test
    public void transformsHeaders() {
        LiveHttpRequest request = LiveHttpRequest.get("/").headers(
                new HttpHeaders.Builder()
                        .add("x", "y")
                        .build())
                .build()
                .newBuilder()
                .headers(
                        new HttpHeaders.Builder()
                        .add("a", "b")
                        .build())
                .build();

        assertThat(request.header("x"), is(Optional.empty()));
        assertThat(request.header("a"), is(Optional.of("b")));
    }

    @Test
    public void transformerAddsHeader() {
        LiveHttpRequest request = LiveHttpRequest.get("/").build()
                .newBuilder()
                .addHeader("x", "y")
                .build();

        assertEquals(request.header("x"), Optional.of("y"));
    }

    @Test
    public void transformerRemovesHeader() {
        LiveHttpRequest request = LiveHttpRequest.get("/").addHeader("x", "y").build()
                .newBuilder()
                .removeHeader("x")
                .build();

        assertEquals(request.header("x"), Optional.empty());
    }

    @Test
    public void transformsUrl() {
        LiveHttpRequest request = LiveHttpRequest.get("/").build()
                .newBuilder()
                .url(url("/z").build())
                .build();

        assertEquals(request.url().path(), "/z");
    }

    @Test
    public void transformsCookies() {
        LiveHttpRequest request = LiveHttpRequest.get("/").addCookies(requestCookie("cookie", "xyz010")).build()
                .newBuilder()
                .cookies(requestCookie("cookie", "xyz202"))
                .build();

        assertEquals(request.cookie("cookie"), Optional.of(requestCookie("cookie", "xyz202")));
    }

    @Test
    public void transformsCookiesViaList() {
        LiveHttpRequest request = LiveHttpRequest.get("/").addCookies(requestCookie("cookie", "xyz010")).build()
                .newBuilder()
                .cookies(listOf(requestCookie("cookie", "xyz202")))
                .build();

        assertEquals(request.cookie("cookie"), Optional.of(requestCookie("cookie", "xyz202")));
    }

    @Test
    public void transformsByAddingCookies() {
        LiveHttpRequest request = LiveHttpRequest.get("/").build()
                .newBuilder()
                .addCookies(requestCookie("cookie", "xyz202"))
                .build();

        assertEquals(request.cookie("cookie"), Optional.of(requestCookie("cookie", "xyz202")));
    }

    @Test
    public void transformsByAddingCookiesList() {
        LiveHttpRequest request = LiveHttpRequest.get("/").build()
                .newBuilder()
                .addCookies(listOf(requestCookie("cookie", "xyz202")))
                .build();

        assertEquals(request.cookie("cookie"), Optional.of(requestCookie("cookie", "xyz202")));
    }

    @Test
    public void transformsByRemovingCookies() {
        LiveHttpRequest request = LiveHttpRequest.get("/").addCookies(requestCookie("cookie", "xyz202")).build()
                .newBuilder()
                .removeCookies("cookie")
                .build();

        assertEquals(request.cookie("cookie"), Optional.empty());
    }

    @Test
    public void transformsByRemovingCookieList() {
        LiveHttpRequest request = LiveHttpRequest.get("/").addCookies(requestCookie("cookie", "xyz202")).build()
                .newBuilder()
                .removeCookies(listOf("cookie"))
                .build();

        assertEquals(request.cookie("cookie"), Optional.empty());
    }

    @Test
    public void ensuresContentLengthIsPositive() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> LiveHttpRequest.post("/y")
                .header("Content-Length", -3)
                .build());
        assertEquals("Invalid Content-Length found. -3", e.getMessage());
    }

    private static ByteStream body(String... contents) {

        return new ByteStream(
                Flux.fromIterable(
                        Stream.of(contents)
                                .map(content -> new Buffer(content, UTF_8))
                                .collect(toList())));
    }

    private static String bytesToString(ByteStream body) {
        try {
            return new String(body.aggregate(100000).get().content(), UTF_8);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] bytes(String content) {
        return content.getBytes(UTF_8);
    }
}