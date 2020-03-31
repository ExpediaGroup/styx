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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hotels.styx.common.Collections.listOf;
import static com.hotels.styx.common.Strings.isNotEmpty;
import static com.hotels.styx.common.io.ResourceFactory.newResource;
import static java.util.Collections.emptyList;
import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

/**
 * Https Connector configuration.
 */
@JsonDeserialize(builder = HttpsConnectorConfig.Builder.class)
public final class HttpsConnectorConfig extends HttpConnectorConfig {
    private final String sslProvider;
    private final String certificateFile;
    private final String certificateKeyFile;
    private final List<String> cipherSuites;
    private final long sessionTimeoutMillis;
    private final long sessionCacheSize;
    private final List<String> protocols;

    private HttpsConnectorConfig(Builder builder) {
        super(builder.port);
        this.sslProvider = builder.sslProvider;
        this.certificateFile = builder.certificateFile;
        this.certificateKeyFile = builder.certificateKeyFile;
        this.cipherSuites = builder.cipherSuites;
        this.sessionTimeoutMillis = builder.sessionTimeoutMillis;
        this.sessionCacheSize = builder.sessionCacheSize;
        this.protocols = builder.protocols;
    }

    @Override
    public String type() {
        return "https";
    }

    /**
     * Implementation of SSL functionality, can be JDK or OPENSSL.
     *
     * @return SSL provider
     */
    public String sslProvider() {
        return sslProvider;
    }

    public String certificateFile() {
        return certificateFile;
    }

    public String certificateKeyFile() {
        return certificateKeyFile;
    }

    /**
     * The cipher suites to enable, in the order of preference.
     *
     * @return cipher suites
     */
    public List<String> ciphers() {
        return cipherSuites;
    }

    /**
     * Timeout for the cached SSL session objects.
     *
     * @return timeout
     */
    public long sessionTimeoutMillis() {
        return sessionTimeoutMillis;
    }

    /**
     * Size of the cache used for storing SSL session objects.
     *
     * @return cache size
     */
    public long sessionCacheSize() {
        return sessionCacheSize;
    }

    /**
     * The TLS protocol versions to enable.
     *
     * @return protocols
     */
    public List<String> protocols() {
        return protocols;
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + hash(sslProvider, certificateFile, certificateKeyFile, sessionTimeoutMillis, sessionCacheSize, protocols);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }
        HttpsConnectorConfig other = (HttpsConnectorConfig) obj;
        return Objects.equals(this.sslProvider, other.sslProvider)
                && Objects.equals(this.certificateFile, other.certificateFile)
                && Objects.equals(this.certificateKeyFile, other.certificateKeyFile)
                && Objects.equals(this.sessionTimeoutMillis, other.sessionTimeoutMillis)
                && Objects.equals(this.sessionCacheSize, other.sessionCacheSize)
                && Objects.equals(this.protocols, other.protocols);
    }

    @Override
    public String toString() {
        return new StringBuilder(256)
                .append(this.getClass().getSimpleName())
                .append("{port=")
                .append(port())
                .append(", sslProvider=")
                .append(sslProvider)
                .append(", certificateFile=")
                .append(certificateFile)
                .append(", certificateKeyFile=")
                .append(certificateKeyFile)
                .append(", sessionTimeoutMillis=")
                .append(sessionTimeoutMillis)
                .append(", sessionCacheSize=")
                .append(sessionCacheSize)
                .append(", cipherSuites=")
                .append(cipherSuites)
                .append(", protocols=")
                .append(protocols != null
                        ? protocols.stream().filter(java.util.Objects::nonNull).collect(joining(","))
                        : "None")
                .append('}')
                .toString();
    }

    public boolean isConfigured() {
        return isNotEmpty(certificateFile) && isNotEmpty(certificateKeyFile);
    }

    /**
     * Builder for {@link HttpsConnectorConfig}.
     */
    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private int port;

        private String sslProvider = "JDK";
        private String certificateFile;
        private String certificateKeyFile;
        private long sessionTimeoutMillis = 300_000;
        private long sessionCacheSize;
        private List<String> cipherSuites = emptyList();
        private List<String> protocols = emptyList();

        @JsonProperty("port")
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder sslProvider(String sslProvider) {
            this.sslProvider = requireNonNull(sslProvider);
            return this;
        }

        public Builder certificateFile(String certificateFile) {
            this.certificateFile = newResource(certificateFile).absolutePath();
            return this;
        }

        public Builder certificateKeyFile(String certificateKeyFile) {
            this.certificateKeyFile = newResource(certificateKeyFile).absolutePath();
            return this;
        }

        public Builder cipherSuites(List<String> cipherSuites) {
            this.cipherSuites = listOf(requireNonNull(cipherSuites));
            return this;
        }

        public Builder sessionTimeout(long sessionTimeout, TimeUnit timeUnit) {
            this.sessionTimeoutMillis = timeUnit.toMillis(sessionTimeout);
            return this;
        }

        public Builder sessionCacheSize(long sessionCacheSize) {
            this.sessionCacheSize = sessionCacheSize;
            return this;
        }

        public Builder protocols(String... protocols) {
            this.protocols = listOf(protocols);
            return this;
        }

        public HttpsConnectorConfig build() {
            return new HttpsConnectorConfig(this);
        }
    }
}
