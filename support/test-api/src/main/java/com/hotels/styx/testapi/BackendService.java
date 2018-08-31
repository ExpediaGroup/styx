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
package com.hotels.styx.testapi;

import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.TlsSettings;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.extension.service.BackendService.newBackendServiceBuilder;

import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toSet;

/**
 * A backend service made of one or more origins that Styx can route proxied requests to.
 */
public class BackendService {
    private final Set<Origin> origins = new HashSet<>();
    private int responseTimeoutMillis;
    private boolean ssl;

    /**
     * Set the response timeout for this backend.
     *
     * @param responseTimeout response timeout
     * @param timeUnit        unit that timeout is measured in
     * @return this builder
     */
    public BackendService responseTimeout(int responseTimeout, TimeUnit timeUnit) {
        this.responseTimeoutMillis = (int) timeUnit.toMillis(responseTimeoutMillis);
        return this;
    }

    /**
     * Adds a set of origins.
     *
     * @param origins origins
     * @return this builder
     */
    public BackendService addOrigins(Collection<Origin> origins) {
        this.origins.addAll(origins);
        return this;
    }

    /**
     * Adds an origin.
     *
     * @param origin origin
     * @return this builder
     */
    public BackendService addOrigin(Origin origin) {
        this.origins.add(origin);
        return this;
    }

    /**
     * Adds an origin.
     *
     * @param host hostname
     * @param port port number
     * @return this builder
     */
    public BackendService addOrigin(String host, int port) {
        this.origins.add(Origins.origin(host, port));
        return this;
    }

    /**
     * Adds an origin with its host set to "localhost".
     *
     * @param port port number
     * @return this builder
     */
    public BackendService addOrigin(int port) {
        this.origins.add(Origins.origin(port));
        return this;
    }

    public BackendService ssl() {
        this.ssl = true;
        return this;
    }

    // for internal use
    com.hotels.styx.api.extension.service.BackendService createBackendService(String path) {
        requireNonNull(path, "path must not be null");
        checkArgument(!origins.isEmpty(), "A backend service must have at least one origin");

        String appId = newId();

        Set<Origin> adaptedOrigins = this.origins.stream()
                .map(origin -> newOriginBuilder(origin.host().getHostText(), origin.host().getPort())
                        .applicationId(appId)
                        .id(newId())
                        .build())
                .collect(toSet());

        com.hotels.styx.api.extension.service.BackendService.Builder builder = newBackendServiceBuilder()
                .id(appId)
                .responseTimeoutMillis(responseTimeoutMillis)
                .origins(adaptedOrigins)
                .path(path);

        if (ssl) {
            builder = builder.https(new TlsSettings.Builder()
                    .trustAllCerts(true)
                    .build());
        }

        return builder
                .build();
    }

    private static String newId() {
        return randomUUID().toString();
    }
}
