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

import rx.Observable;
import rx.Observer;
import rx.subjects.PublishSubject;

import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static rx.Observable.concat;
import static rx.schedulers.Schedulers.computation;

/**
 * Stores data about the current state of the system.
 * Added to allow Styx to operate in a more dynamic, adaptive way.
 */
public class ConfigStore {
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

    private <T> Observable<T> currentValueIfPresent(String key) {
        return Observable.from(singleOrEmpty(() -> (T) values.get(key)));
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

    private static class Update {
        private final String key;
        private final Object value;

        Update(String key, Object value) {
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
