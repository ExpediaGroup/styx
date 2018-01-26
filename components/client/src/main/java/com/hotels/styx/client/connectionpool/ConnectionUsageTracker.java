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
package com.hotels.styx.client.connectionpool;

import com.hotels.styx.api.client.Connection;

/**
 * Provides methods used for decorating and tracking connections for additional conditions for expiration.
 */
interface ConnectionUsageTracker {

    /**
     * Used for decoration of a connection to provide expiration tracking logic.
     * @param connection connection to decorate
     * @return decorated connection
     */
    Connection decorate(Connection connection);

    /**
     * Provides logic for checking if connection is usable.
     * @param connection connection under test.
     * @return value whether the connection has expired.
     */
    boolean shouldTerminate(Connection connection);

    /**
     * Creates a {@link ConnectionUsageTracker} that doesn't affect a tracked connection.
     * @return tracker that doesn't change the connection characteristics.
     */
    static Factory identityConnectionTrackerFactory() {
        return () -> new ConnectionUsageTracker() {

            @Override
            public Connection decorate(Connection connection) {
                return connection;
            }

            @Override
            public boolean shouldTerminate(Connection connection) {
                return false;
            }
        };
    }

    /**
     * Creates a {@link ConnectionUsageTracker}.
     */
    interface Factory {
        ConnectionUsageTracker createTracker();
    }
}
