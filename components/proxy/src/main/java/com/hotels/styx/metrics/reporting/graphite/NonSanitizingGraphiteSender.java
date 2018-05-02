package com.hotels.styx.metrics.reporting.graphite;

import com.codahale.metrics.graphite.Graphite;

// The sanitize method in Graphite/PickledGraphite adds a lot of object creation. We do not need it because our
// metric names and values do not contain whitespace.
final class NonSanitizingGraphiteSender extends Graphite {
    public NonSanitizingGraphiteSender(String host, int port) {
        super(host, port);
    }

    protected String sanitize(String s) {
        return s;
    }
}
