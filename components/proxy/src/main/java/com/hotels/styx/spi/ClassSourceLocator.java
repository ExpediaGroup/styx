/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx.spi;

import com.hotels.styx.common.SimpleCache;

import java.nio.file.Path;

import static com.hotels.styx.spi.JarResources.jars;

/**
 * Allows file-system to be mocked in the context of locating class sources (such as one or more JAR files).
 */
public interface ClassSourceLocator {
    ClassSourceLocator JARS = cached(classPath -> new JarsClassSource(jars(classPath)));

    /**
     * Provides a class source representing the specified class-path.
     *
     * @param classPath class path
     * @return class source
     */
    ClassSource classSource(Path classPath);

    /**
     * Wraps a class source locator, so that it will cache the result of using a particular path.
     * This can be useful for when multiple extensions are sourced from the same place, as it will
     * prevent loaded classes from being reloaded (and treated as new, different classes).
     *
     * @param locator locator
     * @return cached locator
     */
    static ClassSourceLocator cached(ClassSourceLocator locator) {
        SimpleCache<Path, ClassSource> cache = new SimpleCache<>(locator::classSource);

        return cache::get;
    }
}
