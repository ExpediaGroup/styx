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
import com.google.common.collect.ImmutableMap;
import com.hotels.styx.api.messages.FullHttpRequest;
import com.hotels.styx.api.messages.HttpMethod;
import com.hotels.styx.api.messages.HttpVersion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import rx.Observable;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.stream.Stream;

import static com.hotels.styx.api.HttpCookie.cookie;
import static com.hotels.styx.api.HttpHeader.header;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.COOKIE;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.HttpRequest.Builder.patch;
import static com.hotels.styx.api.HttpRequest.Builder.post;
import static com.hotels.styx.api.HttpRequest.Builder.put;
import static com.hotels.styx.api.TestSupport.bodyAsString;
import static com.hotels.styx.api.Url.Builder.url;
import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static com.hotels.styx.support.matchers.MapMatcher.isMap;
import static io.netty.handler.codec.http.HttpMethod.DELETE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_0;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.ErrorDataDecoderException;
import static java.lang.String.valueOf;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static rx.Observable.empty;
import static rx.Observable.just;

public class HttpRequestTest {

    @Test
    public void createsARequestWithDefaultValues() {
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

        assertThat(request.body().content().isEmpty().toBlocking().first(), is(true));
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
                .header("singleValue", "a")
                .header("multipleValue", asList("a", "b"))
                .header("singleValueObject", (Object) "a")
                .addCookie("cfoo", "bar")
                .build();

        assertThat(request.toString(), is("HttpRequest{version=HTTP/1.0, method=PATCH, uri=https://hotels.com, " +
                "headers=[singleValue=a, multipleValue=a, multipleValue=b, singleValueObject=a, Host=hotels.com], cookies=[cfoo=bar], id=id, clientAddress=127.0.0.1:0}"));

        assertThat(request.headers("singleValue"), is(singletonList("a")));
        assertThat(request.headers("multipleValue"), is(asList("a", "b")));
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
        HttpRequest request = get("http://example.com/?foo=bar")
                .build();
        assertThat(request.queryParam("foo").get(), is("bar"));
    }

    @Test
    public void decodesQueryParamsContainingEncodedEquals() {
        HttpRequest request = get("http://example.com/?foo=a%2Bb%3Dc")
                .build();
        assertThat(request.queryParam("foo").get(), is("a+b=c"));
    }

    @Test
    public void decodesPostParams() {
        HttpRequest request = post("http://example.com/")
                .body("foo=bar")
                .build();
        HttpRequest.DecodedRequest<FormData> formData = request.decodePostParams(100).toBlocking().first();
        assertThat(formData.body().postParam("foo"), is(Optional.of("bar")));
    }

    @Test
    public void decodesMultipleParameters() {
        HttpRequest request = post("http://example.com/")
                .body("foo=bar&baz=qux")
                .build();
        HttpRequest.DecodedRequest<FormData> decodedFormData = request.decodePostParams(100).toBlocking().first();
        assertThat(decodedFormData.body().postParam("foo"), is(Optional.of("bar")));
        assertThat(decodedFormData.body().postParam("baz"), is(Optional.of("qux")));
    }

    @Test
    public void decodesPostParamsWithOnlyKey() {
        HttpRequest request = post("http://example.com/")
                .body("foo")
                .build();
        HttpRequest.DecodedRequest<FormData> decodedFormData = request.decodePostParams(100).toBlocking().first();
        assertThat(decodedFormData.body().postParam("foo"), is(Optional.empty()));
    }

    @Test
    public void decodesApplicationJsonData() {
        String jsonObject = "{ \"foo\": \"bar\" }";
        HttpRequest request = post("http://example.com/")
                .body(jsonObject)
                .build();
        HttpRequest.DecodedRequest<String> decodedRequest = request.decode((bb) -> bb.toString(Charsets.UTF_8), 100).toBlocking().first();
        assertThat(decodedRequest.body(), is(jsonObject));
    }

    @Test(expectedExceptions = ErrorDataDecoderException.class)
    public void handlesIncompletePostParamsWithNoKey() {
        HttpRequest request = post("http://example.com/")
                .body("=bar")
                .build();
        request.decodePostParams(100).toBlocking().first();
    }

    @Test(expectedExceptions = ErrorDataDecoderException.class)
    public void handlesIncompletePostParamsWithNoKeyAndNoValue() {
        HttpRequest request = post("http://example.com/")
                .body("=")
                .build();
        request.decodePostParams(100).toBlocking().first();
    }

    @Test
    public void handlesIncompletePostParamsWithExtraDelimiter() {
        HttpRequest request = post("http://example.com/")
                .body("foo=bar&")
                .build();
        HttpRequest.DecodedRequest<FormData> decodedFormData = request.decodePostParams(100).toBlocking().first();
        assertThat(decodedFormData.body().postParam("foo"), is(Optional.of("bar")));
    }

    @Test(expectedExceptions = ContentOverflowException.class)
    public void raiseExceptionWhenContentOverflows() {
        HttpRequest request = post("http://example.com/")
                .body("foo=bar")
                .build();
        request.decodePostParams(1).toBlocking().first();
    }

    @Test
    public void createsRequestBuilderFromRequest() {
        HttpRequest originalRequest = get("/home")
                .addCookie(cookie("fred", "blogs"))
                .header("some", "header")
                .build();

        HttpRequest clonedRequest = originalRequest.newBuilder().build();

        assertThat(clonedRequest.method(), is(equalTo(originalRequest.method())));
        assertThat(clonedRequest.url(), is(equalTo(originalRequest.url())));
        assertThat(clonedRequest.headers().toString(), is(equalTo(originalRequest.headers().toString())));
        assertThat(clonedRequest.body().toString(), is(equalTo(originalRequest.body().toString())));
    }

    @Test
    public void extractsSingleQueryParameter() {
        HttpRequest req = get("http://host.com:8080/path?fish=cod&fruit=orange")
                .build();
        assertThat(req.queryParam("fish").get(), is("cod"));
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
                .addCookie("cookie1", "foo")
                .addCookie("cookie3", "baz")
                .addCookie("cookie2", "bar")
                .build();

        assertThat(request.cookie("cookie1").get(), is(cookie("cookie1", "foo")));
        assertThat(request.cookie("cookie2").get(), is(cookie("cookie2", "bar")));
        assertThat(request.cookie("cookie3").get(), is(cookie("cookie3", "baz")));
    }

    @Test
    public void cannotExtractNonExistentCookie() {
        HttpRequest request = get("/")
                .addCookie("cookie1", "foo")
                .addCookie("cookie3", "baz")
                .addCookie("cookie2", "bar")
                .build();

        assertThat(request.cookie("cookie4"), isAbsent());
    }

    @Test
    public void extractsAllCookies() {
        HttpRequest request = get("/")
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

    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "cookieHeaderName")
    public void willNotAllowCookieHeaderToBeSet(CharSequence cookieHeaderName) {
        get("/").header(cookieHeaderName, "Value");
    }

    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "cookieHeaderName")
    public void willNotAllowCookieHeaderToBeSetAsObject(CharSequence cookieHeaderName) {
        get("/").header(cookieHeaderName, (Object) "Value");
    }

    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "cookieHeaderName")
    public void willNotAllowCookieHeaderToBeSetAsIterable(CharSequence cookieHeaderName) {
        get("/").header(cookieHeaderName, asList("Value1", "Value2"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "cookieHeaderName")
    public void willNotAllowCookieHeaderToBeSetInHttpHeaders(CharSequence cookieHeaderName) {
        get("/").headers(header(cookieHeaderName.toString(), "value"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "cookieHeaderName")
    public void willNotAllowCookieHeaderToBeAdded(CharSequence cookieHeaderName) {
        get("/").addHeader(cookieHeaderName, "Value");
    }

    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "cookieHeaderName")
    public void willNotAllowCookieHeaderToBeAddedAsObject(CharSequence cookieHeaderName) {
        get("/").addHeader(cookieHeaderName, (Object) "Value");
    }

    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "cookieHeaderName")
    public void willNotAllowCookieHeaderToBeAddedAsIterable(CharSequence cookieHeaderName) {
        get("/").addHeader(cookieHeaderName, asList("Value1", "Value2"));
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
        HttpRequest request = get("/")
                .addCookie("lang", "en_US|en-us_hotels_com")
                .addCookie("styx_origin_hpt", "hpt1")
                .removeCookie("lang")
                .build();
        assertThat(request.cookies(), contains(cookie("styx_origin_hpt", "hpt1")));
    }

    @Test
    public void removesACookieSetInCookie() throws Exception {
        HttpRequest request = get("/")
                .addCookie("lang", "en_US|en-us_hotels_com")
                .addCookie("styx_origin_hpt", "hpt1")
                .removeCookie("lang")
                .build();
        assertThat(request.cookies(), contains(cookie("styx_origin_hpt", "hpt1")));
    }

    @Test
    public void shouldSetsContentLengthForNonStreamingBodyMessage() throws Exception {
        assertThat(put("/home").body("").build().header(CONTENT_LENGTH).get(), equalTo("0"));
        assertThat(put("/home").body("Hello").build().header(CONTENT_LENGTH).get(), equalTo(valueOf(bytes("Hello").length)));
        assertThat(put("/home").body(bytes("Hello")).build().header(CONTENT_LENGTH).get(), equalTo(valueOf(bytes("Hello").length)));
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
        HttpRequest request = post("/foo/bar")
                .body("Foo bar")
                .build();

        assertThat(bodyAsString(request.body()), is("Foo bar"));
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
        HttpRequest request = get("http://www.hotels.com")
                .build();

        assertThat(request.url(), is(url("http://www.hotels.com").build()));
    }

    @Test
    public void setsHostHeaderFromAuthorityIfSet() throws Exception {
        HttpRequest request = get("http://www.hotels.com")
                .build();

        assertThat(request.header(HOST).get(), is("www.hotels.com"));

    }

    @Test
    public void createsANewRequestWithSameVersionAsBefore() {
        HttpRequest v10Request = get("/foo/bar").version(HTTP_1_0).build();

        HttpRequest newRequest = v10Request.newBuilder().uri("/blah/blah").build();

        assertThat(newRequest.version(), is(HTTP_1_0));
    }

    @Test
    public void shouldCreateAChunkedResponse() {
        assertThat(post("/foo").build().chunked(), is(false));
        assertThat(post("/foo").chunked().build().chunked(), is(true));
    }

    @Test
    public void shouldRemoveContentLengthFromChunkedMessages() {
        HttpRequest request = post("/foo").header(CONTENT_LENGTH, 5).build();
        HttpRequest chunkedRequest = request.newBuilder().chunked().build();

        assertThat(chunkedRequest.chunked(), is(true));
        assertThat(chunkedRequest.header(CONTENT_LENGTH).isPresent(), is(false));
    }

    @Test
    public void shouldNotFailToRemoveNonExistentContentLength() {
        HttpRequest request = post("/foo").build();
        HttpRequest chunkedRequest = request.newBuilder().chunked().build();

        assertThat(chunkedRequest.chunked(), is(true));
        assertThat(chunkedRequest.header(CONTENT_LENGTH).isPresent(), is(false));
    }

    @Test
    public void builderCopiesClientIpAddress() throws Exception {
        InetSocketAddress address = InetSocketAddress.createUnresolved("styx.io", 8080);
        HttpRequest request = post("/foo").clientAddress(address).build();

        HttpRequest newRequest = request.newBuilder().build();

        assertThat(newRequest.clientAddress(), is(address));
    }

    @Test
    public void decodesToFullHttpRequest() throws Exception {
        HttpRequest request = post("/foo/bar")
                .clientAddress(InetSocketAddress.createUnresolved("example.org", 8080))
                .secure(true)
                .version(HTTP_1_0)
                .header("HeaderName", "HeaderValue")
                .addCookie("CookieName", "CookieValue")
                .body(stream("foo", "bar", "baz"))
                .build();

        FullHttpRequest full = request.toFullRequest(0x100000)
                .toBlocking()
                .single();

        assertThat(full.method(), is(HttpMethod.POST));
        assertThat(full.isSecure(), is(true));
        assertThat(full.version(), is(HttpVersion.HTTP_1_0));
        assertThat(full.headers(), contains(header("HeaderName", "HeaderValue")));
        assertThat(full.cookies(), contains(cookie("CookieName", "CookieValue")));
        assertThat(full.url().toString(), is("/foo/bar"));
        assertThat(full.bodyAs(UTF_8), is("foobarbaz"));
    }

    @Test
    public void decodesToFullHttpRequestWithEmptyBody() throws Exception {
        HttpRequest request = get("/foo/bar")
                .body(empty())
                .build();

        FullHttpRequest full = request.toFullRequest(0x100000)
                .toBlocking()
                .single();

        assertThat(full.url().toString(), is("/foo/bar"));
        assertThat(full.bodyAs(UTF_8), is(""));
    }

    @Test
    public void decodingToFullHttpRequestDefaultsToUTF8() throws Exception {
        HttpRequest request = get("/foo/bar")
                .body(stream("foo", "bar", "baz"))
                .build();

        FullHttpRequest full = request.toFullRequest(0x100000)
                .toBlocking()
                .single();

        assertThat(full.url().toString(), is("/foo/bar"));
        assertThat(full.bodyAs(UTF_8), is("foobarbaz"));
    }

    @Test
    public void retainsClientAddressAfterConversionToFullHttpMessage() {
        InetSocketAddress address = InetSocketAddress.createUnresolved("styx.io", 8080);
        HttpRequest original = HttpRequest.Builder.get("/")
                .clientAddress(address)
                .build();

        FullHttpRequest fullRequest = original
                .toFullRequest(100)
                .toBlocking()
                .first();

        HttpRequest streaming = fullRequest.toStreamingRequest();

        assertThat(streaming.clientAddress().getHostName(), is("styx.io"));
        assertThat(streaming.clientAddress().getPort(), is(8080));
    }

    private static Observable<ByteBuf> stream(String... strings) {
        return Observable.from(Stream.of(strings)
                .map(string -> Unpooled.copiedBuffer(string, UTF_8))
                .collect(toList()));
    }
}
