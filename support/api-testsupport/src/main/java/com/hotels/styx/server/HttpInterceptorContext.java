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
package com.hotels.styx.server;

import com.hotels.styx.api.HttpInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This is a duplicate of `com.hotels.styx.testapi.HttpInterceptorContext`
 * for the use of Styx core unit tests.
 * They cannot use the `testapi` implementation because the `testapi` itself
 * depends on the styx core modules, introducing a cyclic dependency.
 *
 */
public final class HttpInterceptorContext implements HttpInterceptor.Context {
    private final Map<String, Object> context = new ConcurrentHashMap<>();

    @Override
    public void add(String key, Object value) {
        context.put(key, value);
    }

    @Override
    public <T> T get(String key, Class<T> clazz) {
        return (T) context.get(key);
    }

    public static HttpInterceptorContext create() {
        return new HttpInterceptorContext();
    }
}
