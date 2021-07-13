/*
  Copyright (C) 2013-2021 Expedia Inc.

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
package com.hotels.styx.routing;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.ResponseEventListener;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancingMetric;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.LongAdder;

import static java.util.Objects.requireNonNull;

/**
 * Decorates a RoutingObject instance with additional metadata. This includes, but not
 * limited to, a load balancing metric. Styx core needs this metadata to use the
 * routing object in the forwarding path.
 */
public class RoutingMetadataDecorator implements RoutingObject {

    private final RoutingObject delegate;

    private final LongAdder allRequests = new LongAdder();
    private final LongAdder finishedRequests = new LongAdder();

    /**
     * Routing object adapater constructor.
     * @param routingObject
     */
    public RoutingMetadataDecorator(RoutingObject routingObject) {
        this.delegate = requireNonNull(routingObject);
    }

    @Override
    public Eventual<LiveHttpResponse> handle(LiveHttpRequest request, HttpInterceptor.Context context) {
        allRequests.increment();

        return new Eventual<>(
                ResponseEventListener.from(this.delegate.handle(request, context))
                        .whenFinished(finishedRequests::increment)
                        .apply());
    }

    @Override
    public CompletableFuture<Void> stop() {
        return delegate.stop();
    }

    public LoadBalancingMetric metric() {
        return new LoadBalancingMetric(allRequests.intValue() - finishedRequests.intValue());
    }
}
