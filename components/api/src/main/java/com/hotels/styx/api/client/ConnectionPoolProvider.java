package com.hotels.styx.api.client;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.client.ConnectionPool;

import java.io.Closeable;
import java.util.Optional;

public interface ConnectionPoolProvider extends Closeable {

    Optional<ConnectionPool> connectionPool(HttpRequest httpRequest);

    default Optional<ConnectionPool> connectionPool(HttpRequest httpRequest, Iterable<ConnectionPool> exclude) {
        return this.connectionPool(httpRequest);
    }

    default void registerStatusGauges() {
    }

    default void close() {
    }
}
