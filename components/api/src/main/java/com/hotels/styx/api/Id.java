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

import com.google.common.base.Objects;

import static java.util.Objects.requireNonNull;


/**
 * An identifier.
 */
public final class Id {
    public static final Id GENERIC_APP = id("generic-app");

    private final String value;

    private Id(String value) {
        this.value = requireNonNull(value);
    }

    /**
     * Create a new Id.
     *
     * @param value string value of Id
     * @return a new Id
     */
    public static Id id(String value) {
        return new Id(value);
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Id other = (Id) obj;
        return Objects.equal(this.value, other.value);
    }
}
