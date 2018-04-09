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
 * Extension of {@link ConfigurationException} thrown when a system variable is undefined.
 */
public class NoSystemPropertyDefined extends ConfigurationException {

    private static final String ERROR_MESSAGE_FORMAT = "No system property %s has been defined.";

    /**
     * Creates an instance of this exception with the undefined system variable name.
     *
     * @param variableName the undefined system variable name
     */
    public NoSystemPropertyDefined(String variableName) {
        super(format(ERROR_MESSAGE_FORMAT, variableName));
    }
}
