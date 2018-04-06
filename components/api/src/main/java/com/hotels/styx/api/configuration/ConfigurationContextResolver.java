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

import com.hotels.styx.api.HttpRequest;

import static com.hotels.styx.api.configuration.Configuration.Context.EMPTY_CONFIGURATION_CONTEXT;

/**
 * Creates a new configuration context relative to the request.
 */
public interface ConfigurationContextResolver {
    ConfigurationContextResolver EMPTY_CONFIGURATION_CONTEXT_RESOLVER = request -> EMPTY_CONFIGURATION_CONTEXT;

    /**
     * Creates a request bound context from the request.
     *
     * @param request the current request
     * @return resolved context
     */
    Configuration.Context resolve(HttpRequest request);
}
