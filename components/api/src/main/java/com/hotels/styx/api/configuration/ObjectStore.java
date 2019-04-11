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

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 *
 * A glorified map with extra features, like tagging objects
 * and watching for changes.
 *
 * @param <T> Stored object type
 */
public interface ObjectStore<T> {

    /**
     * Get all objects tagged with {@code tags}.
     *
     * @param tags
     * @return a set of matching objects
     */
    Collection<T> getAll(Set<String> tags);

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

    /**
     * Inserts an object against key, with tags.
     *
     * If a key already exists, the object registered is replaced.
     *
     * @param key
     * @param tags
     * @param payload
     */
    void insert(String key, Set<String> tags, T payload);

    /**
     * Removes an object.
     *
     * @param key
     */
    void remove(String key);

    /**
     * Changes tags associated with an object.
     *
     * @param key
     * @param oldTag
     * @param newTag
     */
    void retag(String key, String oldTag, String newTag);
}
