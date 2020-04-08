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
    private final String host;
    private final int port;
    private final String hostAsString;
    private final Id originId;
    private final int hashCode;

    private Origin(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.hostAsString = string(host, port);
        this.applicationId = requireNonNull(builder.applicationId);
        this.originId = requireNonNull(builder.originId);
        this.hashCode = Objects.hash(this.applicationId, this.host, this.originId);
    }

    Origin(String originId, String host) {
        HostAndPort hostAndPort = HostAndPort.fromString(host);

        this.originId = Id.id(originId);
        this.host = hostAndPort.getHostText();
        this.port = hostAndPort.getPort();
        this.hostAsString = hostAndPort.toString();
        this.applicationId = GENERIC_APP;
        this.hashCode = Objects.hash(this.host, this.originId);
    }

    /**
     * Creates a new Origin builder.
     *
     * @param host hostname
     * @param port port
     * @return a new Origin builder
     */
    public static Builder newOriginBuilder(String host, int port) {
        return new Builder(host, port);
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

    /**
     * Throws an exception if any of the origins are duplicates - i.e. if the IDs or host:port are the same.
     *
     * @param origins origins to check
     */
    public static void checkThatOriginsAreDistinct(Collection<Origin> origins) {
        Set<Id> ids = origins.stream().map(Origin::id).collect(toSet());

        checkArgument(ids.size() == origins.size(), "Duplicate ids in " + origins);

        Set<String> hosts = origins.stream().map(Origin::hostAndPortString).collect(toSet());

        checkArgument(hosts.size() == origins.size(), "Duplicate host and port in " + origins);
    }

    /**
     * Creates a new Builder that inherits this origin's properties.
     *
     * @return a new Builder that inherits this origin's properties
     */
    public Builder newBuilder() {
        return newOriginBuilder(this);
    }

    /**
     * Returns a string containing application ID and host/port in the format: ID-HOSTANDPORT.
     *
     * @return &lt;ID-HOSTANDPORT&gt;
     */
    public String applicationInfo() {
        return applicationId.toString().toUpperCase() + "-" + hostAsString;
    }

    /**
     * Returns hostname.
     *
     * @return hostname
     */
    public String host() {
        return host;
    }

    /**
     * Returns port.
     *
     * @return port
     */
    public int port() {
        return port;
    }

    /**
     * Host and port as a string.
     *
     * @return host and port as string
     */
    public String hostAndPortString() {
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
    public int compareTo(Origin other) {
        return this.hostAsString.compareTo(other.hostAsString);
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
                && Objects.equals(this.hostAsString, other.hostAsString)
                && Objects.equals(this.originId, other.originId);
    }

    @Override
    public String toString() {
        return format("%s:%s:%s", applicationId, originId, hostAsString);
    }

    private static String string(String host, int port) {
        return HostAndPort.fromParts(host, port).toString();
    }

    /**
     * {@link Origin} builder.
     */
    public static final class Builder {
        private final String host;
        private final int port;
        private Id applicationId = GENERIC_APP;
        private Id originId = Id.id("anonymous-origin");

        private Builder(String host, int port) {
            this.host = requireNonNull(host);
            this.port = port;
        }

        private Builder(Origin origin) {
            this.host = origin.host;
            this.port = origin.port;
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
