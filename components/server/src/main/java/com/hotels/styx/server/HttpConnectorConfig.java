/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx.server;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Http Connector config.
 */
public class HttpConnectorConfig implements ConnectorConfig {
    private int port;

    public HttpConnectorConfig(@JsonProperty("port") Integer port) {
        this.port = port;
    }

    public HttpConnectorConfig port(int port) {
        this.port = port;
        return this;
    }

    @Override
    public int port() {
        return this.port;
    }

    @Override
    public String type() {
        return "http";
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(port);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final HttpConnectorConfig other = (HttpConnectorConfig) obj;
        return Objects.equals(this.port, other.port);
    }


    @Override
    public String toString() {
        return new StringBuilder(32)
                .append(this.getClass().getSimpleName())
                .append("{port=")
                .append(port)
                .append('}')
                .toString();
    }
}
