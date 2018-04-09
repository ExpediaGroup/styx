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
package com.hotels.styx.api.messages;

import com.google.common.collect.ImmutableMap;
import com.hotels.styx.api.HttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import rx.Observable;
import rx.observers.TestSubscriber;

import java.util.Optional;

import static com.hotels.styx.api.HttpCookie.cookie;
import static com.hotels.styx.api.HttpHeader.header;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.COOKIE;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpRequest.Builder.put;
import static com.hotels.styx.api.Url.Builder.url;
import static com.hotels.styx.api.messages.FullHttpRequest.get;
import static com.hotels.styx.api.messages.FullHttpRequest.patch;
import static com.hotels.styx.api.messages.HttpMethod.DELETE;
import static com.hotels.styx.api.messages.HttpMethod.GET;
import static com.hotels.styx.api.messages.HttpMethod.POST;
import static com.hotels.styx.api.messages.HttpVersion.HTTP_1_0;
import static com.hotels.styx.api.messages.HttpVersion.HTTP_1_1;
import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static com.hotels.styx.support.matchers.MapMatcher.isMap;
import static java.lang.String.valueOf;
import static java.nio.charset.StandardCharsets.UTF_16;
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
import static rx.Observable.just;


public class FullHttpRequestTest {
    @Test
    public void convertsToStreamingHttpRequest() {
        FullHttpRequest fullRequest = new FullHttpRequest.Builder(POST, "/foo/bar").body("foobar", UTF_8)
                .secure(true)
                .version(HTTP_1_1)
                .header("HeaderName", "HeaderValue")
                .addCookie("CookieName", "CookieValue")
                .build();

        HttpRequest streaming = fullRequest.toStreamingRequest();

        assertThat(streaming.method(), is(HttpMethod.POST));
        assertThat(streaming.url(), is(url("/foo/bar").build()));
        assertThat(streaming.isSecure(), is(true));
        assertThat(streaming.version(), is(io.netty.handler.codec.http.HttpVersion.HTTP_1_1));
        assertThat(streaming.headers(), containsInAnyOrder(
                header("Content-Length", "6"),
                header("HeaderName", "HeaderValue")));
        assertThat(streaming.cookies(), contains(cookie("CookieName", "CookieValue")));

        String body = streaming.body()
                .aggregate(0x100000)
                .map(byteBuf -> byteBuf.toString(UTF_8))
                .toBlocking()
                .single();

        assertThat(body, is("foobar"));
    }

    @Test(dataProvider = "emptyBodyRequests")
    public void convertsToStreamingHttpRequestWithEmptyBody(FullHttpRequest fullRequest) {
        HttpRequest streaming = fullRequest.toStreamingRequest();

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
                {get("/foo/bar").build()},
                {new FullHttpRequest.Builder(POST, "/foo/bar").body(null, UTF_8).build()},
                {new FullHttpRequest.Builder(POST, "/foo/bar").body("", UTF_8).build()},
                {new FullHttpRequest.Builder(POST, "/foo/bar").body(null, UTF_8, true).build()},
                {new FullHttpRequest.Builder(POST, "/foo/bar").body("", UTF_8, true).build()},
                {new FullHttpRequest.Builder(POST, "/foo/bar").body(null, true).build()},
                {new FullHttpRequest.Builder(POST, "/foo/bar").body(new byte[0], true).build()},
        };
    }

    @Test
    public void createsARequestWithDefaultValues() {
        FullHttpRequest request = get("/index").build();
        assertThat(request.version(), is(HTTP_1_1));
        assertThat(request.url().toString(), is("/index"));
        assertThat(request.path(), is("/index"));
        assertThat(request.id(), is(notNullValue()));
        assertThat(request.isSecure(), is(false));
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
        FullHttpRequest request = patch("https://hotels.com")
                .version(HTTP_1_1)
                .id("id")
                .header("headerName", "a")
                .addCookie("cfoo", "bar")
                .build();

        assertThat(request.toString(), is("FullHttpRequest{version=HTTP/1.1, method=PATCH, uri=https://hotels.com, " +
                "headers=[headerName=a, Host=hotels.com], cookies=[cfoo=bar], id=id, secure=true}"));

        assertThat(request.headers("headerName"), is(singletonList("a")));
    }

    @Test
    public void transformsRequest() {
        FullHttpRequest request = get("/foo")
                .header("remove", "remove")
                .build();

        FullHttpRequest newRequest = request.newBuilder()
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
        FullHttpRequest request = FullHttpRequest.get("/")
                .body("Response content.", UTF_16)
                .build();

        assertThat(request.body(), is("Response content.".getBytes(UTF_16)));
        assertThat(request.header("Content-Length"), is(Optional.of("36")));
    }

    @Test(expectedExceptions = NullPointerException.class, expectedExceptionsMessageRegExp = "Charset is not provided.")
    public void contentFromStringOnlyThrowsNPEWhenCharsetIsNull() {
        FullHttpRequest.get("/")
                .body("Response content.", null)
                .build();
    }

    @Test
    public void contentFromStringSetsContentLengthIfRequired() {
        FullHttpRequest request1 = FullHttpRequest.get("/")
                .body("Response content.", UTF_8, true)
                .build();

        assertThat(request1.header("Content-Length"), is(Optional.of("17")));

        FullHttpRequest request2 = FullHttpRequest.get("/")
                .body("Response content.", UTF_8, false)
                .build();

        assertThat(request2.header("Content-Length"), is(Optional.empty()));
    }

    @Test(expectedExceptions = NullPointerException.class, expectedExceptionsMessageRegExp = "Charset is not provided.")
    public void contentFromStringThrowsNPEWhenCharsetIsNull() {
        FullHttpRequest.get("/")
                .body("Response content.", null, false)
                .build();
    }

    @Test
    public void contentFromByteArraySetsContentLengthIfRequired() {
        FullHttpRequest response1 = FullHttpRequest.get("/")
                .body("Response content.".getBytes(UTF_16), true)
                .build();
        assertThat(response1.body(), is("Response content.".getBytes(UTF_16)));
        assertThat(response1.header("Content-Length"), is(Optional.of("36")));

        FullHttpRequest response2 = FullHttpRequest.get("/")
                .body("Response content.".getBytes(UTF_8), false)
                .build();

        assertThat(response2.body(), is("Response content.".getBytes(UTF_8)));
        assertThat(response2.header("Content-Length"), is(Optional.empty()));
    }


    @Test
    public void requestBodyIsImmutable() {
        FullHttpRequest request = get("/foo")
                .body("Original body", UTF_8)
                .build();

        request.body()[0] = 'A';

        assertThat(request.bodyAs(UTF_8), is("Original body"));
    }

    @Test
    public void requestBodyCannotBeChangedViaStreamingRequest() {
        FullHttpRequest original = FullHttpRequest.get("/foo")
                .body("original", UTF_8)
                .build();

        ByteBuf byteBuf = original.toStreamingRequest()
                .body()
                .content()
                .toBlocking()
                .first();

        byteBuf.array()[0] = 'A';

        assertThat(original.bodyAs(UTF_8), is("original"));
    }

    @Test
    public void requestBodyCannotBeChangedViaStreamingRequest2() {
        ByteBuf content = Unpooled.copiedBuffer("original", UTF_8);

        HttpRequest original = HttpRequest.Builder.get("/foo")
                .body(content)
                .build();

        FullHttpRequest fullRequest = original.toFullRequest(100)
                .toBlocking()
                .first();

        content.array()[0] = 'A';

        assertThat(fullRequest.bodyAs(UTF_8), is("original"));
    }

    @Test
    public void requestBodyCannotBeChangedViaStreamingRequest3() {
        ByteBuf content = Unpooled.copiedBuffer("original", UTF_8);

        HttpRequest original = HttpRequest.Builder.get("/foo")
                .body(Observable.just(content))
                .build();

        FullHttpRequest fullRequest = original.toFullRequest(100)
                .toBlocking()
                .first();

        content.array()[0] = 'A';

        assertThat(fullRequest.bodyAs(UTF_8), is("original"));
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

    @Test
    public void shouldDetermineIfRequestIsSecure() {
        assertThat(get("https://hotels.com").build().isSecure(), is(true));
        assertThat(get("http://hotels.com").build().isSecure(), is(false));
    }

    @Test
    public void decodesQueryParams() {
        FullHttpRequest request = get("http://example.com/?foo=bar").build();
        assertThat(request.queryParam("foo"), isValue("bar"));
    }

    @Test
    public void decodesQueryParamsContainingEncodedEquals() {
        FullHttpRequest request = get("http://example.com/?foo=a%2Bb%3Dc")
                .build();
        assertThat(request.queryParam("foo"), isValue("a+b=c"));
    }

    @Test
    public void createsRequestBuilderFromRequest() {
        FullHttpRequest originalRequest = get("/home")
                .addCookie(cookie("fred", "blogs"))
                .header("some", "header")
                .build();

        FullHttpRequest clonedRequest = originalRequest.newBuilder().build();

        assertThat(clonedRequest.method(), is(originalRequest.method()));
        assertThat(clonedRequest.url(), is(originalRequest.url()));
        assertThat(clonedRequest.headers().toString(), is(originalRequest.headers().toString()));
        assertThat(clonedRequest.body(), is(originalRequest.body()));
    }

    @Test
    public void extractsSingleQueryParameter() {
        FullHttpRequest req = get("http://host.com:8080/path?fish=cod&fruit=orange")
                .build();
        assertThat(req.queryParam("fish"), isValue("cod"));
    }

    @Test
    public void extractsMultipleQueryParameterValues() {
        FullHttpRequest req = get("http://host.com:8080/path?fish=cod&fruit=orange&fish=smørflyndre").build();
        assertThat(req.queryParams("fish"), contains("cod", "smørflyndre"));
    }

    @Test
    public void extractsMultipleQueryParams() {
        FullHttpRequest req = get("http://example.com?foo=bar&foo=hello&abc=def")
                .build();

        assertThat(req.queryParamNames(), containsInAnyOrder("foo", "abc"));

        assertThat(req.queryParams(), isMap(ImmutableMap.of(
                "foo", asList("bar", "hello"),
                "abc", singletonList("def")
        )));
    }

    @Test
    public void alwaysReturnsEmptyListWhenThereIsNoQueryString() {
        FullHttpRequest req = get("http://host.com:8080/path").build();
        assertThat(req.queryParams("fish"), is(emptyIterable()));
        assertThat(req.queryParam("fish"), isAbsent());
    }

    @Test
    public void returnsEmptyListWhenThereIsNoSuchParameter() {
        FullHttpRequest req = get("http://host.com:8080/path?poisson=cabillaud").build();
        assertThat(req.queryParams("fish"), is(emptyIterable()));
        assertThat(req.queryParam("fish"), isAbsent());
    }

    @Test
    public void canExtractCookies() {
        FullHttpRequest request = get("/")
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
        FullHttpRequest request = get("/")
                .addCookie("cookie1", "foo")
                .addCookie("cookie3", "baz")
                .addCookie("cookie2", "bar")
                .build();

        assertThat(request.cookie("cookie4"), isAbsent());
    }

    @Test
    public void extractsAllCookies() {
        FullHttpRequest request = get("/")
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
        FullHttpRequest request = get("/").build();
        assertThat(request.cookies(), is(emptyIterable()));
    }

    @Test
    public void canRemoveAHeader() {
        Object hdValue = "b";
        FullHttpRequest request = get("/")
                .header("a", hdValue)
                .addHeader("c", hdValue)
                .build();
        FullHttpRequest shouldRemoveHeader = request.newBuilder()
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
    public void removesCookies() {
        FullHttpRequest request = get("/")
                .addCookie("lang", "en_US|en-us_hotels_com")
                .addCookie("styx_origin_hpt", "hpt1")
                .removeCookie("lang")
                .build();
        assertThat(request.cookies(), contains(cookie("styx_origin_hpt", "hpt1")));
    }

    @Test
    public void removesACookieSetInCookie() {
        FullHttpRequest request = get("/")
                .addCookie("lang", "en_US|en-us_hotels_com")
                .addCookie("styx_origin_hpt", "hpt1")
                .removeCookie("lang")
                .build();
        assertThat(request.cookies(), contains(cookie("styx_origin_hpt", "hpt1")));
    }

    @Test
    public void shouldSetsContentLengthForNonStreamingBodyMessage() {
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
    public void createARequestWithFullUrl() {
        FullHttpRequest request = get("http://www.hotels.com").build();

        assertThat(request.url(), is(url("http://www.hotels.com").build()));
    }

    @Test
    public void setsHostHeaderFromAuthorityIfSet() {
        FullHttpRequest request = get("http://www.hotels.com").build();

        assertThat(request.header(HOST), isValue("www.hotels.com"));
    }

    @Test
    public void createsANewRequestWithSameVersionAsBefore() {
        FullHttpRequest v10Request = get("/foo/bar").version(HTTP_1_0).build();

        FullHttpRequest newRequest = v10Request.newBuilder().uri("/blah/blah").build();

        assertThat(newRequest.version(), is(HTTP_1_0));
    }

}
