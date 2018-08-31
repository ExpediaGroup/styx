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

import com.google.common.collect.ImmutableMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static com.hotels.styx.api.HttpHeader.header;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpRequest.patch;
import static com.hotels.styx.api.HttpRequest.post;
import static com.hotels.styx.api.HttpRequest.put;
import static com.hotels.styx.api.Url.Builder.url;
import static com.hotels.styx.api.RequestCookie.requestCookie;
import static com.hotels.styx.api.HttpMethod.DELETE;
import static com.hotels.styx.api.HttpMethod.GET;
import static com.hotels.styx.api.HttpMethod.POST;
import static com.hotels.styx.api.HttpVersion.HTTP_1_0;
import static com.hotels.styx.api.HttpVersion.HTTP_1_1;
import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static com.hotels.styx.support.matchers.MapMatcher.isMap;
import static io.netty.buffer.Unpooled.copiedBuffer;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class HttpRequestTest {
    @Test
    public void decodesToFullHttpRequest() throws Exception {
        HttpRequest streamingRequest = post("/foo/bar", body("foo", "bar"))
                .secure(true)
                .version(HTTP_1_0)
                .header("HeaderName", "HeaderValue")
                .cookies(requestCookie("CookieName", "CookieValue"))
                .build();

        FullHttpRequest full = streamingRequest.toFullRequest(0x1000)
                .asCompletableFuture()
                .get();

        assertThat(full.method(), is(POST));
        assertThat(full.url(), is(url("/foo/bar").build()));
        assertThat(full.isSecure(), is(true));
        assertThat(full.version(), is(HTTP_1_0));
        assertThat(full.headers(), containsInAnyOrder(
                header("HeaderName", "HeaderValue"),
                header("Cookie", "CookieName=CookieValue")));
        assertThat(full.cookies(), contains(requestCookie("CookieName", "CookieValue")));
        assertThat(full.body(), is(bytes("foobar")));
    }

    @Test(expectedExceptions = io.netty.util.IllegalReferenceCountException.class)
    public void toFullRequestReleasesOriginalReferenceCountedBuffers() throws ExecutionException, InterruptedException {
        ByteBuf content = Unpooled.copiedBuffer("original", UTF_8);

        HttpRequest original = HttpRequest.get("/foo")
                .body(StyxObservable.of(content))
                .build();

        FullHttpRequest fullRequest = original.toFullRequest(100)
                .asCompletableFuture()
                .get();

        content.array()[0] = 'A';

        assertThat(fullRequest.bodyAs(UTF_8), is("original"));
    }

    @Test(dataProvider = "emptyBodyRequests")
    public void encodesToStreamingHttpRequestWithEmptyBody(HttpRequest streamingRequest) throws Exception {
        FullHttpRequest full = streamingRequest.toFullRequest(0x1000)
                .asCompletableFuture()
                .get();

        assertThat(full.body(), is(new byte[0]));
    }

    // We want to ensure that these are all considered equivalent
    @DataProvider(name = "emptyBodyRequests")
    private Object[][] emptyBodyRequests() {
        return new Object[][]{
                {get("/foo/bar").build()},
                {post("/foo/bar", StyxCoreObservable.empty()).build()},
        };
    }

    @Test
    public void createsARequestWithDefaultValues() throws Exception {
        HttpRequest request = get("/index").build();
        assertThat(request.version(), is(HTTP_1_1));
        assertThat(request.url().toString(), is("/index"));
        assertThat(request.path(), is("/index"));
        assertThat(request.clientAddress().getHostName(), is("127.0.0.1"));
        assertThat(request.id(), is(notNullValue()));
        assertThat(request.isSecure(), is(false));
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
        HttpRequest request = patch("https://hotels.com")
                .version(HTTP_1_0)
                .id("id")
                .header("headerName", "a")
                .cookies(requestCookie("cfoo", "bar"))
                .build();

        assertThat(request.toString(), is("HttpRequest{version=HTTP/1.0, method=PATCH, uri=https://hotels.com, headers=[headerName=a, Cookie=cfoo=bar, Host=hotels.com]," +
                " id=id, secure=true, clientAddress=127.0.0.1:0}"));

        assertThat(request.headers("headerName"), is(singletonList("a")));
    }

    @Test
    public void canModifyPreviouslyCreatedRequest() {
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
    public void shouldDetermineIfRequestIsSecure() {
        assertThat(get("https://hotels.com").build().isSecure(), is(true));
        assertThat(get("http://hotels.com").build().isSecure(), is(false));
    }

    @Test
    public void decodesQueryParams() {
        HttpRequest request = get("http://example.com/?foo=bar")
                .build();
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

        assertThat(req.queryParams(), isMap(ImmutableMap.of(
                "foo", asList("bar", "hello"),
                "abc", singletonList("def")
        )));
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
        assertThat(put("/home").body(StyxObservable.of(copiedBuffer("Hello", UTF_8))).build().header(CONTENT_LENGTH), isAbsent());
    }

    @Test
    public void shouldDetectKeepAlive() {
        assertThat(get("/index").build().keepAlive(), is(true));
        assertThat(get("/index").version(HTTP_1_0).enableKeepAlive().build().keepAlive(), is(true));
        assertThat(get("/index").version(HTTP_1_0).build().keepAlive(), is(false));
    }

    @Test
    public void builderSetsRequestContent() throws Exception {
        HttpRequest request = post("/foo/bar", body("Foo bar")).build();

        assertThat(bytesToString(request.body()), is("Foo bar"));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void rejectsNullCookie() {
        get("/").cookies((RequestCookie) null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsNullCookieName() {
        get("/").cookies(requestCookie(null, "value")).build();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void rejectsNullCookieValue() {
        get("/").cookies(requestCookie("name", null)).build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsMultipleContentLengthInSingleHeader() {
        get("/foo")
                .addHeader(CONTENT_LENGTH, "15, 16")
                .build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsMultipleContentLengthHeaders() {
        get("/foo")
                .addHeader(CONTENT_LENGTH, "15")
                .addHeader(CONTENT_LENGTH, "16")
                .build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsInvalidContentLength() {
        get("/foo")
                .addHeader(CONTENT_LENGTH, "foo")
                .build();
    }

    @Test
    public void createARequestWithStreamingUrl() {
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

    // Make tests to ensure conversion from HttpRequest and back again preserves clientAddress - do it for Fullhttprequest too
    @Test
    public void conversionPreservesClientAddress() throws Exception {
        InetSocketAddress address = InetSocketAddress.createUnresolved("styx.io", 8080);
        HttpRequest original = HttpRequest.post("/foo").clientAddress(address).build();

        HttpRequest streaming = new HttpRequest.Builder(original).build();

        HttpRequest shouldMatchOriginal = streaming.toFullRequest(0x100)
                .asCompletableFuture()
                .get()
                .toStreamingRequest();

        assertThat(shouldMatchOriginal.clientAddress(), is(address));
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

        assertThat(r2.cookies(), contains(requestCookie("y", "y2")));
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

    private static StyxObservable<ByteBuf> body(String... contents) {
        return StyxObservable.from(Stream.of(contents)
                .map(content -> copiedBuffer(content, UTF_8))
                .collect(toList()));
    }

    private static String bytesToString(StyxObservable<ByteBuf> body) throws ExecutionException, InterruptedException {
        return body.reduce((byteBuf, result) -> result + byteBuf.toString(UTF_8), "")
                .asCompletableFuture()
                .get();
    }

    private static byte[] bytes(String content) {
        return content.getBytes(UTF_8);
    }
}