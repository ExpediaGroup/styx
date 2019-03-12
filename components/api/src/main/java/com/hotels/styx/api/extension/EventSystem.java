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
 * TODO write description.
 */
public interface EventSystem {
    /**
     * TODO javadoc.
     *
     * @param key  todo
     * @param type todo
     * @param <T>  todo
     * @return todo
     */
    <T> Optional<T> get(String key, Class<T> type);

    /**
     * TODO javadoc.
     *
     * @param key   todo
     * @param value todo
     */
    void set(String key, Object value);

    /**
     * TODO javadoc.
     *
     * @param prefix todo
     * @return todo
     */
    default EventSystem scope(String prefix) {
        EventSystem self = this;

        return new EventSystem() {
            @Override
            public <T> Optional<T> get(String key, Class<T> type) {
                return self.get(resolve(key, prefix), type);
            }

            @Override
            public void set(String key, Object value) {
                self.set(resolve(key, prefix), value);
            }

            private String resolve(String key, String prefix) {
                return prefix + "." + key;
            }
        };
    }
}
