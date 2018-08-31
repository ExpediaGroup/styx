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
package com.hotels.styx.spi.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import static com.google.common.base.Objects.toStringHelper;
import static java.util.Objects.requireNonNull;

/**
 * Factory for objects of a given class.
 */
public class SpiExtensionFactory {

    private final String factoryClass;
    private final String classPath;

    public SpiExtensionFactory(@JsonProperty("class") String factoryClass,
                               @JsonProperty("classPath") String classPath) {
        this.factoryClass = requireNonNull(factoryClass);
        this.classPath = requireNonNull(classPath);
    }

    public String factoryClass() {
        return factoryClass;
    }

    public String classPath() {
        return classPath;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(factoryClass, classPath);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        SpiExtensionFactory other = (SpiExtensionFactory) obj;
        return Objects.equal(this.factoryClass, other.factoryClass)
                && Objects.equal(this.classPath, other.classPath);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("class", factoryClass)
                .add("classPath", classPath)
                .toString();
    }
}
