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
package com.hotels.styx.routing.config2;

import com.hotels.styx.Environment;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.routing.RoutingObject;
import com.hotels.styx.routing.RoutingObjectRecord;
import com.hotels.styx.routing.config.HttpInterceptorFactory;
import com.hotels.styx.routing.db.StyxObjectStore;
import com.hotels.styx.routing.handlers.RouteRefLookup;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * A factory for constructing Styx routing objects from a StyxObjectDefinition
 * yaml config block.
 */
public interface StyxObject<T> {
    /**
     * Constructs a RoutingObject instance according to configuration block.
     * <p>
     * The routing object is constructed according to the definition codified in
     * the StyxObjectDefinition instance. Context provides access to
     * core Styx components necessary for constructing dependant objects.
     * <p>
     *
     * @param context a routing object factory context
     * @return a RoutingObject with all dependant objects
     */
    T build(@NotNull Context context);

    String type();

    /**
     * Contextual information for factory class.
     *
     * Provides contextual information for the factory class to allow HttpHandlers
     * to integrate into Styx runtime environment.
     */
    class Context {
        private final RouteRefLookup refLookup;
        private final Environment environment;
        private final StyxObjectStore<RoutingObjectRecord<RoutingObject>> routeDb;
        private final Iterable<NamedPlugin> plugins;
        private final Map<String, HttpInterceptorFactory> interceptorFactories;
        private final boolean requestTracking;

        public Context(
                RouteRefLookup refLookup,
                Environment environment,
                StyxObjectStore<RoutingObjectRecord<RoutingObject>> routeDb,
                Iterable<NamedPlugin> plugins,
                Map<String, HttpInterceptorFactory> interceptorFactories,
                boolean requestTracking) {
            this.refLookup = refLookup;
            this.environment = requireNonNull(environment);
            this.routeDb = requireNonNull(routeDb);
            this.plugins = requireNonNull(plugins);
            this.interceptorFactories = requireNonNull(interceptorFactories);
            this.requestTracking = requestTracking;
        }

        public Environment environment() {
            return environment;
        }

        public StyxObjectStore<RoutingObjectRecord<RoutingObject>> routeDb() {
            return routeDb;
        }

        public Iterable<NamedPlugin> plugins() {
            return plugins;
        }

        public Map<String, HttpInterceptorFactory> interceptorFactories() {
            return interceptorFactories;
        }

        public boolean requestTracking() {
            return requestTracking;
        }

        public RouteRefLookup refLookup() {
            return refLookup;
        }
    }
}
