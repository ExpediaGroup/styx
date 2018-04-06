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

import static java.lang.String.format;

/**
 * Exception thrown when some required configuration property is missing.
 */
public final class MissingConfigurationException extends ConfigurationException {
    public static final String MISSING_CONFIGURATION_MESSAGE = "the configuration %s is not found";

    /**
     * Constructs an instance of this exception with a specified configuration key for the missing property.
     *
     * @param configurationKey configuration key
     */
    public MissingConfigurationException(String configurationKey) {
        super(format(MISSING_CONFIGURATION_MESSAGE, configurationKey));
    }
}
