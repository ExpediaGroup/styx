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
package com.hotels.styx.serviceproviders;

import com.fasterxml.jackson.databind.JsonNode;
import com.hotels.styx.api.Environment;
import com.hotels.styx.api.extension.service.spi.StyxService;
import com.hotels.styx.routing.RoutingObjectRecord;
import com.hotels.styx.routing.db.StyxObjectStore;

/**
 * A generic factory that can be implemented to create objects whose type is not known
 * until read from configuration.
 *
 */
public interface ServiceProviderFactory {
    /**
     * Create a service provider instance.
     *
     * @param environment          environment
     * @param serviceConfiguration Styx service configuration
     *
     * @return Styx service instance
     */
    StyxService create(Environment environment, JsonNode serviceConfiguration, StyxObjectStore<RoutingObjectRecord> routeDatabase);
}
