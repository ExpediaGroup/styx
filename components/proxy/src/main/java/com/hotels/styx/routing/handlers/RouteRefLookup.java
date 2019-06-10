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
package com.hotels.styx.routing.handlers;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.routing.RoutingObject;
import com.hotels.styx.routing.RoutingObjectRecord;
import com.hotels.styx.routing.config.RoutingObjectReference;
import com.hotels.styx.routing.db.StyxObjectStore;

import java.util.Optional;

import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * Resolves a routing object reference from route database.
 */
public interface RouteRefLookup {
    // Consider modifying this interface to return Optional<RoutingObject>.
    // Then we can move .orElse handler to RoutingObjectFactory. This will
    // prevent NPEs in test RouteRefLookup implementations.
    RoutingObject apply(RoutingObjectReference route);

    /**
     * A StyxObjectStore based route reference lookup function.
     */
    class RouteDbRefLookup implements RouteRefLookup {
        private final StyxObjectStore<RoutingObjectRecord> routeDatabase;

        public RouteDbRefLookup(StyxObjectStore<RoutingObjectRecord> routeDatabase) {
            this.routeDatabase = requireNonNull(routeDatabase);
        }

        @Override
        public RoutingObject apply(RoutingObjectReference route) {
            Optional<RoutingObjectRecord> routingObjectRecord = this.routeDatabase.get(route.name());

            return routingObjectRecord
                    .map(it -> (RoutingObject) it.getRoutingObject())
                    .orElse((liveRequest, na) -> {
                        liveRequest.consume();

                        return Eventual.of(response(NOT_FOUND)
                                .body("Not found: " + route.name(), UTF_8)
                                .build()
                                .stream()
                        );
                    });
        }
    }
}
