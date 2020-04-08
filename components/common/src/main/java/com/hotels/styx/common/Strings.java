package com.hotels.styx.common;

public final class Strings {

    public static final boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }

    public static final boolean isNotEmpty(String s) {
        return !isNullOrEmpty(s);
    }

    private Strings() {
        // Not used.
    }
}
