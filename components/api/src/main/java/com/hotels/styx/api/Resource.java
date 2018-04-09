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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Interface for a resource descriptor that abstracts from the actual type of underlying resource,
 * such as a file or class path resource.
 */
public interface Resource {
    /**
     * Path associated with this resource. This could be relative to a file system location, or classpath depending on the
     * resource type
     *
     * @return path
     */
    String path();

    /**
     * URL associated with this resource. The form of this URL will depend on the resource type.
     *
     * @return URL
     */
    URL url();

    /**
     * Absolute path associated with this resource. This could be relative to the file system, or classpath depending on the
     * resource type
     *
     * @return
     */
    String absolutePath();

    /**
     * Creates an {@link InputStream} that supplies the content of the resource.
     *
     * @return an input stream
     * @throws IOException if the stream cannot be created, e.g. if the resource does not exist
     */
    InputStream inputStream() throws IOException;

    /**
     * Returns the classloader if this is a classpath resource.
     *
     * @return classloader
     * @throws UnsupportedOperationException if this is not a classpath resource
     */
    ClassLoader classLoader() throws UnsupportedOperationException;
}
