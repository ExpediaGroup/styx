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
package com.hotels.styx.common;

import java.util.AbstractMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * A class to make it more convenient to work with the entries in {@link java.util.Map}s in a streaming fashion,
 * by allowing the developer to deal with keys and values directly (as lambda parameters) without having to go
 * via {@link java.util.Map.Entry}.
 *
 * This class is not intended to replicate all the functionality of {@link java.util.stream.Stream}, just to
 * simplify common operations.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class MapStream<K, V> {
    private final Stream<Map.Entry<K, V>> stream;

    public MapStream(Stream<Map.Entry<K, V>> stream) {
        this.stream = requireNonNull(stream);
    }

    public MapStream(Map<K, V> map) {
        this(map.entrySet().stream());
    }

    public static <K, V> MapStream<K, V> stream(Map<K, V> map) {
        return new MapStream<>(map);
    }

    public MapStream<K, V> filter(BiPredicate<K, V> predicate) {
        return new MapStream<>(stream.filter(entry -> predicate.test(entry.getKey(), entry.getValue())));
    }

    public <R, S> MapStream<R, S> map(BiFunction<K, V, Map.Entry<R, S>> function) {
        return new MapStream<>(stream.map(entry -> function.apply(entry.getKey(), entry.getValue())));
    }

    public <R> MapStream<R, V> mapKey(BiFunction<K, V, R> function) {
        return map((key, value) -> new AbstractMap.SimpleEntry<>(
                function.apply(key, value),
                value));
    }

    public <R> MapStream<K, R> mapValue(BiFunction<K, V, R> function) {
        return map((key, value) -> new AbstractMap.SimpleEntry<>(
                key,
                function.apply(key, value)));
    }

    public <R> Stream<R> mapToObject(BiFunction<K, V, R> function) {
        return stream.map(entry -> function.apply(entry.getKey(), entry.getValue()));
    }

    public Map<K, V> toMap() {
        return stream.collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue
        ));
    }
}
