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
package com.hotels.styx.api.extension;

import com.google.common.net.HostAndPort;
import com.hotels.styx.api.Id;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.hotels.styx.api.Id.GENERIC_APP;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

/**
 * An origin, i.e. a particular instance of a backend service. Has an ID, as well as an associated host name and port number.
 */
public class Origin implements Comparable<Origin> {
    private final Id applicationId;
    private final HostAndPort host;
    private final String hostAsString;
    private final Id originId;
    private final int hashCode;

    /**
     * Creates a new Origin builder.
     *
     * @param host host and port
     * @return a new Origin builder
     */
    public static Builder newOriginBuilder(HostAndPort host) {
        return new Builder(host);
    }

    /**
     * Creates a new Origin builder.
     *
     * @param host hostname
     * @param port port
     * @return a new Origin builder
     */
    public static Builder newOriginBuilder(String host, int port) {
        return new Builder(HostAndPort.fromParts(host, port));
    }

    /**
     * Creates a new builder from an existing origin that will inherit the properties of that origin.
     *
     * @param origin an existing origin
     * @return a new builder inheriting its properties
     */
    public static Builder newOriginBuilder(Origin origin) {
        return new Builder(origin);
    }

    private Origin(Builder builder) {
        this.host = requireNonNull(builder.host);
        this.hostAsString = this.host.toString();
        this.applicationId = requireNonNull(builder.applicationId);
        this.originId = requireNonNull(builder.originId);
        this.hashCode = Objects.hash(this.applicationId, this.host, this.originId);
    }

    Origin(String originId, String host) {
        this.originId = Id.id(originId);
        this.host = HostAndPort.fromString(host);
        this.hostAsString = this.host.toString();
        this.applicationId = GENERIC_APP;
        this.hashCode = Objects.hash(this.host, this.originId);
    }

    /**
     * Throws an exception if any of the origins are duplicates - i.e. if the IDs or host:port are the same.
     *
     * @param origins origins to check
     */
    public static void checkThatOriginsAreDistinct(Collection<Origin> origins) {
        Set<Id> ids = origins.stream().map(Origin::id).collect(toSet());

        checkArgument(ids.size() == origins.size(), "Duplicate ids in " + origins);

        Set<String> hosts = origins.stream().map(Origin::hostAsString).collect(toSet());

        checkArgument(hosts.size() == origins.size(), "Duplicate host and port in " + origins);
    }

    /**
     * Returns a string containing application ID and host/port in the format: ID-HOSTANDPORT.
     *
     * @return &lt;ID-HOSTANDPORT&gt;
     */
    public String applicationInfo() {
        return applicationId.toString().toUpperCase() + "-" + host;
    }

    /**
     * Returns hostname and port.
     *
     * @return hostname and port
     */
    public HostAndPort host() {
        return this.host;
    }

    /**
     * Host and port as a string.
     *
     * @return host and port as string
     */
    public String hostAsString() {
        return this.hostAsString;
    }

    /**
     * Returns the ID of the application the origin belongs to.
     *
     * @return application ID
     */
    public Id applicationId() {
        return this.applicationId;
    }

    /**
     * Returns a name for this specific origin.
     *
     * @return returns origin name.
     */
    public Id id() {
        return this.originId;
    }

    String idAsString() {
        return originId.toString();
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Origin other = (Origin) obj;
        return Objects.equals(this.applicationId, other.applicationId)
                && Objects.equals(this.host, other.host)
                && Objects.equals(this.originId, other.originId);
    }

    @Override
    public String toString() {
        return format("%s:%s:%s", applicationId, originId, host);
    }

    @Override
    public int compareTo(Origin other) {
        return this.host.toString().compareTo(other.host().toString());
    }

    /**
     * {@link Origin} builder.
     */
    public static final class Builder {
        private final HostAndPort host;
        private Id applicationId = GENERIC_APP;
        private Id originId = Id.id("anonymous-origin");

        private Builder(HostAndPort host) {
            this.host = host;
        }

        private Builder(Origin origin) {
            this.host = origin.host;
            this.applicationId = origin.applicationId;
            this.originId = origin.originId;
        }

        /**
         * Sets origin ID from a string.
         *
         * @param id id
         * @return this builder
         */
        public Builder id(String id) {
            this.originId = Id.id(id);
            return this;
        }

        /**
         * Sets the ID of the application the origin belongs to.
         *
         * @param applicationId application ID
         * @return this builder
         */
        public Builder applicationId(Id applicationId) {
            this.applicationId = applicationId;
            return this;
        }

        /**
         * Sets the ID of the application the origin belongs to from a string.
         *
         * @param applicationId application ID
         * @return this builder
         */
        public Builder applicationId(String applicationId) {
            this.applicationId = Id.id(applicationId);
            return this;
        }

        /**
         * Builds a new Origin with the properties set in this builder.
         *
         * @return a new Origin
         */
        public Origin build() {
            return new Origin(this);
        }
    }
}
