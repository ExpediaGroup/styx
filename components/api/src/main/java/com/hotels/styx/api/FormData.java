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

import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.google.common.base.Throwables.propagate;
import static java.lang.String.format;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

/**
 * Provides a representation of Form Url Encoded parameters extracted from an {@link HttpRequest}.
 */
public final class FormData {

    private final Map<String, String> parameters;

    FormData(HttpPostRequestDecoder content) {
        requireNonNull(content);
        parameters = extractParameters(content);
        content.destroy();
    }

    private Map<String, String> extractParameters(HttpPostRequestDecoder content) {
        return content.getBodyHttpDatas()
                .stream()
                .filter(data -> data instanceof Attribute)
                .map(data -> (Attribute) data)
                .collect(toMap(Attribute::getName, (Function<Attribute, String>) (attribute) -> {
                    try {
                        return attribute.getValue();
                    } catch (IOException e) {
                        throw propagate(e);
                    }
                }));
    }

    /**
     * Returns the full map of available parameters.
     *
     * @return a map of available parameters.
     */
    public Map<String, String> parameters() {
        return unmodifiableMap(parameters);
    }

    /**
     * Helper method to access data of a single parameter.
     *
     * @param key
     * @return The parameter value wrapped in an {@link Optional} if the parameter exists, {@link Optional#empty} otherwise.
     */
    public Optional<String> postParam(String key) {
        return ofNullable(parameters.get(key));
    }

    @Override
    public String toString() {
        return parameters.entrySet()
                .stream()
                .map(entry -> format("%s=%s", entry.getKey(), entry.getValue()))
                .collect(joining("&"));
    }


}
