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
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.net.URL;
import java.nio.charset.CharacterCodingException;
import java.util.Optional;

import static com.hotels.styx.api.Url.Authority.authority;
import static com.hotels.styx.api.Url.Builder.url;
import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static com.hotels.styx.support.matchers.MapMatcher.isMap;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;

public class UrlTest {
    @Test
    public void setsSchemeAndAuthorityCorrectly() throws Exception {
        Url url = new Url.Builder()
                .scheme("http")
                .authority("example.com")
                .build();
        assertThat(url.scheme(), is("http"));
        assertThat(url.authority(), is(authority("example.com")));
    }

    @Test
    public void shouldCreateWithSchemeAndHost() throws CharacterCodingException {
        assertThat("http://example.com", is(new Url.Builder()
                .scheme("http")
                .authority("example.com")
                .path("")
                .build()
                .toString()));
    }

    @Test
    public void shouldCreateWithHostAndPort() throws CharacterCodingException {
        assertThat("http://example.com:8080", is(new Url.Builder()
                .scheme("http")
                .authority(authority("example.com", 8080))
                .build()
                .toString()));
    }

    @Test
    public void shouldCreateWithUserAndHostAndPort() throws CharacterCodingException {
        assertThat("http://someone@example.com:8080", is(new Url.Builder()
                .scheme("http")
                .authority(authority("someone", "example.com", 8080))
                .build()
                .toString()));
    }

    @Test
    public void shouldCreateWithPath() throws CharacterCodingException {
        Url url = new Url.Builder()
                .scheme("http")
                .authority("example.com")
                .path("/landing/de3445")
                .build();

        assertThat(url.toString(), is("http://example.com/landing/de3445"));
    }

    @Test
    public void decodesQueryParams() {
        Url url = url("http://example.com/?foo=bar")
                .build();

        System.out.println(url.query());

        assertThat(url.queryParam("foo"), isValue("bar"));
    }

    @Test
    public void decodesQueryParamsContainingEncodedEquals() {
        Url url = url("http://example.com/?foo=a%2Bb%3Dc")
                .build();
        assertThat(url.queryParam("foo"), isValue("a+b=c"));
    }

    @Test
    public void decodesQueryParamsWithMultipleValues() {
        Url url = url("http://example.com?foo=bar&foo=hello")
                .build();

        assertThat(url.queryParams("foo"), containsInAnyOrder("bar", "hello"));
    }

    @Test
    public void decodesMultipleQueryParams() {
        Url url = url("http://example.com?foo=bar&foo=hello&abc=def")
                .build();

        assertThat(url.queryParamNames(), containsInAnyOrder("foo", "abc"));

        assertThat(url.queryParams(), isMap(ImmutableMap.of(
                "foo", asList("bar", "hello"),
                "abc", singletonList("def")
        )));
    }

    @Test
    public void shouldCreateFullyQualifiedUrl() throws Exception {
        Url fqUrl = url("/landing/de3445")
                .authority("example.com")
                .build();

        assertThat(fqUrl, is(url("//example.com/landing/de3445").build()));
    }

    @Test
    public void canDropThePath() throws Exception {
        Url fqUrl = url("/landing/de3445")
                .authority("example.com")
                .build();

        Url newUrl = fqUrl.newBuilder()
                .dropHost()
                .build();

        assertThat(newUrl.authority(), isAbsent());
        assertThat(newUrl.toString(), is("/landing/de3445"));
    }

    @Test
    public void handlesPathSegmentEncodedInUTF8() {
        Url url = url("http://example.com/foo/\u2603")
                .build();
        assertThat(url.encodedUri(), is("http://example.com/foo/\u2603"));
    }

    @Test
    public void extractsTheHostUrl() throws Exception {
        Optional<String> host = url("http://ecom.com").build().host();

        assertThat(host, isValue("ecom.com"));
    }

    @Test
    public void portNumberIsStoredInAuthority() throws Exception {
        int port = url("http://ecom.com:8080")
                .build().authority().get().port();
        assertThat(port, is(8080));
    }

    @Test
    public void absentPortIsStoredInAuthorityAsMinusOne() throws Exception {
        int port = url("http://example.com")
                .build().authority().get().port();
        assertThat(port, is(-1));
    }

    @Test
    public void authorityReturnsHostAndPortStringWithPort() {
        Url.Authority authority = url("http://example.com:8080")
                .build().authority().get();
        assertThat(authority.hostAndPort(), is("example.com:8080"));
    }

    @Test
    public void authorityReturnsHostAndPortStringWithoutPort() {
        Url.Authority authority = url("http://example.com")
                .build().authority().get();
        assertThat(authority.hostAndPort(), is("example.com"));
    }


    @Test
    public void identifiesCorrectlyIfUrlIsFullyQualified() throws Exception {
        Url fqUrl = url("http://example.com").build();
        assertThat(fqUrl.isFullyQualified(), is(true));
        assertThat(fqUrl.isAbsolute(), is(false));
        assertThat(fqUrl.isRelative(), is(true));

        Url nonFqUrl = url("/somepath").build();
        assertThat(nonFqUrl.isFullyQualified(), is(false));
        assertThat(nonFqUrl.isAbsolute(), is(true));
        assertThat(nonFqUrl.isRelative(), is(false));
    }

    @Test
    public void transformsToURL() throws Exception {
        assertThat(url("http://somerandompath?withsomequery=noquery%20encodeme").build().toURL().toString(), is(new URL("http://somerandompath?withsomequery=noquery%20encodeme").toString()));
    }

    @Test
    public void shouldReturnTheRawURIPassedIn() throws Exception {
        assertThat(url("http://example.com/?foo=a%2Bb%3Dc").build().encodedUri(), is("http://example.com/?foo=a%2Bb%3Dc"));
    }

    @Test
    public void canEncodeTheModifiedUrl() throws Exception {
        Url url = url("/search.do?foo=a%2Bb%3Dc").build();
        Url newUrl = url.newBuilder().path("/v1/search").build();
        assertThat(newUrl.encodedUri(), is("/v1/search?foo=a%2Bb%3Dc"));

    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void unwiseCharsAreNotAccepted() throws Exception {
        String urlWithUnwiseChars = "/search.do?foo={&srsReport=Landing|AutoS|HOTEL|Hotel%20Il%20Duca%20D%27Este|0|0|0|2|1|2|284128&srsr=Landing|AutoS|HOTEL|Hotel%20Il%20Duca%20D%27Este|0|0|0|2|1|2|284128";
        url(urlWithUnwiseChars).build();
    }

    @Test
    public void canModifyAComponentOfTheUrl() {
        Url url = url("/somerandompath?withsomequery=noquery").build();
        assertThat(url.newBuilder().path("/newpath").build(), is(url("/newpath?withsomequery=noquery").build()));
    }

    @Test
    public void canDecodeAndEncodeThePath() {
        assertThat(url("/landing/de408991/%D7%9E%D7%9C%D7%95%D7%A0%D7%95%D7%AA-%D7%A7%D7%95%D7%A4%D7%A0%D7%94%D7%92%D7%9F-%D7%93%D7%A0%D7%9E%D7%A8%D7%A7/")
                .build().encodedUri(), is("/landing/de408991/%D7%9E%D7%9C%D7%95%D7%A0%D7%95%D7%AA-%D7%A7%D7%95%D7%A4%D7%A0%D7%94%D7%92%D7%9F-%D7%93%D7%A0%D7%9E%D7%A8%D7%A7/"));
    }

    @Test
    public void shouldPreserveLastTrailingPathSeparator() {
        assertThat(url("/landing/foo/bar").build().encodedUri(), is("/landing/foo/bar"));
        assertThat(url("/landing/foo/bar/").build().encodedUri(), is("/landing/foo/bar/"));
    }

    @Test(dataProvider = "pathSegmentAllowedChars")
    public void shouldNotEncodeAllowedCharactersInPath(String character) {
        String path = "/customercare/subscribe.html" + character + "sessid=nXF5jQ8rTW3bAbh6djb2hYJE3D.web-app-02";
        assertThat(url(path).build().encodedUri(), is(path));
    }
    @Test
    public void spaceIsAlsoPlusIsAlsoHex20() {
        String ENCODED_SPACE = "/customercare/subscribe.html%20sessid=nXF5jQ8rTW3bAbh6djb2hYJE3D.web-app-02";

        String path = "/customercare/subscribe.html+sessid=nXF5jQ8rTW3bAbh6djb2hYJE3D.web-app-02";
        assertThat(url(path).build().encodedUri(), is(path));

        assertThat(url(ENCODED_SPACE).build().encodedUri(), is(ENCODED_SPACE));
    }

    @Test
    public void shouldCreateAValidUrlIfAuthorityIsMissing() {
        Url url = new Url.Builder()
                .scheme("https")
                .path("/")
                .build();

        assertThat(url.toString(), is("/"));
    }

    @Test
    public void acceptsEmptyQueryString() {
        Url url = url("/webapp/assets/images/icons.eot?").build();

        assertThat(url.encodedUri(), is("/webapp/assets/images/icons.eot?"));
        assertThat(url.queryParams().isEmpty(), is(true));
    }

    @Test
    public void exposesDecodedPath() throws Exception {
        Url url = Url.Builder.url("http://localhost/foo%20bar").build();
        assertThat(url.toURI().getPath(), is("/foo bar"));
    }

    @Test
    public void exposesRawPath() throws Exception {
        Url url = Url.Builder.url("http://localhost/foo%20bar").build();
        assertThat(url.toURI().getRawPath(), is("/foo%20bar"));
    }

    @DataProvider(name = "pathSegmentAllowedChars")
    public static Object[][] pathSegmentAllowedChars() {
        return new Object[][]{
                {":"},
                {"@"},
                {"-"},
                {"."},
                {"_"},
                {"~"},
                {"!"},
                {"$"},
                {"&"},
                {"'"},
                {"("},
                {")"},
                {"*"},
                {"+"},
                {","},
                {";"},
                {"="}
        };
    }
}