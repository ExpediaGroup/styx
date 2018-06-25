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

    private final Observer<Update> updates;
    private final ConcurrentMap<String, Object> values;
    private final Observable<Update> propagation;

    public ConfigStore() {
        this.values = new ConcurrentHashMap<>();

        PublishSubject<Update> updates = PublishSubject.create();
        PublishSubject<Update> propagation = PublishSubject.create();

        updates.subscribe(update -> {
            this.values.put(update.key, update.value);
            propagation.onNext(update);
        });

        // Use more restricted interfaces for fields
        this.updates = updates;
        this.propagation = propagation;
    }

    public <T> Optional<T> get(String key) {
        return Optional.ofNullable((T) values.get(key));
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        return Optional.ofNullable(values.get(key)).map(type::cast);
    }

    public void set(String key, Object value) {
        this.updates.onNext(new Update(key, value));
    }

    public <T> Observable<T> watch(String key, Class<T> type) {
        return watch(key).cast(type);
    }

    public <T> Observable<T> watch(String key) {
        Observable<T> subsequentStates = propagation
                .filter(update -> update.key().equals(key))
                .map(Update::value)
                .map(value -> (T) value)
                .observeOn(computation());

        return concat(currentValueIfPresent(key), subsequentStates);
    }

    public <T> Observable<KeyValuePair<T>> watchAll(String rootKey) {
        return watchAll(rootKey, Object.class)
                .map(value -> (KeyValuePair<T>) value);
    }

    public <T> Observable<KeyValuePair<T>> watchAll(String rootKey, Class<T> type) {
        String prefix = rootKey + DELIMITER;

        return watch(key -> rootKey.equals(key) || key.startsWith(prefix), type);
    }

    public <T> List<KeyValuePair<T>> startingWith(String rootKey) {
        return (List) startingWith(rootKey, Object.class);
    }

    // Note: as the number of entries increases, it may be necessary to adopt a different data structure.
    public <T> List<KeyValuePair<T>> startingWith(String rootKey, Class<T> type) {
        String prefix = rootKey + DELIMITER;

        return values.entrySet().stream()
                .filter(entry -> entry.getKey().equals(rootKey) || entry.getKey().startsWith(prefix))
                .map(entry -> new KeyValuePair<>(entry, type))
                .collect(toList());
    }

    private <T> Observable<KeyValuePair<T>> watch(Predicate<String> keyMatcher, Class<T> type) {
        Observable<KeyValuePair<T>> subsequentStates = propagation
                .filter(update -> keyMatcher.test(update.key()))
                .map(update -> new KeyValuePair<>(update, type))
                .observeOn(computation());

        return concat(currentValuesIfPresent(keyMatcher, type), subsequentStates);
    }

    private <T> Observable<T> currentValueIfPresent(String key) {
        return Observable.from(singleOrEmpty(() -> (T) values.get(key)));
    }

    private <T> Observable<KeyValuePair<T>> currentValuesIfPresent(Predicate<String> keyMatcher, Class<T> type) {
        Iterable<KeyValuePair<T>> generateOnCall = () -> values.entrySet().stream()
                .filter(entry -> keyMatcher.test(entry.getKey()))
                .map(entry -> new KeyValuePair<>(entry, type))
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

    public static final class KeyValuePair<T> {
        private final String key;
        private final T value;

        @VisibleForTesting
        KeyValuePair(String key, T value) {
            this.key = requireNonNull(key);
            this.value = requireNonNull(value);
        }

        private KeyValuePair(Map.Entry<String, ?> entry, Class<T> type) {
            this(entry.getKey(), type.cast(entry.getValue()));
        }

        private KeyValuePair(Update update, Class<T> type) {
            this(update.key(), type.cast(update.value()));
        }

        public String key() {
            return key;
        }

        public T value() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            KeyValuePair<?> that = (KeyValuePair<?>) o;
            return Objects.equals(key, that.key) &&
                    Objects.equals(value, that.value);
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

    private static class Update {
        private final String key;
        private final Object value;

        private Update(String key, Object value) {
            this.key = requireNonNull(key);
            this.value = requireNonNull(value);
        }

        String key() {
            return key;
        }

        Object value() {
            return value;
        }
    }
}
