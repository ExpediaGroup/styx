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
package com.hotels.styx.api.matchers;

import com.hotels.styx.api.HttpHeader;
import com.hotels.styx.api.HttpHeaderNames;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import static com.google.common.net.HttpHeaders.CACHE_CONTROL;
import static org.hamcrest.Matchers.is;

public class HeaderMatcher extends TypeSafeMatcher<HttpHeader> {
    private final CharSequence name;
    private final Matcher<String> value;

    @Factory
    public static Matcher<HttpHeader> header(CharSequence name, Matcher<String> matcher) {
        return new HeaderMatcher(name, matcher);
    }

    @Factory
    public static Matcher<HttpHeader> header(CharSequence name, String value) {
        return new HeaderMatcher(name, is(value));
    }

    @Factory
    public static Matcher<HttpHeader> contentType(String value) {
        return new HeaderMatcher(HttpHeaderNames.CONTENT_TYPE, is(value));
    }

    @Factory
    public static Matcher<HttpHeader> cacheControl(String value) {
        return new HeaderMatcher(CACHE_CONTROL, is(value));
    }

    @Factory
    public static Matcher<HttpHeader> header(HttpHeader header) {
        return new HeaderMatcher(header.name(), is(header.value()));
    }

    private HeaderMatcher(CharSequence name, Matcher<String> value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public boolean matchesSafely(HttpHeader actual) {
        return actual.name().equalsIgnoreCase(name.toString()) && value.matches(actual.value());
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("header ").appendValue(name).appendText(" with value of ");
        value.describeTo(description);
    }


}
