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

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;

/**
 * A {@link com.hotels.styx.api.Resource} implementation for class path resources.
 * Uses either a given ClassLoader or a given Class for loading resources.
 * <p>Supports resolution as {@link java.io.File} if the class path
 * resource resides in the file system, but not for resources in a JAR.
 * Always supports resolution as URL.
 */
public class ClasspathResource implements Resource {
    public static final String CLASSPATH_SCHEME = "classpath:";

    private final String path;
    private final ClassLoader classLoader;

    /**
     * Construct using a path and the {@link ClassLoader} associated with a given class.
     *
     * @param path  path
     * @param clazz class to get {@link ClassLoader} from
     */
    public ClasspathResource(String path, Class<?> clazz) {
        this(path, clazz.getClassLoader());
    }

    /**
     * Construct using a path and class loader.
     *
     * @param path        path
     * @param classLoader class loader
     */
    public ClasspathResource(String path, ClassLoader classLoader) {
        this.path = stripInitialSlash(path.replace(CLASSPATH_SCHEME, ""));
        this.classLoader = classLoader;
    }

    private static String stripInitialSlash(String path) {
        if (path.startsWith("/")) {
            return path.substring(1);
        }

        return path;
    }

    @Override
    public String path() {
        return this.path;
    }

    @Override
    public URL url() {
        URL url = this.classLoader.getResource(this.path);

        if (url == null) {
            throw new RuntimeException(new FileNotFoundException(this.path));
        }

        return url;
    }

    @Override
    public String absolutePath() {
        return url().getFile();
    }

    @Override
    public InputStream inputStream() throws FileNotFoundException {
        InputStream stream = classLoader.getResourceAsStream(this.path);

        if (stream == null) {
            throw new FileNotFoundException(CLASSPATH_SCHEME + path);
        }

        return stream;
    }

    @Override
    public ClassLoader classLoader() {
        return classLoader;
    }

    @Override
    public String toString() {
        return CLASSPATH_SCHEME + path();
    }
}
