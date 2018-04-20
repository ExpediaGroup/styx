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

package com.hotels.styx.admin.handlers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.Identifiable;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.service.BackendService;
import com.hotels.styx.api.service.HealthCheckConfig;
import com.hotels.styx.api.service.RewriteConfig;
import com.hotels.styx.api.service.StickySessionConfig;
import com.hotels.styx.api.service.TlsSettings;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Objects.toStringHelper;
/**
 * Used to provide annotated properties for fields of {@link BackendService} object, required to keep naming of fields
 * used in configuration.
 */

public final class BackendServiceSerializer implements Identifiable {
    private final Id id;
    private final String path;
    private final ConnectionPool.Settings connectionPoolSettings;
    private final Set<Origin> origins;
    private final HealthCheckConfig healthCheckConfig;
    private final StickySessionConfig stickySessionConfig;
    private final List<RewriteConfig> rewrites;
    private final int responseTimeoutMillis;
    private final TlsSettings tlsSettings;

    public BackendServiceSerializer(BackendService backendService) {
        this.id = backendService.id();
        this.path = backendService.path();
        this.connectionPoolSettings = backendService.connectionPoolConfig();
        this.origins = backendService.origins();
        this.healthCheckConfig = backendService.healthCheckConfig();
        this.stickySessionConfig = backendService.stickySessionConfig();
        this.rewrites = backendService.rewrites();
        this.responseTimeoutMillis = backendService.responseTimeoutMillis();
        this.tlsSettings = backendService.tlsSettings().orElse(null);
    }

    @Override
    public Id id() {
        return this.id;
    }

    @JsonProperty("id")
    String idAsString() {
        return this.id.toString();
    }

    @JsonProperty("path")
    public String path() {
        return this.path;
    }

    @JsonProperty("origins")
    public Set<Origin> origins() {
        return this.origins;
    }

    @JsonProperty("connectionPool")
    public ConnectionPool.Settings connectionPoolConfig() {
        return this.connectionPoolSettings;
    }

    @JsonProperty("healthCheck")
    public HealthCheckConfig healthCheckConfig() {
        return healthCheckConfig;
    }

    @JsonProperty("stickySession")
    public StickySessionConfig stickySessionConfig() {
        return this.stickySessionConfig;
    }

    @JsonProperty("rewrites")
    public List<RewriteConfig> rewrites() {
        return this.rewrites;
    }

    @JsonProperty("responseTimeoutMillis")
    public int responseTimeoutMillis() {
        return this.responseTimeoutMillis;
    }

    public Optional<TlsSettings> tlsSettings() {
        return Optional.ofNullable(this.tlsSettings);
    }

    @JsonProperty("tlsSettings")
    private TlsSettings getTlsSettings() {
        return this.tlsSettings().orElse(null);
    }

    public BackendService.Protocol protocol() {
        if (tlsSettings == null) {
            return BackendService.Protocol.HTTP;
        } else {
            return BackendService.Protocol.HTTPS;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, path, connectionPoolSettings, origins,
                healthCheckConfig, stickySessionConfig, rewrites, responseTimeoutMillis);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        BackendServiceSerializer other = (BackendServiceSerializer) obj;
        return Objects.equals(this.id, other.id)
                && Objects.equals(this.path, other.path)
                && Objects.equals(this.connectionPoolSettings, other.connectionPoolSettings)
                && Objects.equals(this.origins, other.origins)
                && Objects.equals(this.healthCheckConfig, other.healthCheckConfig)
                && Objects.equals(this.stickySessionConfig, other.stickySessionConfig)
                && Objects.equals(this.rewrites, other.rewrites)
                && Objects.equals(this.tlsSettings, other.tlsSettings)
                && Objects.equals(this.responseTimeoutMillis, other.responseTimeoutMillis);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("id", this.id)
                .add("path", this.path)
                .add("origins", this.origins)
                .add("connectionPoolSettings", this.connectionPoolSettings)
                .add("healthCheckConfig", this.healthCheckConfig)
                .add("stickySessionConfig", this.stickySessionConfig)
                .add("rewrites", this.rewrites)
                .add("tlsSettings", this.tlsSettings)
                .toString();
    }
}
