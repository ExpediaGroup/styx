/**
 * Copyright (C) 2013-2018 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.client.stickysession;

import com.hotels.styx.api.HttpCookie;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.client.ActiveOrigins;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.OriginsInventorySnapshot;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;

import java.util.Collections;
import java.util.Optional;
import java.util.function.Predicate;

import static com.hotels.styx.client.stickysession.StickySessionCookie.stickySessionCookieName;
import static java.util.stream.StreamSupport.stream;

/**
 * A load balancing strategy that selects first a preferred origin.
 */
public class StickySessionLoadBalancingStrategy implements LoadBalancingStrategy {
    private final LoadBalancingStrategy delegate;
    private final ActiveOrigins activeOrigins;

    public StickySessionLoadBalancingStrategy(ActiveOrigins activeOrigins, LoadBalancingStrategy delegate) {
        this.delegate = delegate;
        this.activeOrigins = activeOrigins;
    }

    @Override
    public Iterable<ConnectionPool> vote(Context context) {
        return stickySessionOriginId(context)
                .flatMap(preferredOriginId -> originsById(activeOrigins.snapshot(), preferredOriginId))
                .orElseGet(() -> delegate.vote(context));
    }

    private Optional<Iterable<ConnectionPool>> originsById(Iterable<ConnectionPool> origins, Id id) {
        return originById(origins, id).map(Collections::singleton);
    }

    private Optional<ConnectionPool> originById(Iterable<ConnectionPool> origins, Id id) {
        return stream(origins.spliterator(), false)
                .filter(hasId(id))
                .findFirst();
    }

    private static Optional<Id> stickySessionOriginId(Context context) {
        return context.currentRequest()
                .cookie(stickySessionCookieName(context.appId()))
                .map(HttpCookie::value)
                .map(Id::id);
    }

    private static Predicate<ConnectionPool> hasId(Id id) {
        return input -> input.getOrigin().id().equals(id);
    }

    @Override
    public void originsInventoryStateChanged(OriginsInventorySnapshot snapshot) {
        delegate.originsInventoryStateChanged(snapshot);
    }
}
