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


import java.util.Objects;

import static com.google.common.base.Objects.toStringHelper;
import static java.util.Objects.requireNonNull;

/**
 * SSL certificate.
 */
public class Certificate {
    private String alias;
    private String certificatePath;

    Certificate(String alias, String certificatePath) {
        this.alias = requireNonNull(alias);
        this.certificatePath = requireNonNull(certificatePath);
    }

    public static Certificate certificate(String alias, String certificatePath) {
        return new Builder()
                .setAlias(alias)
                .setCertificatePath(certificatePath)
                .build();
    }

    private Certificate(Builder builder) {
        this.alias = builder.alias;
        this.certificatePath = builder.certificatePath;
    }

    public String getAlias() {
        return this.alias;
    }

    public String getCertificatePath() {
        return this.certificatePath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Certificate that = (Certificate) o;
        return Objects.equals(alias, that.alias)
                && Objects.equals(certificatePath, that.certificatePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alias, certificatePath);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("alias", this.alias)
                .add("certificatePath", this.certificatePath)
                .toString();
    }

    /**
     * certificate builder.
     */
    public static final class Builder {
        private String alias;
        private String certificatePath;

        private Builder() {
        }

        /**
         * set alias.
         * @param alias alias
         * @return this
         */
        public Certificate.Builder setAlias(String alias) {
            this.alias = alias;
            return this;
        }

        /**
         * set certificate path.
         * @param certificatePath certificate path
         * @return this
         */
        public Certificate.Builder setCertificatePath(String certificatePath) {
            this.certificatePath = certificatePath;
            return this;
        }

        /**
         * build certificate.
         * @return new certificate
         */
        public Certificate build() {
            return new Certificate(this);
        }
    }
}
