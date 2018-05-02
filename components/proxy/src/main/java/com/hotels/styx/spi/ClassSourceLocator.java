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
package com.hotels.styx.spi;

import java.nio.file.Path;

import static com.hotels.styx.spi.JarResources.jars;

/**
 * Allows file-system to be mocked in the context of locating class sources (such as one or more JAR files).
 */
public interface ClassSourceLocator {
    ClassSourceLocator JARS = classPath -> new JarsClassSource(jars(classPath));

    /**
     * Provides a class source representing the specified class-path.
     *
     * @param classPath class path
     * @return class source
     */
    ClassSource classSource(Path classPath);
}
