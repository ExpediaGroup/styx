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
package com.hotels.styx.infrastructure.configuration.json.mixins;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.service.BackendService;
import com.hotels.styx.api.service.ConnectionPoolSettings;
import com.hotels.styx.api.service.HealthCheckConfig;
import com.hotels.styx.api.service.RewriteConfig;
import com.hotels.styx.api.service.StickySessionConfig;
import com.hotels.styx.api.service.TlsSettings;

import java.util.List;
import java.util.Set;

@JsonDeserialize(builder = BackendService.Builder.class)
public interface BackendServiceMixin {
    @JsonProperty("id")
    String idAsString() ;

    @JsonProperty("path")
    String path() ;

    @JsonProperty("origins")
    Set<Origin> origins() ;

    @JsonProperty("connectionPool")
    ConnectionPool.Settings connectionPoolConfig() ;

    @JsonProperty("healthCheck")
    HealthCheckConfig healthCheckConfig() ;

    @JsonProperty("stickySession")
    StickySessionConfig stickySessionConfig() ;

    @JsonProperty("rewrites")
    List<RewriteConfig> rewrites() ;

    @JsonProperty("responseTimeoutMillis")
    int responseTimeoutMillis() ;

    @JsonProperty("tlsSettings")
    TlsSettings getTlsSettings();

    @JsonPOJOBuilder(buildMethodName = "build", withPrefix = "")
    interface Builder {
        @JsonProperty("id")
        BackendService.Builder id(String id);

        @JsonProperty("path")
        BackendService.Builder path(String path);

        @JsonProperty("responseTimeoutMillis")
        BackendService.Builder responseTimeoutMillis(int timeout);

        @JsonProperty("origins")
        BackendService.Builder origins(Set<Origin> origins);

        @JsonProperty("sslSettings")
        BackendService.Builder httpsOld(TlsSettings tlsSettings);

        @JsonProperty("tlsSettings")
        BackendService.Builder https(TlsSettings tlsSettings);

        @JsonProperty("rewrites")
        BackendService.Builder rewrites(List<RewriteConfig> rewriteConfigs);

        @JsonProperty("connectionPool")
        BackendService.Builder connectionPoolConfig(ConnectionPoolSettings connectionPoolSettings);

        BackendService.Builder connectionPoolConfig(ConnectionPool.Settings connectionPoolSettings);

        @JsonProperty("stickySession")
        BackendService.Builder stickySessionConfig(StickySessionConfig stickySessionConfig);

        @JsonProperty("healthCheck")
        BackendService.Builder healthCheckConfig(HealthCheckConfig healthCheckConfig);
    }

}
