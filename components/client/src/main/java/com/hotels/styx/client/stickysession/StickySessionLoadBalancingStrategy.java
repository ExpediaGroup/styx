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
package com.hotels.styx.client.stickysession;

import com.hotels.styx.api.extension.ActiveOrigins;
import com.hotels.styx.api.extension.RemoteHost;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer;

import java.util.Optional;

import static java.util.stream.StreamSupport.stream;

/**
 * A load balancing strategy that selects first a preferred origin.
 */
public class StickySessionLoadBalancingStrategy implements LoadBalancer {
    private final LoadBalancer delegate;
    private final ActiveOrigins activeOrigins;

    public StickySessionLoadBalancingStrategy(ActiveOrigins activeOrigins, LoadBalancer delegate) {
        this.delegate = delegate;
        this.activeOrigins = activeOrigins;
    }

    @Override
    public Optional<RemoteHost> choose(LoadBalancer.Preferences context) {
        return context.preferredOrigins()
                .flatMap(preferredHost -> originById(activeOrigins.snapshot(), preferredHost))
                .map(Optional::of)
                .orElse(delegate.choose(context));
    }

    private Optional<RemoteHost> originById(Iterable<RemoteHost> origins, String id) {
        return stream(origins.spliterator(), false)
                .filter(host -> host.id().toString().equals(id))
                .findFirst();
    }

}
