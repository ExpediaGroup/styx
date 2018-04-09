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
package com.hotels.styx;

import com.hotels.styx.api.HttpMessageBody;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.hotels.styx.support.api.BlockingObservables.getFirst;
import static com.hotels.styx.api.HttpMessageBody.NO_BODY;
import static java.nio.charset.Charset.defaultCharset;

public class HttpMessageBodyMatcher extends TypeSafeMatcher<HttpMessageBody> {

    public static Matcher<? super HttpMessageBody> empty() {
        return new HttpMessageBodyMatcher(NO_BODY);
    }

    private final HttpMessageBody HttpMessageBody;

    public HttpMessageBodyMatcher(HttpMessageBody HttpMessageBody) {
        this.HttpMessageBody = checkNotNull(HttpMessageBody);
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue(HttpMessageBody);
    }

    @Override
    protected boolean matchesSafely(HttpMessageBody HttpMessageBody) {
        return Objects.equals(contentAsString(HttpMessageBody), contentAsString(this.HttpMessageBody));
    }

    private String contentAsString(HttpMessageBody HttpMessageBody) {
        return getFirst(HttpMessageBody.content().reduce(new StringBuilder(), (sb, byteBuf) ->
                sb.append(byteBuf.toString(defaultCharset())))).toString();
    }
}
