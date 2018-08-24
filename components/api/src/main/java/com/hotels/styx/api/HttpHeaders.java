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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import io.netty.handler.codec.http.DefaultHttpHeaders;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import static com.hotels.styx.api.HttpHeader.header;
import static java.time.ZoneOffset.UTC;
import static java.util.Locale.US;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * Represent a collection of {@link HttpHeader}s from a single HTTP message.
 */
public final class HttpHeaders implements Iterable<HttpHeader> {
    private static final DateTimeFormatter RFC1123_DATE_FORMAT = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'")
            .withLocale(US)
            .withZone(UTC);

    private final DefaultHttpHeaders nettyHeaders;

    private HttpHeaders(Builder builder) {
        this.nettyHeaders = builder.nettyHeaders;
    }

    /**
     * Returns an {@link ImmutableSet} that contains the names of all headers in this object.
     *
     * @return header names
     */
    public ImmutableSet<String> names() {
        return ImmutableSet.copyOf(nettyHeaders.names());
    }

    /**
     * Returns an {@link Optional} containing the value of the header with the specified {@code name},
     * if such an element exists.
     * If there are more than one values for the specified name, the first value is returned.
     *
     * @param name header name
     * @return header value if header exists
     */
    public Optional<String> get(CharSequence name) {
        return Optional.ofNullable(nettyHeaders.get(name));
    }

    /**
     * Returns an {@link ImmutableList} of header values with the specified {@code name}.
     *
     * @param name The name of the headers
     * @return a {@link ImmutableList} of header values which will be empty if no values
     * are found
     */
    public ImmutableList<String> getAll(CharSequence name) {
        return ImmutableList.copyOf(nettyHeaders.getAll(name));
    }

    /**
     * Returns {@code true} if this header contains a header with the specified {@code name}.
     *
     * @param name header name
     * @return {@code true} if this map contains a header with the specified {@code name}
     */
    public boolean contains(CharSequence name) {
        return nettyHeaders.contains(name);
    }

    @Override
    public Iterator<HttpHeader> iterator() {
        return stream(nettyHeaders.spliterator(), false)
                .map(header -> header(header.getKey(), header.getValue()))
                .iterator();
    }

    public void forEach(BiConsumer<String, String> consumer) {
        nettyHeaders.forEach(entry -> consumer.accept(entry.getKey(), entry.getValue()));
    }

    /**
     * Creates a new builder.
     *
     * @return a new builder
     */
    public Builder newBuilder() {
        return new Builder(this);
    }

    @Override
    public String toString() {
        return Iterables.toString(nettyHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toString());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        HttpHeaders other = (HttpHeaders) obj;
        return Objects.equals(toString(), other.toString());
    }

    /**
     * Builds headers.
     */
    public static class Builder {
        private final DefaultHttpHeaders nettyHeaders;

        public Builder() {
            this.nettyHeaders = new DefaultHttpHeaders(false);
        }

        public Builder(HttpHeaders headers) {
            this.nettyHeaders = new DefaultHttpHeaders(false);
            this.nettyHeaders.set(headers.nettyHeaders);
        }


        public List<String> getAll(CharSequence name) {
            return this.nettyHeaders.getAll(name);
        }

        public String get(CharSequence name) {
            return this.nettyHeaders.get(name);
        }

        /**
         * Adds a new header with the specified {@code name} and {@code value}.
         * <p/>
         * Will not replace any existing values for the header.
         *
         * @param name  The name of the header
         * @param value The value of the header
         * @return this builder
         */
        public Builder add(CharSequence name, String value) {
            this.nettyHeaders.add(name, requireNonNull(value));
            return this;
        }

        /**
         * Adds a new header with the specified {@code name} and {@code value}.
         * <p/>
         * Will not replace any existing values for the header.
         *
         * @param name  The name of the header
         * @param value The value of the header
         * @return this builder
         */
        public Builder add(CharSequence name, Object value) {
            this.nettyHeaders.add(name, requireNonNull(value));
            return this;
        }

        /**
         * Adds a new header with the specified {@code name} and {@code values}.
         * <p/>
         * Will not replace any existing values for the header.
         *
         * @param name   The name of the header
         * @param values The value of the header
         * @return this builder
         */
        public Builder add(CharSequence name, Iterable values) {
            nonNullValues(values)
                    .ifPresent(nonNullValues -> this.nettyHeaders.add(name, nonNullValues));

            return this;
        }

        private Optional<List<?>> nonNullValues(Iterable<?> values) {
            List<?> list = stream(values.spliterator(), false)
                    .filter(value -> value != null)
                    .collect(toList());

            return list.isEmpty() ? Optional.empty() : Optional.of(list);
        }

        /**
         * Removes the header with the specified {@code name}.
         *
         * @param name the name of the header to remove
         * @return this builder
         */
        public Builder remove(CharSequence name) {
            this.nettyHeaders.remove(name);
            return this;
        }

        /**
         * Sets the (only) value for the header with the specified name.
         * <p/>
         * All existing values for the same header will be removed.
         *
         * @param name  The name of the header
         * @param value The value of the header
         * @return this builder
         */
        public Builder set(CharSequence name, String value) {
            nettyHeaders.set(name, value);
            return this;
        }

        /**
         * Sets the (only) value for the header with the specified name.
         * <p/>
         * All existing values for the same header will be removed.
         *
         * @param name  The name of the header
         * @param value The value of the header
         * @return this builder
         */
        public Builder set(CharSequence name, Instant value) {
            nettyHeaders.set(name, RFC1123_DATE_FORMAT.format(value));
            return this;
        }

        /**
         * Sets the (only) value for the header with the specified name.
         * <p/>
         * All existing values for the same header will be removed.
         *
         * @param name  The name of the header
         * @param value The value of the header
         * @return this builder
         */
        public Builder set(CharSequence name, Object value) {
            nettyHeaders.set(name, value);
            return this;
        }

        /**
         * Sets the (only) value for the header with the specified name.
         * <p/>
         * All existing values for the same header will be removed.
         *
         * @param name   The name of the header
         * @param values The value of the header
         * @return this builder
         */
        public Builder set(CharSequence name, Iterable values) {
            nonNullValues(values)
                    .ifPresent(nonNullValues -> this.nettyHeaders.set(name, nonNullValues));

            return this;
        }

        /**
         * Sets the (only) value for the header with the specified name.
         * <p/>
         * All existing values for the same header will be removed.
         *
         * @param name  The name of the header
         * @param value The value of the header
         * @return this builder
         */
        public Builder set(CharSequence name, int value) {
            nettyHeaders.set(name, value);
            return this;
        }

        public HttpHeaders build() {
            return new HttpHeaders(this);
        }
    }
}
