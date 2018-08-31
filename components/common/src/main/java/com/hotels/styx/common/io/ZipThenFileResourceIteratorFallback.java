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
 * Resource iterator factory implementation which acts as a fallback when no
 * other factories are found.
 */
public class ZipThenFileResourceIteratorFallback implements ResourceIteratorFactory {
    /**
     * The file resource iterator factory.
     */
    private final FileResourceIteratorFactory fileResourceIteratorFactory;

    /**
     * The ZIP resource iterator factory.
     */
    private final ZipResourceIteratorFactory zipResourceIteratorFactory;

    /**
     * Initializes a new instance of the ZipThenFileResourceIteratorFallback class.
     */
    public ZipThenFileResourceIteratorFallback() {
        fileResourceIteratorFactory = new FileResourceIteratorFactory();
        zipResourceIteratorFactory = new ZipResourceIteratorFactory();
    }

    @Override
    public boolean isFactoryFor(URL url) {
        return zipResourceIteratorFactory.isFactoryFor(url) || fileResourceIteratorFactory.isFactoryFor(url);
    }

    @Override
    public Iterator<Resource> createIterator(URL url, String path, String suffix) {
        if (zipResourceIteratorFactory.isFactoryFor(url)) {
            return zipResourceIteratorFactory.createIterator(url, path, suffix);
        }
        return fileResourceIteratorFactory.createIterator(url, path, suffix);
    }
}
