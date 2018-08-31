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
package com.hotels.styx.client;

import com.hotels.styx.api.extension.service.TlsSettings;

/**
 * Connection configuration.
 */
public class ConnectionSettings {
    private final int connectTimeoutMillis;

    /**
     * Constructor that will take timeouts as longs. Note that they will be treated as ints internally,
     * so numbers above the int maximum value will wrap around, however, this is not likely to be a practical concern
     * as such a duration would be over 24 days long.
     * <p>
     * This constructor exists for convenience.
     * @param tlsSettings SSL Settings
     * @param connectTimeoutMillis socket connection timeout in milliseconds
     */
    public ConnectionSettings(long connectTimeoutMillis, TlsSettings tlsSettings) {
        this((int) connectTimeoutMillis, tlsSettings);
    }

    /**
     * Construct settings that doesn't support SSL.
     *
     * @param connectTimeoutMillis socket connection timeout in milliseconds
     */
    public ConnectionSettings(long connectTimeoutMillis) {
        this((int) connectTimeoutMillis, null);
    }

    /**
     * Construct settings that doesn't support SSL.
     *
     * @param connectTimeoutMillis socket connection timeout in milliseconds
     */
    public ConnectionSettings(int connectTimeoutMillis) {
        this(connectTimeoutMillis, null);
    }

    /**
     * Construct settings with SSL.
     *
     * @param connectTimeoutMillis socket connection timeout in milliseconds
     * @param tlsSettings SSL Settings
     */
    public ConnectionSettings(int connectTimeoutMillis, TlsSettings tlsSettings) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int connectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    /**
     * Deprecated and due to be removed in a future release.
     *
     * @return Always returns a constant 0.
     */
    @Deprecated
    public int socketTimeoutMillis() {
        return 0;
    }
}
