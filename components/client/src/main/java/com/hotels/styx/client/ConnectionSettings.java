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
package com.hotels.styx.client;

import com.hotels.styx.api.client.Connection;
import com.hotels.styx.client.ssl.TlsSettings;

/**
 * Connections settings.
 */
public final class ConnectionSettings implements Connection.Settings {
    private final int connectTimeoutMillis;
    private final int socketTimeoutMillis;

    /**
     * Constructor that will take timeouts as longs. Note that they will be treated as ints internally,
     * so numbers above the int maximum value will wrap around, however, this is not likely to be a practical concern
     * as such a duration would be over 24 days long.
     * <p>
     * This constructor exists for convenience.
     * @param tlsSettings SSL Settings
     * @param connectTimeoutMillis socket connection timeout in milliseconds
     * @param socketTimeoutMillis  socket read/write timeout in milliseconds
     */
    public ConnectionSettings(long connectTimeoutMillis, long socketTimeoutMillis, TlsSettings tlsSettings) {
        this((int) connectTimeoutMillis, (int) socketTimeoutMillis, tlsSettings);
    }

    /**
     * Construct settings that doesn't support SSL.
     *
     * @param connectTimeoutMillis socket connection timeout in milliseconds
     * @param socketTimeoutMillis socket read/write timeout in milliseconds
     */
    public ConnectionSettings(long connectTimeoutMillis, long socketTimeoutMillis) {
        this((int) connectTimeoutMillis, (int) socketTimeoutMillis, null);
    }

    /**
     * Construct settings that doesn't support SSL.
     *
     * @param connectTimeoutMillis socket connection timeout in milliseconds
     * @param socketTimeoutMillis socket connection timeout in milliseconds
     */
    public ConnectionSettings(int connectTimeoutMillis, int socketTimeoutMillis) {
        this(connectTimeoutMillis, socketTimeoutMillis, null);
    }

    /**
     * Construct settings with SSL.
     *
     * @param connectTimeoutMillis socket connection timeout in milliseconds
     * @param socketTimeoutMillis  socket read/write timeout in milliseconds
     * @param tlsSettings SSL Settings
     */
    public ConnectionSettings(int connectTimeoutMillis, int socketTimeoutMillis, TlsSettings tlsSettings) {
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.socketTimeoutMillis = socketTimeoutMillis;
    }

    @Override
    public int socketTimeoutMillis() {
        return socketTimeoutMillis;
    }

    @Override
    public int connectTimeoutMillis() {
        return connectTimeoutMillis;
    }

}
