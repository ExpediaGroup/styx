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

import com.google.common.base.Ticker;
import com.hotels.styx.api.client.Connection;
import com.hotels.styx.api.client.ConnectionPool;

import java.util.function.Supplier;

/**
 * Tracks connections usage and expires them based on configured expiration time.
 */
public class ConnectionTrackerDecorator implements ConnectionDecorator {

    private static Supplier<Ticker> systemTicker = Ticker::systemTicker;
    private final Long connectionExpirationSeconds;

    public ConnectionTrackerDecorator(ConnectionPool.Settings settings) {
        this.connectionExpirationSeconds = settings.connectionExpirationSeconds();
    }

    @Override
    public Connection decorate(Connection connection) {
        if (connectionExpirationSeconds > 0) {
            return new TrackedConnectionAdapter(connection, connectionExpirationSeconds, systemTicker);
        } else {
            return connection;
        }
    }
}
