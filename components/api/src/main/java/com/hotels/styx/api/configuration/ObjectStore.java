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
package com.hotels.styx.api.configuration;

import java.util.Optional;

/**
 *
 * A repository for storing named objects.
 *
 * @param <T> Stored object type
 */
public interface ObjectStore<T> {

    /**
     * Gets an object with given key.
     *
     * @param key
     * @return
     */
    Optional<T> get(String key);

    /**
     * Inserts an object against key.
     *
     * If a key already exists, the object registered is replaced.
     *
     * @param key
     * @param payload
     */
    void insert(String key, T payload);

}
