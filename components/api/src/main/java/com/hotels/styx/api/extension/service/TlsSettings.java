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
package com.hotels.styx.api.extension.service;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.base.Objects.toStringHelper;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;

/**
 * SSL settings for a connection or pool.
 */
public class TlsSettings {

    private static final String DEFAULT_TRUST_STORE_PATH = System.getProperty("java.home")
            + File.separator + "lib" + File.separator + "security" + File.separator + "cacerts";

    private static final String DEFAULT_SSL_PROVIDER = "JDK";

    private final boolean trustAllCerts;
    private final String sslProvider;
    private final Set<Certificate> additionalCerts;
    private final String trustStorePath;
    private final char[] trustStorePassword;
    private final List<String> protocols;
    private final List<String> cipherSuites;

    private TlsSettings(Builder builder) {
        this.trustAllCerts = requireNonNull(builder.trustAllCerts);
        this.sslProvider = requireNonNull(builder.sslProvider);
        this.additionalCerts = builder.additionalCerts;
        this.trustStorePath = builder.trustStorePath;
        this.trustStorePassword = toCharArray(builder.trustStorePassword);
        this.protocols = ImmutableList.copyOf(builder.protocols);
        this.cipherSuites = ImmutableList.copyOf(builder.cipherSuites);
    }

    private char[] toCharArray(String password) {
        return password == null ? "".toCharArray() : password.toCharArray();
    }

    public boolean trustAllCerts() {
        return trustAllCerts;
    }

    public boolean authenticate() {
        return !trustAllCerts;
    }

    public String sslProvider() {
        return sslProvider;
    }

    public Set<Certificate> additionalCerts() {
        return additionalCerts;
    }

    public String trustStorePath() {
        return trustStorePath;
    }

    public char[] trustStorePassword() {
        return trustStorePassword;
    }

    public List<String> protocols() {
        return protocols;
    }

    public List<String> cipherSuites() {
        return this.cipherSuites;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        TlsSettings other = (TlsSettings) obj;
        return Objects.equals(this.trustAllCerts, other.trustAllCerts)
                && Objects.equals(this.sslProvider, other.sslProvider)
                && Objects.equals(this.additionalCerts, other.additionalCerts)
                && Objects.equals(this.trustStorePath, other.trustStorePath)
                && Arrays.equals(this.trustStorePassword, other.trustStorePassword)
                && Objects.equals(this.protocols, other.protocols)
                && Objects.equals(this.cipherSuites, other.cipherSuites);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("trustAllCerts", this.trustAllCerts)
                .add("sslProvider", this.sslProvider)
                .add("additionalCerts", this.additionalCerts)
                .add("trustStorePath", this.trustStorePath)
                .add("trustStorePassword", this.trustStorePassword)
                .add("protocols", this.protocols)
                .add("cipherSuites", this.cipherSuites)
                .toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(trustAllCerts, sslProvider, additionalCerts,
                trustStorePath, Arrays.hashCode(trustStorePassword), protocols, cipherSuites);
    }


    /**
     * The builder for SSL settings.
     */
    public static final class Builder {
        private boolean trustAllCerts = true;
        private String sslProvider = DEFAULT_SSL_PROVIDER;
        private Set<Certificate> additionalCerts = emptySet();
        private String trustStorePath = firstNonNull(System.getProperty("javax.net.ssl.trustStore"),
                DEFAULT_TRUST_STORE_PATH);
        private String trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
        private List<String> protocols = Collections.emptyList();
        private List<String> cipherSuites = Collections.emptyList();

        /**
         * Skips origin authentication.
         *
         * When true, styx will not attempt to authenticate backend servers.
         * It will accept any certificate presented by the origins.
         *
         * @deprecated
         * @param trustAllCerts
         * @return
         */
        @Deprecated
        public Builder trustAllCerts(boolean trustAllCerts) {
            this.trustAllCerts = trustAllCerts;
            return this;
        }

        public Builder authenticate(boolean authenticate) {
            this.trustAllCerts = !authenticate;
            return this;
        }

        /**
         * Sets SSL provider.
         *
         * @param sslProvider
         * @return
         */
        public Builder sslProvider(String sslProvider) {
            this.sslProvider = sslProvider;
            return this;
        }

        /**
         * Configures additional certificates.
         *
         * The additional certificates are loaded into the java keystore that has been
         * initialised from the trust store file.
         *
         * @param certificates
         * @return
         */
        public Builder additionalCerts(Certificate... certificates) {
            this.additionalCerts = Sets.newHashSet(certificates);
            return this;
        }

        /**
         * A path to trust store that is used to verify credentials presented by
         * remote origin.
         *
         * @param trustStorePath
         * @return
         */
        public Builder trustStorePath(String trustStorePath) {
            this.trustStorePath = trustStorePath;
            return this;
        }

        public Builder trustStorePassword(String trustStorePwd) {
            this.trustStorePassword = trustStorePwd;
            return this;
        }

        public Builder protocols(List<String> protocols) {
            this.protocols = protocols;
            return this;
        }

        public Builder cipherSuites(List<String> cipherSuites) {
            this.cipherSuites = cipherSuites;
            return this;
        }

        public TlsSettings build() {
            if (!trustAllCerts && trustStorePassword == null) {
                throw new IllegalArgumentException("trustStorePassword must be supplied when remote peer authentication is enabled.");
            }
            return new TlsSettings(this);
        }
    }
}
