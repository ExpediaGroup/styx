/**
 * Copyright (C) 2013-2017 Expedia Inc.
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
package com.hotels.styx.api.client.loadbalancing.spi;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.client.OriginsInventorySnapshot;
import com.hotels.styx.api.client.OriginsInventoryStateChangeListener;

import java.util.Collections;

/**
 * Used to sort a cluster of origins so that a request can be tried against them, failing over through the sequence if necessary.
 * The LoadBalancingStrategy can also respond to changes in the origins inventory state if needed.
 */
public interface LoadBalancingStrategy extends OriginsInventoryStateChangeListener {

    /**
     * Holds information about the current request execution.
     */
    interface Context {
        /**
         * Returns the ID of the application being load-balanced.
         *
         * @return an app ID
         */
        Id appId();

        /**
         * Returns the current request.
         *
         * @return current request
         */
        HttpRequest currentRequest();

        /**
         * Returns an origin's 500 error rate.
         *
         * @param origin an origin
         * @return one minute rate for 500 responses from {@code origin}
         */
        default double oneMinuteRateForStatusCode5xx(Origin origin) {
            return 0.0;
        }
    }

    /**
     * Sorts the specified origins according to the order that they should be tried.
     *
     * @param context load balancing context
     * @return the origins sorted into a new order
     */
    Iterable<ConnectionPool> vote(Context context);

    /**
     * Returns a collection of {@link ConnectionPool}, without any guarantee of ordering.
     * @return available, unordered origins
     */
    default Iterable<ConnectionPool> snapshot() {
        return Collections.emptyList();
    }



    default void originsInventoryStateChanged(OriginsInventorySnapshot snapshot) {
        // do nothing
    }
}
