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

import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.QueryStringEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.Objects.toStringHelper;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * Query part of a URL.
 */
final class UrlQuery {
    private final List<Parameter> parameters;
    private final String encodedQuery;

    private UrlQuery(List<Parameter> parameters) {
        this.parameters = unmodifiableList(new ArrayList<>(parameters));

        QueryStringEncoder encoder = new QueryStringEncoder("", UTF_8);

        parameters.forEach(parameter -> encoder.addParam(parameter.key, parameter.value));

        this.encodedQuery = removeInitialCharacter(encoder.toString()); // remove initial '?' character
    }

    private String removeInitialCharacter(String encodedQuery) {
        return encodedQuery.isEmpty() ? "" : encodedQuery.substring(1);
    }

    Optional<String> parameterValue(String name) {
        return stream(parameterValues(name).spliterator(), false).findFirst();
    }

    Iterable<String> parameterValues(String name) {
        return parameters().stream()
                .filter(parameter -> parameter.key.equals(name))
                .map(Parameter::value)
                .collect(toList());
    }

    List<Parameter> parameters() {
        return parameters;
    }

    /**
     * Get the names of all query parameters.
     *
     * @return the names of all query parameters.
     */
    Iterable<String> parameterNames() {
        return parameters().stream().map(Parameter::key).distinct().collect(toList());
    }

    String encodedQuery() {
        return encodedQuery;
    }

    Builder newBuilder() {
        return new Builder(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UrlQuery query = (UrlQuery) o;
        return Objects.equals(encodedQuery, query.encodedQuery);
    }

    @Override
    public int hashCode() {
        return Objects.hash(encodedQuery);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("parameters", parameters)
                .add("encodedQuery", encodedQuery)
                .toString();
    }

    static class Parameter {
        private final String key;
        private final String value;

        Parameter(String key, String value) {
            this.key = key;
            this.value = value;
        }

        String key() {
            return key;
        }

        String value() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Parameter parameter = (Parameter) o;
            return Objects.equals(key, parameter.key)
                    && Objects.equals(value, parameter.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, value);
        }

        @Override
        public String toString() {
            return key + "=" + value;
        }
    }

    static class Builder {
        private List<Parameter> parameters;

        Builder() {
            this((String) null);
        }

        Builder(String rawQuery) {
            if (rawQuery != null) {
                populateParametersFrom(rawQuery);
            }
        }

        Builder(UrlQuery query) {
            this.parameters = new ArrayList<>(query.parameters());
        }

        private void populateParametersFrom(String rawQuery) {
            QueryStringDecoder decoder = new QueryStringDecoder(rawQuery, UTF_8, false);

            parameters = decoder.parameters().entrySet().stream()
                    .flatMap(entry -> {
                        List<String> values = entry.getValue();

                        return values.stream()
                                .map(value -> new Parameter(entry.getKey(), value));
                    })
                    .collect(toList());
        }

        Builder addParam(String name, String value) {
            if (parameters == null) {
                parameters = new ArrayList<>();
            }

            parameters.add(new Parameter(name, value));
            return this;
        }

        UrlQuery build() {
            return new UrlQuery(parameters == null ? emptyList() : parameters);
        }
    }
}
