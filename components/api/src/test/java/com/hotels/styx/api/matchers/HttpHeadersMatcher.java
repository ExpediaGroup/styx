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
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.hamcrest.core.IsCollectionContaining;

import java.util.List;

import static com.google.common.collect.Iterables.all;
import static com.hotels.styx.api.matchers.HeaderMatcher.header;
import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.util.Arrays.asList;

public class HttpHeadersMatcher extends TypeSafeMatcher<Iterable<HttpHeader>> {
    private static final String START = lineSeparator();
    private static final String END = lineSeparator();
    private final List<Matcher<HttpHeader>> matchers;

    @Factory
    public static Matcher<Iterable<HttpHeader>> isNotCacheable() {
        return new HttpHeadersMatcher(asList(
                header("Pragma", "no-cache"),
                header("Expires", "Mon, 1 Jan 2007 08:00:00 GMT"),
                header("Cache-Control", "no-cache,must-revalidate,no-store")));
    }

    @Factory
    @SuppressWarnings ("unchecked")
    public static Matcher<Iterable<HttpHeader>> hasHeaders(Matcher<HttpHeader>... matchers) {
        return new HttpHeadersMatcher(asList(matchers));
    }

    public HttpHeadersMatcher(List<Matcher<HttpHeader>> matchers) {
        this.matchers = matchers;
    }

    @Override
    public boolean matchesSafely(Iterable<HttpHeader> actual) {
        return all(matchers, elementMatcher -> new IsCollectionContaining<>(elementMatcher).matches(actual));
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("headers to contain")
                .appendText(lineSeparator())
                .appendList(START, format(" and %s%s", lineSeparator(), " "), END, matchers);
    }

    private static String lineSeparator() {
        return getProperty("line.separator");
    }
}
