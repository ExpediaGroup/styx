/*
  Copyright (C) 2013-2020 Expedia Inc.

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
import com.hotels.styx.InetServer;
import com.hotels.styx.routing.config.RoutingObjectFactory;
import com.hotels.styx.routing.db.StyxObjectStore;
import com.hotels.styx.StyxObjectRecord;

/**
 * A generic factory that can be implemented to create objects whose type is not known
 * until read from configuration.
 *
 */
public interface StyxServerFactory {
    /**
     * Create a service provider instance.
     *
     * @param name                 Service provider name
     * @param context              Routing object factory context
     * @param serviceConfiguration Styx service configuration
     * @param serviceDb            Styx service database
     *
     * @return Styx service instance
     */
    InetServer create(String name, RoutingObjectFactory.Context context, JsonNode serviceConfiguration, StyxObjectStore<StyxObjectRecord<InetServer>> serviceDb);
}
