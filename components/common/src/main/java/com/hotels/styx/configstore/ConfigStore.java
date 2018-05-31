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

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.Objects.requireNonNull;

/**
 * Stores data about the current state of the system.
 * Added to allow Styx to operate in a more dynamic, adaptive way.
 */
public class ConfigStore {
    private final ConcurrentMap<String, Object> config;

    public ConfigStore() {
        this.config = new ConcurrentHashMap<>();
    }

    public <T> Optional<T> get(String key) {
        return Optional.ofNullable((T) config.get(key));
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        return Optional.ofNullable(config.get(key)).map(type::cast);
    }

    public void set(String key, Object value) {
        this.config.put(key, requireNonNull(value));
    }
}
