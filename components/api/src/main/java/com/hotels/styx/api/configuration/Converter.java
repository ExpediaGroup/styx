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
package com.hotels.styx.api.configuration;

/**
 * A function that converts a source to targetType.
 */
public interface Converter {

    /**
     * Convert the source to targetType.
     *
     * @param source     the source object to convert (may be null)
     * @param targetType the target type to convert to (required)
     * @return the converted object, an instance of targetType
     */
    <T> T convert(Object source, Class<T> targetType);
}
