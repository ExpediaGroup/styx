/*
  Copyright (C) 2013-2023 Expedia Inc.

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
package com.hotels.styx.infrastructure.configuration.json.mixins;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hotels.styx.api.extension.service.Http2ConnectionPoolSettings;

/**
 * Jackson annotations for {@link com.hotels.styx.api.extension.service.ConnectionPoolSettings}.
 */
public abstract class ConnectionPoolSettingsMixin {

    @JsonCreator
    ConnectionPoolSettingsMixin(@JsonProperty("maxConnectionsPerHost") Integer maxConnectionsPerHost,
                                @JsonProperty("maxPendingConnectionsPerHost") Integer maxPendingConnectionsPerHost,
                                @JsonProperty("connectTimeoutMillis") Integer connectTimeoutMillis,
                                @JsonProperty("socketTimeoutMillis") Integer socketTimeoutMillis,
                                @JsonProperty("pendingConnectionTimeoutMillis") Integer pendingConnectionTimeoutMillis,
                                @JsonProperty("connectionExpirationSeconds") Long connectionExpirationSeconds,
                                @JsonProperty("http2ConnectionPoolSettings") Http2ConnectionPoolSettings http2ConnectionPoolSettings) {
    }

    @JsonProperty("socketTimeoutMillis")
    public abstract int socketTimeoutMillis();

    @JsonProperty("connectTimeoutMillis")
    public abstract int connectTimeoutMillis();

    @JsonProperty("maxConnectionsPerHost")
    public abstract int maxConnectionsPerHost();

    @JsonProperty("maxPendingConnectionsPerHost")
    public abstract int maxPendingConnectionsPerHost();

    @JsonProperty("pendingConnectionTimeoutMillis")
    public abstract int pendingConnectionTimeoutMillis();

    @JsonProperty("connectionExpirationSeconds")
    public abstract long connectionExpirationSeconds();

    @JsonProperty("http2ConnectionPoolSettings")
    public abstract Http2ConnectionPoolSettings http2ConnectionPoolSettings();
}
