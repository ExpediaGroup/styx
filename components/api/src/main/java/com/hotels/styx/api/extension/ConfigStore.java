/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx.api.extension;

import java.util.Optional;

/**
 * Stores data about the current state of the system.
 */
public interface ConfigStore {
    /**
     * Get the current value of a config entry, if present.
     *
     * @param key  key
     * @param type type to cast to, if present
     * @param <T>  type
     * @return value if present, otherwise empty
     */
    <T> Optional<T> get(String key, Class<T> type);

    /**
     * Sets the value of a config entry. This will also publish the new value to watchers.
     *
     * @param key   key
     * @param value new value
     */
    void set(String key, Object value);
}
