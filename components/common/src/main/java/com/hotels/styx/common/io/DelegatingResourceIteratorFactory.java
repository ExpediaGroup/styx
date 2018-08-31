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
import java.util.ServiceLoader;

import static java.util.Objects.requireNonNull;


/**
 * A {@link ResourceIteratorFactory} implementation which delegates to
 * factories found by the {@link ServiceLoader} class.
 */
public class DelegatingResourceIteratorFactory implements ResourceIteratorFactory {
    private final Iterable<ResourceIteratorFactory> delegates;
    private final ResourceIteratorFactory fallback;

    /**
     * Initializes a new instance of the DelegatingResourceIteratorFactory
     * class.
     */
    public DelegatingResourceIteratorFactory() {
        this(new ZipThenFileResourceIteratorFallback());
    }

    /**
     * Initializes a new instance of the DelegatingResourceIteratorFactory
     * class with a fallback factory.
     *
     * @param fallback The fallback resource iterator factory to use when an
     *                 appropriate one couldn't be found otherwise.
     */
    public DelegatingResourceIteratorFactory(ResourceIteratorFactory fallback) {
        this.delegates = ServiceLoader.load(ResourceIteratorFactory.class);
        this.fallback = requireNonNull(fallback);
    }

    @Override
    public boolean isFactoryFor(URL url) {
        for (ResourceIteratorFactory delegate : delegates) {
            if (delegate.isFactoryFor(url)) {
                return true;
            }
        }
        return fallback.isFactoryFor(url);
    }

    @Override
    public Iterator<Resource> createIterator(URL url, String path, String suffix) {
        for (ResourceIteratorFactory delegate : delegates) {
            if (delegate.isFactoryFor(url)) {
                return delegate.createIterator(url, path, suffix);
            }
        }
        if (fallback.isFactoryFor(url)) {
            return fallback.createIterator(url, path, suffix);
        }
        throw new RuntimeException("Fallback factory cannot handle URL: " + url);
    }
}
