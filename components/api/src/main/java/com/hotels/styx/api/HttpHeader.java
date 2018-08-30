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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

/**
 * Represents the key to value relationship in an HTTP header.
 * It is possible for a header to have multiple values.
 */
public final class HttpHeader {
    private static final Joiner HEADER_VALUES_JOINER = Joiner.on(", ").skipNulls();

    private final String name;
    private final List<String> values;

    /**
     * Creates a header with a name and one or more values.
     *
     * @param name header name
     * @param values header values
     * @return created header
     */
    public static HttpHeader header(String name, String... values) {
        checkArgument(values.length > 0, "must give at least one value");

        return new HttpHeader(requireNonNull(name), ImmutableList.copyOf(values));
    }

    private HttpHeader(String name, List<String> values) {
        this.name = name;
        this.values = values;
    }

    /**
     * Return header name.
     *
     * @return name
     */
    public String name() {
        return name;
    }

    /**
     * Return the first header value.
     *
     * @return value
     */
    public String value() {
        return values.get(0);
    }

    /**
     * Return all header values.
     *
     * @return values
     */
    public Iterable<String> values() {
        return values;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, values);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HttpHeader that = (HttpHeader) o;
        return Objects.equals(name, that.name) && Objects.equals(values, that.values);
    }

    @Override
    public String toString() {
        return name + "=" + HEADER_VALUES_JOINER.join(values);
    }
}
