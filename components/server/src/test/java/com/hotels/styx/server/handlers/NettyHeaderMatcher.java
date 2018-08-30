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

import com.hotels.styx.api.HttpHeader;
import io.netty.handler.codec.http.HttpMessage;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.Map;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.hotels.styx.api.HttpHeader.header;
import static java.util.Objects.requireNonNull;

public final class NettyHeaderMatcher<T extends HttpMessage> extends TypeSafeMatcher<T> {
    private final HttpHeader expected;

    @Factory
    public static <T extends HttpMessage> Matcher<T> has(HttpHeader expected) {
        return new NettyHeaderMatcher<>(expected);
    }

    @Factory
    public static <T extends HttpMessage> Matcher<T> hasContentType(String contentType) {
        return new NettyHeaderMatcher<>(header(CONTENT_TYPE, contentType.toLowerCase()));
    }

    private NettyHeaderMatcher(HttpHeader expected) {
        this.expected = requireNonNull(expected);
    }

    @Override
    public boolean matchesSafely(T actual) {
        for (Map.Entry<String, String> header : actual.headers()) {
            if (matches(header)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(Map.Entry<String, String> header) {
        return header.getKey().equalsIgnoreCase(expected.name()) && header.getValue().equalsIgnoreCase(expected.value());
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("a ").appendText(HttpMessage.class.getSimpleName()).appendText(" with the header ");
        description.appendValue(expected);
    }
}
