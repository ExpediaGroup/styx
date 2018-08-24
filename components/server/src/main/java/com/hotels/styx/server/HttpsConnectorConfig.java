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
package com.hotels.styx.server;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.hotels.styx.common.Joiners;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.hotels.styx.common.io.ResourceFactory.newResource;
import static java.util.Objects.requireNonNull;

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

    public String sslProvider() {
        return sslProvider;
    }

    public String certificateFile() {
        return certificateFile;
    }

    public String certificateKeyFile() {
        return certificateKeyFile;
    }

    public List<String> ciphers() {
        return cipherSuites;
    }

    public long sessionTimeoutMillis() {
        return sessionTimeoutMillis;
    }

    public long sessionCacheSize() {
        return sessionCacheSize;
    }

    public List<String> protocols() {
        return protocols;
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + Objects.hashCode(sslProvider, certificateFile, certificateKeyFile, sessionTimeoutMillis, sessionCacheSize, protocols);
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
        return Objects.equal(this.sslProvider, other.sslProvider)
                && Objects.equal(this.certificateFile, other.certificateFile)
                && Objects.equal(this.certificateKeyFile, other.certificateKeyFile)
                && Objects.equal(this.sessionTimeoutMillis, other.sessionTimeoutMillis)
                && Objects.equal(this.sessionCacheSize, other.sessionCacheSize)
                && Objects.equal(this.protocols, other.protocols);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("port", port())
                .add("sslProvider", sslProvider)
                .add("certificateFile", certificateFile)
                .add("certificateKeyFile", certificateKeyFile)
                .add("sessionTimeoutMillis", sessionTimeoutMillis)
                .add("sessionCacheSize", sessionCacheSize)
                .add("cipherSuites", cipherSuites)
                .add("protocols", protocols != null ? Joiners.JOINER_ON_COMMA.join(protocols) : "None")
                .toString();
    }

    public boolean isConfigured() {
        return !isNullOrEmpty(certificateFile) && !isNullOrEmpty(certificateKeyFile);
    }

    /**
     * Builder for {@link HttpsConnectorConfig}.
     */
    @JsonPOJOBuilder(buildMethodName = "build", withPrefix = "")
    public static class Builder {
        private int port;

        private String sslProvider = "JDK";
        private String certificateFile;
        private String certificateKeyFile;
        private long sessionTimeoutMillis = 300_000;
        private long sessionCacheSize;
        private List<String> cipherSuites = Collections.emptyList();
        private List<String> protocols = Collections.emptyList();

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
            this.cipherSuites = ImmutableList.copyOf(requireNonNull(cipherSuites));
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
            this.protocols = ImmutableList.copyOf(protocols);
            return this;
        }

        public HttpsConnectorConfig build() {
            return new HttpsConnectorConfig(this);
        }
    }
}
