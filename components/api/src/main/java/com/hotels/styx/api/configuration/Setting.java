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

import java.util.Optional;

/**
 * Represents a System environment variable necessary for bootstrapping the application.
 *
 * @param <T> type of the system environment value
 * @see SystemSettings.String
 * @see SystemSettings.Location
 */
public interface Setting<T> {

    /**
     * Return the key of the setting.
     *
     * @return the key of the setting
     */
    String name();

    /**
     * Return the value defined using the system variable.
     *
     * @return the value defined using the system variable
     */
    Optional<T> value();
}
