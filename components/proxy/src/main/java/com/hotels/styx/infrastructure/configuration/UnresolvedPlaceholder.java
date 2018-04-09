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
package com.hotels.styx.infrastructure.configuration;

import java.util.Objects;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Information on an unresolved placeholder.
 */
public final class UnresolvedPlaceholder {
    private final String path;
    private final String value;
    private final String placeholder;

    public UnresolvedPlaceholder(String path, String value, String placeholder) {
        this.path = requireNonNull(path);
        this.value = requireNonNull(value);
        this.placeholder = requireNonNull(placeholder);
    }

    /**
     * The path/key at which the value containing the placeholder was found.
     *
     * @return path/key
     */
    public String path() {
        return path;
    }

    /**
     * The value that contained the placeholder.
     *
     * @return value
     */
    public String value() {
        return value;
    }

    /**
     * The placeholder itself.
     *
     * @return placeholder
     */
    public String placeholder() {
        return placeholder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UnresolvedPlaceholder that = (UnresolvedPlaceholder) o;
        return Objects.equals(path, that.path)
                && Objects.equals(value, that.value)
                && Objects.equals(placeholder, that.placeholder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, value, placeholder);
    }

    @Override
    public String toString() {
        return format("%s in %s=%s", placeholder, path, value);
    }
}
