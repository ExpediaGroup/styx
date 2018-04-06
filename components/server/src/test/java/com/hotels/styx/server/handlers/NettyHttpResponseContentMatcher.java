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
package com.hotels.styx.server.handlers;

import io.netty.handler.codec.http.FullHttpResponse;

import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import static com.google.common.base.Charsets.UTF_8;
import static org.hamcrest.CoreMatchers.equalTo;

public class NettyHttpResponseContentMatcher<T extends FullHttpResponse> extends TypeSafeMatcher<T> {

    private final Matcher<String> matcher;

    @Factory
    public static <T extends FullHttpResponse> Matcher<T> content(Matcher<String> matcher) {
        return new NettyHttpResponseContentMatcher<>(matcher);
    }

    @Factory
    public static <T extends FullHttpResponse> Matcher<T> content(String content) {
        return new NettyHttpResponseContentMatcher<>(equalTo(content));
    }

    @Factory
    public static <T extends FullHttpResponse> Matcher<T> hasBody(String content) {
        return new NettyHttpResponseContentMatcher<>(equalTo(content));
    }

    public NettyHttpResponseContentMatcher(Matcher<String> matcher) {
        this.matcher = matcher;
    }

    @Override
    public boolean matchesSafely(T actual) {
        return matcher.matches(actual.content().toString(UTF_8));
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("content with ");
        matcher.describeTo(description);
    }
}