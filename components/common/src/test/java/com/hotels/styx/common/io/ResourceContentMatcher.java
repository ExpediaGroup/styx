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
package com.hotels.styx.common.io;

import com.google.common.io.CharStreams;
import com.hotels.styx.api.Resource;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Objects;

import static com.google.common.base.Throwables.propagate;
import static java.lang.System.lineSeparator;
import static java.util.Objects.requireNonNull;

public class ResourceContentMatcher extends TypeSafeMatcher<Resource> {
    private final String expected;

    private ResourceContentMatcher(String expected) {
        this.expected = requireNonNull(expected);
    }

    /**
     * Returns a Matcher that will compare the textual content of this {@link Resource} (using UTF-8 encoding)
     * to the provided String.  Line separator differences will be ignored as long the CRLF or LF sequences
     * are used for line breaks.
     *
     * @param expected text string to which this resource will be compared.
     * @return
     */
    public static ResourceContentMatcher contains(String expected) {
        return new ResourceContentMatcher(expected.replace("\r\n", "\n"));
    }

    @Override
    protected boolean matchesSafely(Resource item) {
        return Objects.equals(read(item), expected);
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(expected);
    }

    @Override
    protected void describeMismatchSafely(Resource item, Description mismatchDescription) {
        mismatchDescription.appendText(read(item));
    }

    private static String read(Resource item) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(item.inputStream()))) {
            return CharStreams.toString(reader).replace(lineSeparator(), "\n");
        } catch (IOException e) {
            throw propagate(e);
        }
    }

}
