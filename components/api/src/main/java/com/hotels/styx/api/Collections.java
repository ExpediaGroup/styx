/*
  Copyright (C) 2013-2020 Expedia Inc.

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

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;

public final class Collections {

    public static <T> List<T> copyToUnmodifiableList(Iterator<? extends T> iterator) {
        return copyToUnmodifiableList(toIterable(iterator));
    }

    public static <T> List<T> copyToUnmodifiableList(Iterable<? extends T> iterable) {
        return unmodifiableList(stream(iterable).map(Objects::requireNonNull).collect(toList()));
    }

    public static <T> List<T> copyToUnmodifiableList(Collection<? extends T> list) {
        return unmodifiableList(list.stream().map(Objects::requireNonNull).collect(toList()));
    }

    public static <T> List<T> unmodifiableListOf(T... elements) {
        return unmodifiableList(Arrays.stream(elements).map(Objects::requireNonNull).collect(toList()));
    }

    public static <T> Stream<T> stream(Iterator<? extends T> iterator) {
        return stream(toIterable(iterator));
    }

    public static <T> Stream<T> stream(Iterable<? extends T> iterable) {
        return StreamSupport.stream(((Iterable<T>) iterable).spliterator(), false);
    }

    private static <T> Iterable<? extends T> toIterable(Iterator<? extends T> iterator) {
        return () -> (Iterator<T>) iterator;
    }

    private Collections() {
        // Private constructor
    }
}
