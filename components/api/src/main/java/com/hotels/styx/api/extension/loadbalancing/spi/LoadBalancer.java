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
package com.hotels.styx.api.extension.loadbalancing.spi;

import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.OriginsSnapshot;
import com.hotels.styx.api.extension.OriginsChangeListener;
import com.hotels.styx.api.extension.RemoteHost;

import java.util.List;
import java.util.Optional;

/**
 * Styx Load Balancer Interface.
 */
public interface LoadBalancer extends OriginsChangeListener {
    /**
     * Asks the load balancer to choose a remote host.
     *
     * @param preferences   Allows consumer to influence load balancer's decision.
     *
     *                      Whether or not the load balancer considers the requested preferences
     *                      depends entirely on the load balancer implementation.
     *
     *                      Pass in 'null' to indicate no preferences.
     *
     *
     * @return              An optional of RemoteHost.
     */
    Optional<RemoteHost> choose(LoadBalancer.Preferences preferences);

    default void originsChanged(OriginsSnapshot snapshot) {
        // Do nothing
    }

    /**
     * Preferences requested from the load balancer.
     *
     * Consumer may ask load balancer to prefer some hosts over others by
     * using the preferences object.
     *
     * Load balancer is under no obligation to actually satisfy these requirements.
     * Instead, load balancer may choose to ignore them. It is at its own discretion
     * if the preferences are honoured.
     *
     */
    interface Preferences {
        /**
         * Returns an Optional regular expression pattern matching the preferred origin IDs.
         *
         * @return An Optional regular expression string.
         */
        Optional<String> preferredOrigins();

        /**
         * Returns List of origin IDs to avoid.
         *
         * @return A list of origins.
         */
        List<Origin> avoidOrigins();
    }
}
