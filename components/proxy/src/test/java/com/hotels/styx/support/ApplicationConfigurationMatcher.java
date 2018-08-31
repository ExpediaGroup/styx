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
package com.hotels.styx.support;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.ConnectionPoolSettings;
import com.hotels.styx.api.extension.service.RewriteConfig;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.HealthCheckConfig;
import com.hotels.styx.api.extension.service.StickySessionConfig;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.extension.service.ConnectionPoolSettings.DEFAULT_CONNECT_TIMEOUT_MILLIS;
import static com.hotels.styx.api.extension.service.ConnectionPoolSettings.DEFAULT_MAX_CONNECTIONS_PER_HOST;
import static com.hotels.styx.api.extension.service.ConnectionPoolSettings.DEFAULT_MAX_PENDING_CONNECTIONS_PER_HOST;
import static com.hotels.styx.api.extension.service.StickySessionConfig.stickySessionDisabled;
import static java.util.Collections.emptyList;

public class ApplicationConfigurationMatcher extends TypeSafeMatcher<BackendService> {
    private String name;
    private String path;
    private Set<Origin> origins;
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT_MILLIS;
    private int maxConnectionsPerHost = DEFAULT_MAX_CONNECTIONS_PER_HOST;
    private int maxPendingConnectionsPerHost = DEFAULT_MAX_PENDING_CONNECTIONS_PER_HOST;
    private HealthCheckConfig healthCheckConfig = null;
    private StickySessionConfig stickySessionConfig = stickySessionDisabled();
    private List<RewriteConfig> rewrites = emptyList();

    public static ApplicationConfigurationMatcher anApplication() {
        return new ApplicationConfigurationMatcher();
    }

    public ApplicationConfigurationMatcher withName(String name) {
        this.name = name;
        return this;
    }

    public ApplicationConfigurationMatcher withPath(String path) {
        this.path = path;
        return this;
    }

    public ApplicationConfigurationMatcher withOrigins(Origin... origins) {
        this.origins = ImmutableSet.copyOf(origins);
        return this;
    }

    public ApplicationConfigurationMatcher withRewrites(RewriteConfig... urlRewrites) {
        this.rewrites = ImmutableList.copyOf(urlRewrites);
        return this;
    }

    public ApplicationConfigurationMatcher withConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    public ApplicationConfigurationMatcher withMaxConnectionsPerHost(int maxConnectionsPerHost) {
        this.maxConnectionsPerHost = maxConnectionsPerHost;
        return this;
    }

    public ApplicationConfigurationMatcher withMaxPendingConnectionsPerHost(int maxPendingConnectionsPerHost) {
        this.maxPendingConnectionsPerHost = maxPendingConnectionsPerHost;
        return this;
    }

    public ApplicationConfigurationMatcher withHealthCheckConfig(HealthCheckConfig healthCheckConfig) {
        this.healthCheckConfig = healthCheckConfig;
        return this;
    }

    public ApplicationConfigurationMatcher withStickySessionConfig(StickySessionConfig stickySessionConfig) {
        this.stickySessionConfig = stickySessionConfig;
        return this;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("Application with");
        description.appendText(" name=" + name);
        description.appendText(", path=" + path);
        description.appendText(", origins=" + origins);
        description.appendText(", connectTimeout=" + connectTimeout);
        description.appendText(", maxConnectionsPerHost=" + maxConnectionsPerHost);
        description.appendText(", maxPendingConnectionsPerHost=" + maxPendingConnectionsPerHost);
        description.appendText(", healthCheckConfig=" + healthCheckConfig);
        description.appendText(", stickySessionConfig=" + stickySessionConfig);
        description.appendText(", rewrites=" + rewrites);
    }

    @Override
    protected boolean matchesSafely(BackendService backendService) {
        ConnectionPoolSettings settings = backendService.connectionPoolConfig();

        return Objects.equals(id(name), backendService.id())
                && Objects.equals(path, backendService.path())
                && Objects.equals(origins, backendService.origins())
                && Objects.equals(connectTimeout, settings.connectTimeoutMillis())
                && Objects.equals(maxConnectionsPerHost, settings.maxConnectionsPerHost())
                && Objects.equals(maxPendingConnectionsPerHost, settings.maxPendingConnectionsPerHost())
                && Objects.equals(healthCheckConfig, backendService.healthCheckConfig())
                && Objects.equals(stickySessionConfig, backendService.stickySessionConfig())
                && Objects.equals(rewrites, backendService.rewrites());
    }
}