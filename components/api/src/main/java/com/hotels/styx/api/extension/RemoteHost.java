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
package com.hotels.styx.api.extension;

import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancingMetric;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancingMetricSupplier;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A load balanced host.
 */
public final class RemoteHost {
    private final Origin origin;
    private final HttpHandler hostClient;
    private final LoadBalancingMetricSupplier metricSupplier;

    private RemoteHost(Origin origin, HttpHandler hostClient, LoadBalancingMetricSupplier metricSupplier) {
        this.origin = requireNonNull(origin);
        this.hostClient = requireNonNull(hostClient);
        this.metricSupplier = requireNonNull(metricSupplier);
    }

    public static RemoteHost remoteHost(Origin origin, HttpHandler client, LoadBalancingMetricSupplier metricSupplier) {
        return new RemoteHost(origin, client, metricSupplier);
    }

    public Id id() {
        return origin().id();
    };

    public Origin origin() {
        return this.origin;
    }

    public HttpHandler hostClient() {
        return hostClient;
    }

    public LoadBalancingMetric metric() {
        return metricSupplier.loadBalancingMetric();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("RemoteHost{");
        sb.append("origin=").append(origin);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        RemoteHost that = (RemoteHost) o;
        return Objects.equals(origin, that.origin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(origin);
    }
}
