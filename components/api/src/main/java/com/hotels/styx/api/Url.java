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
package com.hotels.styx.api;

import javax.annotation.concurrent.ThreadSafe;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Throwables.propagate;
import static com.hotels.styx.api.URLEncoder.encodePathSegment;
import static java.lang.Integer.parseInt;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;

/**
 * Alternate implementation for java.net.URL for easing its usage.
 */
@ThreadSafe
public final class Url implements Comparable<Url> {
    private static final String PATH_DELIMITER = "/";

    private final String scheme;
    private final Optional<Authority> authority;
    private final String path;
    private final String fragment;
    private final Optional<UrlQuery> query;

    private Url(Builder builder) {
        this.scheme = builder.scheme;
        this.authority = builder.authority;
        this.path = builder.path;
        this.query = Optional.ofNullable(builder.queryBuilder).map(UrlQuery.Builder::build);
        this.fragment = builder.fragment;
    }

    /**
     * Scheme of the URL, e.g. http
     *
     * @return scheme
     */
    public String scheme() {
        return this.scheme;
    }

    /**
     * The path part of the URL.
     *
     * @return path
     */
    public String path() {
        return this.path;
    }

    /**
     * The authority of the URL, e.g. host, host:port, user@host:port, etc.
     *
     * @return authority if present
     */
    public Optional<Authority> authority() {
        return this.authority;
    }

    /**
     * The host in the authority, if present.
     *
     * @return host if present
     */
    public Optional<String> host() {
        return authority.map(Authority::host);
    }

    /**
     * Whether the URL is secure, i.e. whether the scheme is "https".
     *
     * @return true if the URL is secure
     */
    public boolean isSecure() {
        return "https".equals(scheme);
    }

    /**
     * Whether the URL is fully-qualified, i.e. whether there is a host present.
     *
     * @return true if the URL is fully-qualified
     */
    public boolean isFullyQualified() {
        Optional<String> host = host();
        return host.isPresent() && !isNullOrEmpty(host.get());
    }

    /**
     * Whether the URL is absolute, i.e. whether the path starts with a "/".
     *
     * @return true if the URL is absolute.
     */
    public boolean isAbsolute() {
        return path.startsWith("/");
    }

    /**
     * Whether the URL is relative, i.e. not absolute.
     *
     * @return true if the URL is relative.
     * @see {@link #isAbsolute()}
     */
    public boolean isRelative() {
        return !isAbsolute();
    }

    /**
     * Convert this object to the {@link URL} class from the java standard library.
     *
     * @return a {@link URL}
     */
    public URL toURL() {
        try {
            return toURI().toURL();
        } catch (MalformedURLException e) {
            throw propagate(e);
        }
    }

    /**
     * Convert this object to the {@link URI} class from the java standard library.
     *
     * @return a {@link URI}
     */
    public URI toURI() {
        try {
            return new URI(toString());
        } catch (URISyntaxException e) {
            throw propagate(e);
        }
    }

    /**
     * The query part of the URL.
     *
     * @return query
     */
    public Optional<UrlQuery> query() {
        return this.query;
    }

    /**
     * Gets the value of a named query parameter, if present.
     *
     * @param name parameter name
     * @return query parameter value if present
     */
    public Optional<String> queryParam(String name) {
        return query.flatMap(query -> query.parameterValue(name));
    }

    /**
     * Gets the values of a named query parameter.
     *
     * @param name parameter name
     * @return values, or an empty iterable if the parameter is not present
     */
    public Iterable<String> queryParams(String name) {
        return query.map(query -> query.parameterValues(name)).orElse(emptyList());
    }

    /**
     * Get all query parameters.
     *
     * @return all query parameters
     */
    public Map<String, List<String>> queryParams() {
        if (!query.isPresent()) {
            return emptyMap();
        }

        Map<String, List<String>> map = new HashMap<>();

        query.get().parameters().forEach(parameter -> {
            List<String> values = map.computeIfAbsent(parameter.key(), k -> new ArrayList<>());

            values.add(parameter.value());
        });

        return map;
    }

    /**
     * Get the names of all query parameters.
     *
     * @return the names of all query parameters.
     */
    public Iterable<String> queryParamNames() {
        return query.map(UrlQuery::parameterNames).orElse(emptySet());
    }

    /**
     * Return the encoded url string.
     *
     * @return the encoded url string
     */
    public String encodedUri() {
        return toString();
    }

    private static CharSequence encodePathElement(String pathElement) {
        try {
            return encodePathSegment(pathElement, UTF_8.toString());
        } catch (UnsupportedEncodingException ignore) {
            return pathElement;
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (authority.isPresent()) {
            if (scheme != null) {
                builder.append(scheme).append(":");
            }
            builder.append("//").append(authority.get());
        }
        builder.append(path);

        query.ifPresent(query -> builder.append("?").append(query.encodedQuery()));

        if (fragment != null) {
            builder.append("#").append(fragment);
        }
        return builder.toString();
    }

    @Override
    public int compareTo(Url other) {
        return toString().compareTo(other.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, authority, path, query, fragment);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Url other = (Url) obj;
        return Objects.equals(this.scheme, other.scheme)
                && Objects.equals(this.authority, other.authority)
                && Objects.equals(this.path, other.path)
                && Objects.equals(this.query, other.query)
                && Objects.equals(this.fragment, other.fragment);
    }

    /**
     * Return a new {@link Builder} that will inherit properties from this object.
     * This allows a new {@link Url} to be made that will be identical to this one except for the properties
     * overridden by the builder methods.
     *
     * @return new builder based on this object
     */
    public Builder newBuilder() {
        return new Builder(this);
    }

    /**
     * Authority part of a url string.
     */
    public static final class Authority {
        private static final Pattern AUTHORITY = Pattern.compile("(?:([^@]+)@)?([^:]+)(?:\\:([\\d]+))?");
        private final String userInfo;
        private final String host;
        private final int port;

        private Authority(String userInfo, String host, int port) {
            this.userInfo = userInfo;
            this.host = host;
            this.port = port;
        }

        static Optional<Authority> authority(String authority) {
            if (authority != null) {
                Matcher matcher = AUTHORITY.matcher(authority);
                if (matcher.matches()) {
                    return Optional.of(authority(matcher.group(1), matcher.group(2), matcher.group(3)));
                }
            }
            return Optional.empty();
        }

        static Authority authority(String userInfo, String host, String port) {
            return authority(userInfo, host, port == null ? -1 : parseInt(port));
        }

        static Authority authority(String host, int port) {
            return authority(null, host, port);
        }

        static Authority authority(String userInfo, String host, int port) {
            return new Authority(userInfo, host, port);
        }

        /**
         * The user-info part of the Authority, or null if absent.
         *
         * @return user-info
         */
        public String userInfo() {
            return userInfo;
        }

        /**
         * The host part of the Authority.
         *
         * @return host
         */
        public String host() {
            return host;
        }

        Authority host(String value) {
            return authority(userInfo, value, port);
        }

        /**
         * The port part of the Authority, or -1 if absent.
         *
         * @return port
         */
        public int port() {
            return port;
        }

        /**
         * The host-and-port part of the Authority, or just the host if the port is absent.
         *
         * @return host-and-port
         */
        public String hostAndPort() {
            if (port >= 0) {
                return host + ":" + port;
            }
            return host;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            if (isNullOrEmpty(host)) {
                return builder.toString();
            }
            if (!isNullOrEmpty(userInfo)) {
                builder.append(userInfo).append("@");
            }
            builder.append(host);
            if (port != -1) {
                builder.append(":").append(port);
            }
            return builder.toString();
        }


        @Override
        public int hashCode() {
            return Objects.hash(userInfo, host, port);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            Authority other = (Authority) obj;
            return Objects.equals(this.userInfo, other.userInfo)
                    && Objects.equals(this.host, other.host)
                    && Objects.equals(this.port, other.port);
        }

    }

    /**
     * Builder for {@link Url} instances.
     */
    public static final class Builder {
        private String scheme;
        private Optional<Authority> authority = Optional.empty();
        private String path = "";
        private String fragment;
        private UrlQuery.Builder queryBuilder;

        /**
         * Construct a builder with no properties set.
         */
        public Builder() {
        }

        /**
         * Construct a builder that will inherit properties from an existing {@link Url}.
         *
         * @param url url
         */
        public Builder(Url url) {
            this.scheme = url.scheme;
            this.authority = url.authority;
            this.path = url.path;
            this.queryBuilder = url.query.map(UrlQuery::newBuilder).orElse(null);
            this.fragment = url.fragment;
        }

        /**
         * Create a builder from a URI string. This will be parsed using the java standard library.
         *
         * @param value a URI string
         * @return a new builder
         */
        public static Builder url(String value) {
            URI uri = URI.create(value);
            return new Builder()
                    .scheme(uri.getScheme())
                    .authority(uri.getAuthority())
                    .path(uri.getRawPath())
                    .rawQuery(uri.getRawQuery())
                    .fragment(uri.getFragment());
        }

        Builder rawQuery(String rawQuery) {
            this.queryBuilder = rawQuery == null ? null : new UrlQuery.Builder(rawQuery);
            return this;
        }

        /**
         * Set the scheme, e.g. http, https
         *
         * @param scheme scheme
         * @return this builder
         */
        public Builder scheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        /**
         * Set the authority as a string, e.g. host, host:port, user@host:port, etc.
         *
         * @param authority authority
         * @return this builder
         */
        public Builder authority(String authority) {
            this.authority = Authority.authority(authority);
            return this;
        }

        /**
         * Sets the path.
         *
         * @param path path
         * @return this builder
         */
        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder addQueryParam(String name, String value) {
            if (queryBuilder == null) {
                queryBuilder = new UrlQuery.Builder();
            }

            queryBuilder.addParam(name, value);
            return this;
        }

        /**
         * Sets the fragment, i.e. the part that follows the # in the URL.
         *
         * @param fragment fragment
         * @return this builder
         */
        public Builder fragment(String fragment) {
            this.fragment = fragment;
            return this;
        }

        /**
         * Sets the authority as an {@link Authority} object.
         *
         * @param authority authority
         * @return this builder
         */
        public Builder authority(Authority authority) {
            this.authority = Optional.of(authority);
            return this;
        }

        /**
         * Removes the Authority from this builder.
         *
         * @return this builder
         */
        public Builder dropHost() {
            if (authority.isPresent()) {
                this.authority = Optional.empty();
            }
            return this;
        }

        /**
         * Builds a new URL using the properties set in this builder.
         *
         * @return a new URL
         */
        public Url build() {
            return new Url(this);
        }
    }
}
