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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Collections.emptyIterator;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

/**
 * {@link Collection}s cannot contain <code>null</code> elements, and will throw a {@link NullPointerException} if one is encountered.
 *
 * {@link Set}s, in addition, preserve ordering of source sets.
 */
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

    public static <T> Set<T> copyToUnmodifiableSet(Iterator<? extends T> iterator) {
        return copyToUnmodifiableSet(toIterable(iterator));
    }

    public static <T> Set<T> copyToUnmodifiableSet(Iterable<? extends T> iterable) {
        return unmodifiableSet(stream(iterable).map(Objects::requireNonNull).collect(toCollection(() -> new LinkedHashSet<>())));
    }

    public static <T> Set<T> copyToUnmodifiableSet(Collection<? extends T> list) {
        return unmodifiableSet(list.stream().map(Objects::requireNonNull).collect(toCollection(() -> new LinkedHashSet<>())));
    }

    public static <T> Set<T> unmodifiableSetOf(T... elements) {
        return unmodifiableSet(Arrays.stream(elements).map(Objects::requireNonNull).collect(toCollection(() -> new LinkedHashSet<>())));
    }

    public static <T> Stream<T> stream(Iterator<? extends T> iterator) {
        return stream(toIterable(iterator));
    }

    public static <T> Stream<T> stream(Iterable<? extends T> iterable) {
        return StreamSupport.stream(((Iterable<T>) iterable).spliterator(), false);
    }

    public static String toString(Iterable<?> iterable) {
        return new StringBuilder("[")
                .append(StreamSupport.stream(iterable.spliterator(), false)
                        .map(Object::toString)
                        .collect(joining(", ")))
                .append("]")
                .toString();
    }

    public static int size(Iterable<?> iterable) {
        if (iterable instanceof Collection) {
            return ((Collection<?>) iterable).size();
        } else {
            return size(iterable.iterator());
        }
    }

    public static boolean contains(Iterable<?> iterable, Object element) {
        if (iterable instanceof Collection) {
            return ((Collection<?>) iterable).contains(element);
        } else {
            return contains(iterable.iterator(), element);
        }
    }

    public static <T> T getFirst(Iterable<? extends T> iterable, T fallback) {
        return getFirst(iterable.iterator(), fallback);
    }

    public static <T> Iterable<T> concat(Iterable<? extends T> a, Iterable<? extends T> b) {
        return new Iterable<T>() {

            @Override
            public Iterator<T> iterator() {
                return concat(a.iterator(), b.iterator());
            }
        };
    }

    public static <F, T> Iterable<T> transform(Iterable<F> iterable,
                                               Function<? super F, ? extends T> function) {
        return new Iterable<T>() {

            @Override
            public Iterator<T> iterator() {
                return new TransformedIterator<F, T>(iterable.iterator()) {

                    @Override
                    T transform(F from) {
                        return function.apply(from);
                    }
                };
            }
        };
    }

    public static int size(Iterator<?> iterator) {
        int c = 0;
        while (iterator.hasNext()) {
            c++;
            iterator.next();
        }
        return c;
    }

    public static boolean contains(Iterator<?> iterator, Object element) {
        while (iterator.hasNext()) {
            if (Objects.equals(iterator.next(), element)) {
                return true;
            }
        }
        return false;
    }

    public static <T> T getFirst(Iterator<? extends T> iterator, T fallback) {
        return iterator.hasNext() ? iterator.next() : fallback;
    }

    private static <T> Iterable<? extends T> toIterable(Iterator<? extends T> iterator) {
        return () -> (Iterator<T>) iterator;
    }



    private Collections() {
        // Private constructor
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

    /**
     * An iterator that transforms a backing iterator; for internal use. This avoids
     * the object overhead of constructing a {@link java.util.function.Function} for internal methods.
     *
     * @author Louis Wasserman
     */
    abstract static class TransformedIterator<F, T> implements Iterator<T> {
        final Iterator<? extends F> backingIterator;

        TransformedIterator(Iterator<? extends F> backingIterator) {
            this.backingIterator = requireNonNull(backingIterator);
        }

        abstract T transform(F from);

        @Override
        public final boolean hasNext() {
            return backingIterator.hasNext();
        }

        @Override
        public final T next() {
            return transform(backingIterator.next());
        }

        @Override
        public final void remove() {
            backingIterator.remove();
        }
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

    /**
     * Combines multiple iterators into a single iterator. The returned iterator
     * iterates across the elements of each iterator in {@code inputs}. The input
     * iterators are not polled until necessary.
     *
     * <p>The returned iterator supports {@code remove()} when the corresponding
     * input iterator supports it. The methods of the returned iterator may throw
     * {@code NullPointerException} if any of the input iterators is null.
     *
     * <p><b>Note:</b> the current implementation is not suitable for nested
     * concatenated iterators, i.e. the following should be avoided when in a loop:
     * {@code iterator = Iterators.concat(iterator, suffix);}, since iteration over the
     * resulting iterator has a cubic complexity to the depth of the nesting.
     */
    public static <T> Iterator<T> concat(Iterator<? extends T>... inputs) {
        requireNonNull(inputs);
        Iterator<? extends Iterator<? extends T>> inputIterator = unmodifiableListOf(inputs).iterator();

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
