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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Simple cache. Concurrent. No invalidation.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class SimpleCache<K, V> {
    private final Function<K, V> generator;
    private final Map<K, V> map;

    public SimpleCache(Function<K, V> generator) {
        this.generator = requireNonNull(generator);
        this.map = new ConcurrentHashMap<>();
    }

    public V get(K key) {
        return map.computeIfAbsent(key, generator);
    }

    @Override
    public String toString() {
        return map.toString();
    }
}
