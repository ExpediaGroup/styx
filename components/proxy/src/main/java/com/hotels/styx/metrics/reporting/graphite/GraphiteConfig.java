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
package com.hotels.styx.metrics.reporting.graphite;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Configuration for graphite.
 */
public class GraphiteConfig {
    private final String host;
    private final int port;
    private final long intervalMillis;
    private final String prefix;
    private final boolean enabled;
    private final boolean tagsEnabled;

    @JsonCreator
    GraphiteConfig(@JsonProperty("host") String host,
                   @JsonProperty("port") Integer port,
                   @JsonProperty("intervalMillis") Long intervalMillis,
                   @JsonProperty("prefix") String prefix,
                   @JsonProperty("enabled") Boolean enabled,
                   @JsonProperty("tagsEnabled") Boolean tagsEnabled) {
        this.host = host;
        this.port = ofNullable(port).orElse(9090);
        this.intervalMillis = ofNullable(intervalMillis).orElse(SECONDS.toMillis(5));
        this.prefix = ofNullable(prefix).orElse("");
        this.enabled = ofNullable(enabled).orElse(true);
        this.tagsEnabled = ofNullable(tagsEnabled).orElse(false);
    }

    @JsonProperty("prefix")
    public String prefix() {
        return prefix;
    }

    @JsonProperty("host")
    public String host() {
        return host;
    }

    @JsonProperty("port")
    public int port() {
        return port;
    }

    @JsonProperty("intervalMillis")
    public long intervalMillis() {
        return intervalMillis;
    }

    @JsonProperty("enabled")
    public boolean enabled() {
        return enabled;
    }

    @JsonProperty("tagsEnabled")
    public boolean tagsEnabled() {
        return tagsEnabled;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName()
                + "{"
                + "host=" + host + ","
                + "port=" + port + ","
                + "intervalMillis=" + intervalMillis + ","
                + "enabled=" + enabled + ","
                + "tagsEnabled=" + tagsEnabled
                + "}";
    }
}
