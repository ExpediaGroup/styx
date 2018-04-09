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
 * This exception is thrown when there is a configuration problem.
 */
public class ConfigurationException extends RuntimeException {
    /**
     * Constructor.
     *
     * @param message message
     */
    public ConfigurationException(String message) {
        super(message);
    }

    /**
     * Constructor.
     *
     * @param cause cause
     */
    public ConfigurationException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructor.
     *
     * @param message message
     * @param cause   cause
     */
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
