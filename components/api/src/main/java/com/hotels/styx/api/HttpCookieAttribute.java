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

import com.google.common.base.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents an HTTP cookie attribute. Instances of this class are immutable and can only be created using static factory methods.
 */
public final class HttpCookieAttribute {
    private static final HttpCookieAttribute SECURE = new HttpCookieAttribute("Secure", null);
    private static final HttpCookieAttribute HTTPONLY = new HttpCookieAttribute("HttpOnly", null);
    private static final String DOMAIN = "Domain";
    private static final String PATH = "Path";
    private static final String MAX_AGE = "Max-Age";

    private final String name;
    private final String value;
    private final int hashCode;

    private HttpCookieAttribute(String name, String value) {
        this.name = checkNotNull(name);
        this.value = value;
        this.hashCode = Objects.hashCode(name, value);
    }

    /**
     * Creates a Domain attribute.
     *
     * @param domain domain value
     * @return created attribute
     */
    public static HttpCookieAttribute domain(String domain) {
        return new HttpCookieAttribute(DOMAIN, domain);
    }

    /**
     * Creates a Path attribute.
     *
     * @param path path value
     * @return created attribute
     */
    public static HttpCookieAttribute path(String path) {
        return new HttpCookieAttribute(PATH, checkNotNull(path));
    }

    /**
     * Creates an Max-Age attribute.
     *
     * @param maxAge Max-Age value
     * @return created attribute
     */
    public static HttpCookieAttribute maxAge(int maxAge) {
        return new HttpCookieAttribute(MAX_AGE, Integer.toString(maxAge));
    }

    /**
     * Creates an Secure attribute.
     *
     * @return created attribute
     */
    public static HttpCookieAttribute secure() {
        return SECURE;
    }

    /**
     * Creates an HttpOnly attribute.
     *
     * @return created attribute
     */
    public static HttpCookieAttribute httpOnly() {
        return HTTPONLY;
    }

    /**
     * Attribute name, e.g. Domain, Path.
     *
     * @return name
     */
    public String name() {
        return name;
    }

    /**
     * Attribute value if applicable. Attributes that do not have values, such as Secure will return null instead.
     *
     * @return value
     */
    public String value() {
        return value;
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
        HttpCookieAttribute other = (HttpCookieAttribute) obj;
        return Objects.equal(name, other.name) && Objects.equal(value, other.value);
    }

    @Override
    public String toString() {
        if (value == null) {
            return name;
        }

        return name + "=" + value;
    }
}
