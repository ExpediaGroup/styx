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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.util.function.Function;

/**
 * Provides convenience method(s) for using Guava's loading cache.
 */
public final class Caches {
    private Caches() {
    }

    /**
     * Creates a LoadingCache with default settings.
     *
     * @param loadingFunction loading function
     * @param <K> key type
     * @param <V> value type
     * @return loading cache
     */
    public static <K, V> LoadingCache<K, V> cache(Function<K, V> loadingFunction) {
        return CacheBuilder.newBuilder()
                .build(new CacheLoader<K, V>() {
                    @Override
                    public V load(K key) throws Exception {
                        return loadingFunction.apply(key);
                    }
                });
    }
}
