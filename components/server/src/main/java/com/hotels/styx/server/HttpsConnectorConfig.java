/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.server;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.base.Objects;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.hotels.styx.api.io.ResourceFactory.newResource;
import static java.util.stream.Collectors.toSet;

/**
 * Https Connector configuration.
 */
@JsonDeserialize(builder = HttpsConnectorConfig.Builder.class)
public final class HttpsConnectorConfig extends HttpConnectorConfig {
    private static final Iterable<String> DEFAULT_CIPHER_SUITES = EnumSet.allOf(CipherSuite.class).stream()
            .map(CipherSuite::name)
            .collect(toSet());

    private final String sslProvider;
    private final String certificateFile;
    private final String certificateKeyFile;
    private final Iterable<String> cipherSuites;
    private final long sessionTimeoutMillis;
    private final long sessionCacheSize;

    private HttpsConnectorConfig(Builder builder) {
        super(builder.port);
        this.sslProvider = builder.sslProvider;
        this.certificateFile = builder.certificateFile;
        this.certificateKeyFile = builder.certificateKeyFile;
        this.cipherSuites = Objects.firstNonNull(builder.cipherSuites, DEFAULT_CIPHER_SUITES);
        this.sessionTimeoutMillis = builder.sessionTimeoutMillis;
        this.sessionCacheSize = builder.sessionCacheSize;
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

    public Iterable<String> ciphers() {
        return cipherSuites;
    }

    public long sessionTimeoutMillis() {
        return sessionTimeoutMillis;
    }

    public long sessionCacheSize() {
        return sessionCacheSize;
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + Objects.hashCode(sslProvider, certificateFile, certificateKeyFile, sessionTimeoutMillis, sessionCacheSize);
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
                && Objects.equal(this.sessionCacheSize, other.sessionCacheSize);
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
                .toString();
    }

    public boolean isConfigured() {
        return !isNullOrEmpty(certificateFile) && !isNullOrEmpty(certificateKeyFile);
    }

    /**
     * Supported cipher suites are in order of preference.
     */
    public enum CipherSuite {
        TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
        TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
        TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,
        TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
        TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,
        TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
        TLS_RSA_WITH_AES_256_GCM_SHA384,
        TLS_RSA_WITH_AES_128_GCM_SHA256,
        TLS_RSA_WITH_AES_256_CBC_SHA256,
        TLS_RSA_WITH_AES_128_CBC_SHA256,
        TLS_RSA_WITH_AES_128_CBC_SHA;
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
        private List<String> cipherSuites;

        @JsonProperty("port")
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder sslProvider(String sslProvider) {
            this.sslProvider = checkNotNull(sslProvider);
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
            this.cipherSuites = checkNotNull(cipherSuites);
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

        public HttpsConnectorConfig build() {
            return new HttpsConnectorConfig(this);
        }
    }
}
