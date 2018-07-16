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
package com.hotels.styx.api.cookies;

import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

/**
 * A set that provides methods for accessing elements like a map. Note that as it is not really a map there is no guarantee
 * that a "key" will only match one element.
 *
 * @param <K> "key" type
 * @param <V> element type
 */
public class PseudoMap<K, V> implements Set<V> {
    private final Set<V> set;
    private final BiPredicate<K, V> searcher;

    public PseudoMap(Set<V> set, BiPredicate<K, V> searcher) {
        this.set = ImmutableSet.copyOf(set);
        this.searcher = requireNonNull(searcher);
    }

    public Set<V> matching(K key) {
        return set.stream()
                .filter(element -> searcher.test(key, element))
                .collect(toSet());
    }

    public Optional<V> firstMatch(K key) {
        return set.stream()
                .filter(element -> searcher.test(key, element))
                .findFirst();
    }

    @Override
    public int size() {
        return set.size();
    }

    @Override
    public boolean isEmpty() {
        return set.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return set.contains(o);
    }

    @Override
    public Iterator<V> iterator() {
        return set.iterator();
    }

    @Override
    public Object[] toArray() {
        return set.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return set.toArray(a);
    }

    @Override
    public boolean add(V e) {
        return set.add(e);
    }

    @Override
    public boolean remove(Object o) {
        return set.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return set.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends V> c) {
        return set.addAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return set.retainAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return set.removeAll(c);
    }

    @Override
    public void clear() {
        set.clear();
    }

    @Override
    public boolean equals(Object o) {
        return set.equals(o);
    }

    @Override
    public int hashCode() {
        return set.hashCode();
    }

    @Override
    public Spliterator<V> spliterator() {
        return set.spliterator();
    }

    @Override
    public boolean removeIf(Predicate<? super V> filter) {
        return set.removeIf(filter);
    }

    @Override
    public Stream<V> stream() {
        return set.stream();
    }

    @Override
    public Stream<V> parallelStream() {
        return set.parallelStream();
    }

    @Override
    public void forEach(Consumer<? super V> action) {
        set.forEach(action);
    }

    @Override
    public String toString() {
        return set.toString();
    }
}
