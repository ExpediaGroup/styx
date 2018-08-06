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

import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.Resource;
import com.hotels.styx.common.io.FileResource;
import com.hotels.styx.common.io.FileResourceIndex;

import java.nio.file.Path;
import java.util.Collection;

import static java.util.Collections.singleton;

/**
 * Provides resources representing JARs located at a given path.
 */
final class JarResources {
    private JarResources() {
    }

    /**
     * Provides resources representing JARs located at the given path.
     *
     * @param path a JAR or a directory containing JARs
     * @return JAR resources
     */
    static Collection<Resource> jars(Path path) {
        return isJarResource(path)
                ? singleton(singleResource(path))
                : multipleResources(path);
    }

    private static boolean isJarResource(Path path) {
        return path.toFile().getName().endsWith(".jar");
    }

    private static FileResource singleResource(Path path) {
        return new FileResource(path.toFile());
    }

    private static Collection<Resource> multipleResources(Path path) {
        FileResourceIndex resourceIndex = new FileResourceIndex();
        return ImmutableList.copyOf(resourceIndex.list(path.toString(), ".jar"));
    }
}
