package com.hotels.styx.infrastructure.configuration;

import java.util.Objects;

import static java.lang.String.format;

/**
 * UnresolvedPlaceholder.
 */
public final class UnresolvedPlaceholder {
    private final String path;
    private final String value;
    private final String placeholder;

    public UnresolvedPlaceholder(String path, String value, String placeholder) {
        this.path = path;
        this.value = value;
        this.placeholder = placeholder;
    }

    public String path() {
        return path;
    }

    public String value() {
        return value;
    }

    public String placeholder() {
        return placeholder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UnresolvedPlaceholder that = (UnresolvedPlaceholder) o;
        return Objects.equals(path, that.path) &&
                Objects.equals(value, that.value) &&
                Objects.equals(placeholder, that.placeholder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, value, placeholder);
    }

    @Override
    public String toString() {
        return format("%s in %s=%s", placeholder, path, value);
    }
}
