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

import com.hotels.styx.api.HttpHandler;

import java.util.Optional;
import java.util.Set;

/**
 * Styx Route Database query interface.
 */
public interface RouteDatabase {

    Set<Record> getAll(String... tags);

    Optional<Record> get(String name);

    void insert(String routingObjectDefAsJson);

    void remove(String key);

    void retag(String key, String oldTag, String newTag);

    /*

    Optional<HttpHandler> handler(String key);

    Set<HttpHandler> handlers(String... tags);

    Set<Record> tagLookup(String... tags);

    Optional<Record> lookup(String key);

    Set<Record> lookupAll();

    */

    /**
     * Route database record.
     */
    interface Record {
        String name();

        String type();

        Set<String> tags();

        HttpHandler handler();

        String configuration();
    }
}
