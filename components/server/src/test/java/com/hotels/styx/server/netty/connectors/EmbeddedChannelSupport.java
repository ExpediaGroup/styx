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
package com.hotels.styx.server.netty.connectors;

import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.HttpHeader;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.util.CharsetUtil.UTF_8;

public final class EmbeddedChannelSupport {
    private EmbeddedChannelSupport() {
    }

    public static List<Object> outbound(EmbeddedChannel channel) {
        return toList(channel::readOutbound);
    }

    public static <T> List<T> toList(Supplier<? extends T> nullTerminatedSupplier) {
        return ImmutableList.copyOf(() -> new Iterator<T>() {
            boolean updated;
            T current;

            private void ensureUpdated() {
                if (!updated) {
                    updated = true;
                    current = nullTerminatedSupplier.get();
                }
            }

            @Override
            public boolean hasNext() {
                ensureUpdated();
                return current != null;
            }

            @Override
            public T next() {
                ensureUpdated();
                updated = false;
                return current;
            }
        });
    }

    public static HttpResponseOkMatcher httpResponseWithOkStatus() {
        return new HttpResponseOkMatcher();
    }

    public static HttpResponseOkMatcher httpResponseWithOkStatusAndHeaders(HttpHeader... headers) {
        return new HttpResponseOkMatcher(headers);
    }

    public static final class HttpResponseOkMatcher extends TypeSafeMatcher<Object> {
        private final List<HttpHeader> headers;

        private HttpResponseOkMatcher(HttpHeader... headers) {
            this.headers = ImmutableList.copyOf(headers);
        }

        @Override
        protected boolean matchesSafely(Object item) {
            if (!(item instanceof HttpResponse)) {
                return false;
            }

            HttpResponse response = (HttpResponse) item;

            if (!OK.equals(response.getStatus())) {
                return false;
            }

            for (HttpHeader header : headers) {
                if (!Objects.equals(response.headers().get(header.name()), header.value())) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText(HttpResponse.class.getName() + " with status = OK");
        }
    }

    public static HttpTextContentMatcher httpContent(String body) {
        return new HttpTextContentMatcher(body);
    }

    public static final class HttpTextContentMatcher extends TypeSafeMatcher<Object> {
        private final String content;

        private HttpTextContentMatcher(String content) {
            this.content = content;
        }

        @Override
        protected boolean matchesSafely(Object item) {
            return item instanceof HttpContent && ((HttpContent) item).content().toString(UTF_8).equals(content);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("HttpContent[");
            description.appendText(content);
            description.appendText("]");
        }
    }
}
