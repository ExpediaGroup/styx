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
package com.hotels.styx.api.extension.service.spi;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Collections.emptyIterator;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

class Collections {

    static <T> List<T> listOf(Iterable<? extends T> iterable) {
        return unmodifiableList(stream(iterable).map(Objects::requireNonNull).collect(toList()));
    }

    @SafeVarargs
    static <T> List<T> listOf(T... elements) {
        if (elements.length == 0) {
            return emptyList();
        } else if (elements.length == 1) {
            return singletonList(elements[0]);
        }
        return unmodifiableList(Arrays.stream(elements).map(Objects::requireNonNull).collect(toList()));
    }

    private static <T> Stream<T> stream(Iterable<? extends T> iterable) {
        return iterable instanceof Collection
                ? ((Collection<T>) iterable).stream()
                : StreamSupport.stream(((Iterable<T>) iterable).spliterator(), false);
    }

    static int size(Iterable<?> iterable) {
        return iterable instanceof Collection
                ? ((Collection<?>) iterable).size()
                : size(iterable.iterator());
    }

    private static int size(Iterator<?> iterator) {
        int c = 0;
        while (iterator.hasNext()) {
            c++;
            iterator.next();
        }
        return c;
    }

    static boolean contains(Iterable<?> iterable, Object element) {
        return iterable instanceof Collection
                ? ((Collection<?>) iterable).contains(element)
                : contains(iterable.iterator(), element);
    }

    private static boolean contains(Iterator<?> iterator, Object element) {
        while (iterator.hasNext()) {
            if (Objects.equals(iterator.next(), element)) {
                return true;
            }
        }
        return false;
    }

    static <T> Iterable<T> concat(Iterable<? extends T> a, Iterable<? extends T> b) {
        return () -> concat(a.iterator(), b.iterator());
    }

    /*
     * Copyright (C) 2012 The Guava Authors
     *
     * Licensed under the Apache License, Version 2.0 (the "License");
     * you may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     *
     * http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     */
    @SafeVarargs
    private static <T> Iterator<T> concat(Iterator<? extends T>... inputs) {
        requireNonNull(inputs);
        Iterator<? extends Iterator<? extends T>> inputIterator = listOf(inputs).iterator();

        return new Iterator<T>() {
            Iterator<? extends T> current = emptyIterator();
            Iterator<? extends T> removeFrom;

            @Override
            public boolean hasNext() {
                // http://code.google.com/p/google-collections/issues/detail?id=151
                // current.hasNext() might be relatively expensive, worth minimizing.
                boolean currentHasNext;
                // checkNotNull eager for GWT
                // note: it must be here & not where 'current' is assigned,
                // because otherwise we'll have called inputs.next() before throwing
                // the first NPE, and the next time around we'll call inputs.next()
                // again, incorrectly moving beyond the error.
                // CHECKSTYLE:OFF
                while (!(currentHasNext = requireNonNull(current).hasNext())
                        && inputIterator.hasNext()) {
                    current = inputIterator.next();
                }
                // CHECKSTYLE:ON
                return currentHasNext;
            }
            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                removeFrom = current;
                return current.next();
            }
            @Override
            public void remove() {
                if (removeFrom == null) {
                    throw new IllegalStateException("no calls to next() since the last call to remove()");
                }
                removeFrom.remove();
                removeFrom = null;
            }
        };
    }

}
