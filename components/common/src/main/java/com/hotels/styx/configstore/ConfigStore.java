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
package com.hotels.styx.configstore;

import com.google.common.annotations.VisibleForTesting;
import rx.Observable;
import rx.Observer;
import rx.subjects.PublishSubject;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static rx.Observable.concat;
import static rx.schedulers.Schedulers.computation;

/**
 * Stores data about the current state of the system.
 * Added to allow Styx to operate in a more dynamic, adaptive way.
 */
public class ConfigStore {
    private static final String DELIMITER = ".";

    private final Observer<ConfigEntry<Object>> updates;
    private final ConcurrentMap<String, Object> values;
    private final Observable<ConfigEntry<Object>> propagation;

    public ConfigStore() {
        this.values = new ConcurrentHashMap<>();

        PublishSubject<ConfigEntry<Object>> updates = PublishSubject.create();
        PublishSubject<ConfigEntry<Object>> propagation = PublishSubject.create();

        updates.subscribe(update -> {
            this.values.put(update.key, update.value);
            propagation.onNext(update);
        });

        // Use more restricted interfaces for fields
        this.updates = updates;
        this.propagation = propagation;
    }

    /**
     * Get the current value of a config entry, if present.
     *
     * @param key  key
     * @param type type to cast to, if present
     * @param <T>  type
     * @return value if present, otherwise empty
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        return Optional.ofNullable(values.get(key)).map(type::cast);
    }

    /**
     * Sets the value of a config entry. This will also publish the new value to watchers.
     *
     * @param key   key
     * @param value new value
     */
    public void set(String key, Object value) {
        this.updates.onNext(new ConfigEntry<>(key, value));
    }

    /**
     * Watch for changes to an entry.
     *
     * @param key  key
     * @param type type to cast values to
     * @param <T>  type
     * @return observable supplying entry values when changes occur
     */
    public <T> Observable<T> watch(String key, Class<T> type) {
        Observable<T> subsequentStates = propagation
                .filter(update -> update.key().equals(key))
                .map(ConfigEntry::value)
                .cast(type)
                .observeOn(computation());

        return concat(currentValueIfPresent(key, type), subsequentStates);
    }

    /**
     * Watch for changes to all entries under a given root key.
     * For example, if the {@code rootKey} is "foo" then you would receive entries named "foo", "foo.bar", "foo.bar.baz", etc.
     *
     * @param rootKey root key
     * @param type    type to cast values to
     * @param <T>     type
     * @return observable supplying entries when changes occur
     */
    public <T> Observable<ConfigEntry<T>> watchAll(String rootKey, Class<T> type) {
        String prefix = rootKey + DELIMITER;

        return watch(key -> rootKey.equals(key) || key.startsWith(prefix), type);
    }

    /**
     * Get all entries under a given root key.
     * For example, if the {@code rootKey} is "foo" then you would receive entries named "foo", "foo.bar", "foo.bar.baz", etc.
     *
     * @param rootKey root key
     * @param type    type to cast values to
     * @param <T>     type
     * @return list of entries
     */
    public <T> List<ConfigEntry<T>> startingWith(String rootKey, Class<T> type) {
        String prefix = rootKey + DELIMITER;

        // Note: as the number of entries increases, it may be necessary to adopt a different data structure.
        return values.entrySet().stream()
                .filter(entry -> entry.getKey().equals(rootKey) || entry.getKey().startsWith(prefix))
                .map(entry -> new ConfigEntry<>(entry, type))
                .collect(toList());
    }

    private <T> Observable<ConfigEntry<T>> watch(Predicate<String> keyMatcher, Class<T> type) {
        Observable<ConfigEntry<T>> subsequentStates = propagation
                .filter(update -> keyMatcher.test(update.key()))
                .map(update -> update.cast(type))
                .observeOn(computation());

        return concat(currentValuesIfPresent(keyMatcher, type), subsequentStates);
    }

    private <T> Observable<T> currentValueIfPresent(String key, Class<T> type) {
        return Observable.from(
                singleOrEmpty(() -> values.get(key)))
                .cast(type);
    }

    private <T> Observable<ConfigEntry<T>> currentValuesIfPresent(Predicate<String> keyMatcher, Class<T> type) {
        Iterable<ConfigEntry<T>> generateOnCall = () -> values.entrySet().stream()
                .filter(entry -> keyMatcher.test(entry.getKey()))
                .map(entry -> new ConfigEntry<>(entry, type))
                .iterator();

        return Observable.from(generateOnCall);
    }

    /* Returns an iterable of one item if the supplier supplies a non-null value
       Returns an iterable of zero items if the supplier supplies a null value
    */
    private static <T> Iterable<T> singleOrEmpty(Supplier<T> supplier) {
        return () -> {
            T value = supplier.get();

            return value == null
                    ? (Iterator<T>) emptyList().iterator()
                    : singletonList(value).iterator();
        };
    }

    /**
     * A config entry with a key and value.
     *
     * @param <T> value type
     */
    public static final class ConfigEntry<T> {
        private final String key;
        private final T value;

        @VisibleForTesting
        ConfigEntry(String key, T value) {
            this.key = requireNonNull(key);
            this.value = requireNonNull(value);
        }

        private ConfigEntry(Map.Entry<String, ?> entry, Class<T> type) {
            this(entry.getKey(), type.cast(entry.getValue()));
        }

        public String key() {
            return key;
        }

        public T value() {
            return value;
        }

        private <R> ConfigEntry<R> map(Function<T, R> mapper) {
            return new ConfigEntry<R>(key, mapper.apply(value));
        }

        private <R> ConfigEntry<R> cast(Class<R> type) {
            return map(type::cast);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ConfigEntry<?> that = (ConfigEntry<?>) o;
            return Objects.equals(key, that.key)
                    && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, value);
        }

        @Override
        public String toString() {
            return key + "->" + value;
        }
    }
}
