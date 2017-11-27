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

import com.google.common.collect.ImmutableMap;
import com.hotels.styx.api.HttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import rx.observers.TestSubscriber;

import java.net.InetSocketAddress;

import static com.hotels.styx.api.HttpCookie.cookie;
import static com.hotels.styx.api.HttpHeader.header;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.COOKIE;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpRequest.Builder.put;
import static com.hotels.styx.api.Url.Builder.url;
import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static com.hotels.styx.support.matchers.MapMatcher.isMap;
import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpMethod.DELETE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_0;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.lang.String.valueOf;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static rx.Observable.just;


public class FullHttpRequestTest {
    @Test
    public void encodesToStreamingHttpRequest() throws Exception {
        FullHttpRequest<String> fullRequest = get("/foo/bar")
                .body("foobar")
                .build();

        HttpRequest streaming = fullRequest.toStreamingHttpRequest(string -> copiedBuffer(string, UTF_8));


        TestSubscriber<ByteBuf> subscriber = TestSubscriber.create(0);
        subscriber.requestMore(1);

        streaming.body().content().subscribe(subscriber);

        assertThat(subscriber.getOnNextEvents().size(), is(1));
        ByteBuf buf = subscriber.getOnNextEvents().get(0);
        assertThat(buf.toString(UTF_8), is("foobar"));
    }

    @Test(dataProvider = "emptyBodyRequests")
    public void encodesToStreamingHttpRequestWithEmptyBody(FullHttpRequest<String> fullRequest) throws Exception {
        HttpRequest streaming = fullRequest.toStreamingHttpRequest(string -> copiedBuffer(string, UTF_8));

        TestSubscriber<ByteBuf> subscriber = TestSubscriber.create(0);
        subscriber.requestMore(1);

        streaming.body().content().subscribe(subscriber);

        assertThat(subscriber.getOnNextEvents().size(), is(0));
        subscriber.assertCompleted();
    }

    // We want to ensure that these are all considered equivalent
    @DataProvider(name = "emptyBodyRequests")
    private Object[][] emptyBodyRequests() {
        return new Object[][]{
                { get("/foo/bar")
                        .build()},
                { get("/foo/bar")
                        .body(null)
                        .build()},
                { get("/foo/bar")
                        .body("")
                        .build()},
        };
    }

    @Test
    public void encodingToStreamingHttpRequestDefaultsToUTF8() throws Exception {
        FullHttpRequest<String> fullRequest = get("/foo/bar")
                .body("foobar")
                .build();

        HttpRequest streaming = FullHttpRequest.toStreamingHttpRequest(fullRequest);


        TestSubscriber<ByteBuf> subscriber = TestSubscriber.create(0);
        subscriber.requestMore(1);

        streaming.body().content().subscribe(subscriber);

        assertThat(subscriber.getOnNextEvents().size(), is(1));
        ByteBuf buf = subscriber.getOnNextEvents().get(0);
        assertThat(buf.toString(UTF_8), is("foobar"));
    }

    @Test
    public void createsARequestWithDefaultValues() {
        FullHttpRequest<String> request = get("/index").build();
        assertThat(request.version(), is(HTTP_1_1));
        assertThat(request.url().toString(), is("/index"));
        assertThat(request.path(), is("/index"));
        assertThat(request.clientAddress().getHostName(), is("127.0.0.1"));
        assertThat(request.id(), is(notNullValue()));
        assertThat(request.isSecure(), is(false));
        assertThat(request.cookies(), is(emptyIterable()));
        assertThat(request.headers(), is(emptyIterable()));
        assertThat(request.headers("any"), is(emptyIterable()));

        assertThat(request.body(), is(nullValue()));
        assertThat(request.cookie("any"), isAbsent());
        assertThat(request.header("any"), isAbsent());
        assertThat(request.keepAlive(), is(true));
        assertThat(request.method(), is(GET));
        assertThat(request.queryParam("any"), isAbsent());
        assertThat(request.queryParams("any"), is(emptyIterable()));
    }

    @Test
    public void canUseBuilderToSetRequestProperties() {
        FullHttpRequest<String> request = patch("https://hotels.com")
                .version(HTTP_1_0)
                .id("id")
                .header("headerName", "a")
                .addCookie("cfoo", "bar")
                .build();

        assertThat(request.toString(), is("FullHttpRequest{version=HTTP/1.0, method=PATCH, uri=https://hotels.com, " +
                "headers=[headerName=a, Host=hotels.com], cookies=[cfoo=bar], id=id, clientAddress=127.0.0.1:0, secure=true}"));

        assertThat(request.headers("headerName"), is(singletonList("a")));
    }

    @Test
    public void canModifyPreviouslyCreatedRequest() {
        FullHttpRequest<String> request = get("/foo")
                .header("remove", "remove")
                .build();

        FullHttpRequest<String> newRequest = request.newBuilder()
                .method(DELETE)
                .uri("/home")
                .header("remove", "notanymore")
                .clientAddress(new InetSocketAddress("localhost", 80))
                .build();

        assertThat(newRequest.method(), is(DELETE));
        assertThat(newRequest.url().path(), is("/home"));
        assertThat(newRequest.headers(), hasItem(header("remove", "notanymore")));
        assertThat(newRequest.clientAddress(), is(new InetSocketAddress("localhost", 80)));
    }

    @Test
    public void shouldDetermineIfRequestIsSecure() {
        assertThat(get("https://hotels.com").build().isSecure(), is(true));
        assertThat(get("http://hotels.com").build().isSecure(), is(false));
    }

    @Test
    public void decodesQueryParams() {
        FullHttpRequest<String> request = get("http://example.com/?foo=bar")
                .build();
        assertThat(request.queryParam("foo"), isValue("bar"));
    }

    @Test
    public void decodesQueryParamsContainingEncodedEquals() {
        FullHttpRequest<String> request = get("http://example.com/?foo=a%2Bb%3Dc")
                .build();
        assertThat(request.queryParam("foo"), isValue("a+b=c"));
    }

    @Test
    public void createsRequestBuilderFromRequest() {
        FullHttpRequest<String> originalRequest = get("/home")
                .addCookie(cookie("fred", "blogs"))
                .header("some", "header")
                .build();

        FullHttpRequest<String> clonedRequest = originalRequest.newBuilder().build();

        assertThat(clonedRequest.method(), is(originalRequest.method()));
        assertThat(clonedRequest.url(), is(originalRequest.url()));
        assertThat(clonedRequest.headers().toString(), is(originalRequest.headers().toString()));
        assertThat(clonedRequest.body(), is(originalRequest.body()));
    }

    @Test
    public void extractsSingleQueryParameter() {
        FullHttpRequest<String> req = get("http://host.com:8080/path?fish=cod&fruit=orange")
                .build();
        assertThat(req.queryParam("fish"), isValue("cod"));
    }

    @Test
    public void extractsMultipleQueryParameterValues() {
        FullHttpRequest<String> req = get("http://host.com:8080/path?fish=cod&fruit=orange&fish=smørflyndre").build();
        assertThat(req.queryParams("fish"), contains("cod", "smørflyndre"));
    }

    @Test
    public void extractsMultipleQueryParams() {
        FullHttpRequest<String> req = get("http://example.com?foo=bar&foo=hello&abc=def")
                .build();

        assertThat(req.queryParamNames(), containsInAnyOrder("foo", "abc"));

        assertThat(req.queryParams(), isMap(ImmutableMap.of(
                "foo", asList("bar", "hello"),
                "abc", singletonList("def")
        )));
    }

    @Test
    public void alwaysReturnsEmptyListWhenThereIsNoQueryString() {
        FullHttpRequest<String> req = get("http://host.com:8080/path").build();
        assertThat(req.queryParams("fish"), is(emptyIterable()));
        assertThat(req.queryParam("fish"), isAbsent());
    }

    @Test
    public void returnsEmptyListWhenThereIsNoSuchParameter() {
        FullHttpRequest<String> req = get("http://host.com:8080/path?poisson=cabillaud").build();
        assertThat(req.queryParams("fish"), is(emptyIterable()));
        assertThat(req.queryParam("fish"), isAbsent());
    }

    @Test
    public void canExtractCookies() {
        FullHttpRequest<String> request = get("/")
                .addCookie("cookie1", "foo")
                .addCookie("cookie3", "baz")
                .addCookie("cookie2", "bar")
                .build();

        assertThat(request.cookie("cookie1"), isValue(cookie("cookie1", "foo")));
        assertThat(request.cookie("cookie2"), isValue(cookie("cookie2", "bar")));
        assertThat(request.cookie("cookie3"), isValue(cookie("cookie3", "baz")));
    }

    @Test
    public void cannotExtractNonExistentCookie() {
        FullHttpRequest<String> request = get("/")
                .addCookie("cookie1", "foo")
                .addCookie("cookie3", "baz")
                .addCookie("cookie2", "bar")
                .build();

        assertThat(request.cookie("cookie4"), isAbsent());
    }

    @Test
    public void extractsAllCookies() {
        FullHttpRequest<String> request = get("/")
                .addCookie("cookie1", "foo")
                .addCookie("cookie3", "baz")
                .addCookie("cookie2", "bar")
                .build();

        assertThat(request.cookies(), containsInAnyOrder(
                cookie("cookie1", "foo"),
                cookie("cookie2", "bar"),
                cookie("cookie3", "baz")));
    }

    @Test
    public void extractsEmptyIterableIfCookieHeaderNotSet() {
        FullHttpRequest<String> request = get("/").build();
        assertThat(request.cookies(), is(emptyIterable()));
    }

    @Test
    public void canRemoveAHeader() {
        Object hdValue = "b";
        FullHttpRequest<String> request = get("/")
                .header("a", hdValue)
                .addHeader("c", hdValue)
                .build();
        FullHttpRequest<String> shouldRemoveHeader = request.newBuilder()
                .removeHeader("c")
                .build();

        assertThat(shouldRemoveHeader.headers(), contains(header("a", "b")));
    }

    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "cookieHeaderName")
    public void willNotAllowCookieHeaderToBeSet(CharSequence cookieHeaderName) {
        get("/").header(cookieHeaderName, "Value");
    }

    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "cookieHeaderName")
    public void willNotAllowCookieHeaderToBeAdded(CharSequence cookieHeaderName) {
        get("/").addHeader(cookieHeaderName, "Value");
    }

    @DataProvider(name = "cookieHeaderName")
    private Object[][] cookieHeaderName() {
        return new Object[][]{
                {COOKIE},
                {"Cookie"},
                {"cookie"},
                {"COOKIE"}
        };
    }

    @Test
    public void removesCookies() throws Exception {
        FullHttpRequest<String> request = get("/")
                .addCookie("lang", "en_US|en-us_hotels_com")
                .addCookie("styx_origin_hpt", "hpt1")
                .removeCookie("lang")
                .build();
        assertThat(request.cookies(), contains(cookie("styx_origin_hpt", "hpt1")));
    }

    @Test
    public void removesACookieSetInCookie() throws Exception {
        FullHttpRequest<String> request = get("/")
                .addCookie("lang", "en_US|en-us_hotels_com")
                .addCookie("styx_origin_hpt", "hpt1")
                .removeCookie("lang")
                .build();
        assertThat(request.cookies(), contains(cookie("styx_origin_hpt", "hpt1")));
    }

    @Test
    public void shouldSetsContentLengthForNonStreamingBodyMessage() throws Exception {
        assertThat(put("/home").body("").build().header(CONTENT_LENGTH), isValue("0"));
        assertThat(put("/home").body("Hello").build().header(CONTENT_LENGTH), isValue(valueOf(bytes("Hello").length)));
        assertThat(put("/home").body(bytes("Hello")).build().header(CONTENT_LENGTH), isValue(valueOf(bytes("Hello").length)));
        assertThat(put("/home").body(just(Unpooled.copiedBuffer("Hello", UTF_8))).build().header(CONTENT_LENGTH), isAbsent());
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
    public void builderSetsRequestContent() {
        FullHttpRequest<String> request = post("/foo/bar")
                .body("Foo bar")
                .build();

        assertThat(request.body(), is("Foo bar"));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void rejectsNullCookie() {
        get("/").addCookie(null).build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsNullCookieName() {
        get("/").addCookie(null, "value").build();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void rejectsNullCookieValue() {
        get("/").addCookie("name", null).build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsMultipleContentLengthInSingleHeader() throws Exception {
        get("/foo")
                .addHeader(CONTENT_LENGTH, "15, 16")
                .build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsMultipleContentLengthHeaders() throws Exception {
        get("/foo")
                .addHeader(CONTENT_LENGTH, "15")
                .addHeader(CONTENT_LENGTH, "16")
                .build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsInvalidContentLength() throws Exception {
        get("/foo")
                .addHeader(CONTENT_LENGTH, "foo")
                .build();
    }

    @Test
    public void createARequestWithFullUrl() throws Exception {
        FullHttpRequest<String> request = get("http://www.hotels.com")
                .build();

        assertThat(request.url(), is(url("http://www.hotels.com").build()));
    }

    @Test
    public void setsHostHeaderFromAuthorityIfSet() throws Exception {
        FullHttpRequest<String> request = get("http://www.hotels.com")
                .build();

        assertThat(request.header(HOST), isValue("www.hotels.com"));
    }

    @Test
    public void createsANewRequestWithSameVersionAsBefore() {
        FullHttpRequest<String> v10Request = get("/foo/bar").version(HTTP_1_0).build();

        FullHttpRequest<String> newRequest = v10Request.newBuilder().uri("/blah/blah").build();

        assertThat(newRequest.version(), is(HTTP_1_0));
    }

    @Test
    public void builderCopiesClientIpAddress() throws Exception {
        InetSocketAddress address = InetSocketAddress.createUnresolved("styx.io", 8080);
        FullHttpRequest<String> request = post("/foo").clientAddress(address).build();

        FullHttpRequest<String> newRequest = request.newBuilder().build();

        assertThat(newRequest.clientAddress(), is(address));
    }

    private static FullHttpRequest.Builder<String> get(String uri) {
        return FullHttpRequest.get(uri);
    }

    private static FullHttpRequest.Builder<String> post(String uri) {
        return FullHttpRequest.post(uri);
    }
    private static FullHttpRequest.Builder<String> patch(String uri) {
        return FullHttpRequest.patch(uri);
    }
}
