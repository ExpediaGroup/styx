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
package com.hotels.styx.client.healthcheck;

import com.hotels.styx.api.extension.Origin;

/**
 * A function that checks the health of origins.
 */
public interface OriginHealthCheckFunction {
    /**
     * An enum that can be used as the type of the health-check result. Possible values are HEALTHY and UNHEALTHY.
     */
    enum OriginState {
        HEALTHY,
        UNHEALTHY
    }

    /**
     * Check the health of an origin.
     *
     * @param origin an origin
     * @param responseCallback a callback to be called when an origin state has been determined by a health-check
     */
    void check(Origin origin, Callback responseCallback);

    /**
     * A callback interface to communicate origin health check outcome.
     */
    interface Callback {
        /**
         * Called when an origin state has been determined by a health-check.
         *
         * @param state the state of the origin
         */
        void originStateResponse(OriginState state);
    }
}
