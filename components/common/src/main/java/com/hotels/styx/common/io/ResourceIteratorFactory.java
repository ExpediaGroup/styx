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

import java.net.URL;
import java.util.Iterator;

/**
 * A factory that creates resource iterators.
 */
public interface ResourceIteratorFactory {

    /**
     * Gets a value indicating whether the factory can create iterators for the
     * resource specified by the given URL.
     *
     * @param url The URL to check.
     * @return True if the factory can create an iterator for the given URL.
     */
    boolean isFactoryFor(URL url);

    /**
     * Creates an iterator for the given URL with the path and suffix.
     *
     * @param url    The URL.
     * @param path   The path.
     * @param suffix The suffix.
     * @return The iterator over the list designated by the URL, path, and
     *         suffix.
     */
    Iterator<Resource> createIterator(URL url, String path, String suffix);
}
