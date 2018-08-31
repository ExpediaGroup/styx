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

import com.google.common.collect.Iterators;
import com.hotels.styx.api.Resource;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.stream.Stream;

import static java.util.Spliterator.ORDERED;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * A resource index that scans the class path for the resources.
 */
public class ClasspathResourceIndex implements ResourceIndex {
    private final ClassLoader classLoader;

    public ClasspathResourceIndex(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public Iterable<Resource> list(String path, String suffix) {
        String classpath = path.replace("classpath:", "");
        ResourceIteratorFactory resourceIteratorFactory = new DelegatingResourceIteratorFactory();

        try {
            Enumeration<URL> resources = classLoader.getResources(classpath);

            return enumerationStream(resources)
                    .map(url -> resourceIteratorFactory.createIterator(url, classpath, suffix))
                    .flatMap(ClasspathResourceIndex::iteratorStream)
                    .collect(toList());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> Stream<T> enumerationStream(Enumeration<T> enumeration) {
        return iteratorStream(Iterators.forEnumeration(enumeration));
    }

    private static <T> Stream<T> iteratorStream(Iterator<T> iterator) {
        return stream(spliteratorUnknownSize(iterator, ORDERED), false);
    }
}
