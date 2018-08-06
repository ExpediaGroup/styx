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
package com.hotels.styx.common.io;

import com.hotels.styx.api.Resource;

import static com.hotels.styx.common.io.ClasspathResource.CLASSPATH_SCHEME;

/**
 * Factory for creating resources (file, classpath) based on prefix.
 */
public class ResourceFactory {

    /**
     * Create a new resource from {@code path} using the {@code classLoader}.
     *
     * @param path a resource path
     * @param classLoader the classloader to load it with (if it is a classpath resource)
     * @return a resource
     */
    public static Resource newResource(String path, ClassLoader classLoader) {
        if (path.startsWith(CLASSPATH_SCHEME)) {
            return new ClasspathResource(path, classLoader);
        }

        return new FileResource(path);
    }

    /**
     * Create a new resource from {@code path} using the current thread classloader.
     *
     * @param path a resource path
     * @return a resource
     */
    public static Resource newResource(String path) {
        return newResource(path, Thread.currentThread().getContextClassLoader());
    }
}
