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
package com.hotels.styx.client;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.extension.Origin;
import rx.Observable;

import java.io.Closeable;
import java.util.EventListener;

/**
 * A connection to an origin.
 */
public interface Connection extends Closeable {

    /**
     * A factory that creates new {@link Connection}s on demand.
     */
    interface Factory {
        /**
         * Creates a {@link Connection}.
         *
         * @param origin             origin to connect to
         * @param connectionSettings connection pool configuration
         * @return the newly created connection
         */
        Observable<Connection> createConnection(Origin origin, ConnectionSettings connectionSettings);
    }

    /**
     * Writes HTTP request to a remote peer in the context of this connection.
     *
     * @param request
     * @return an observable that provides the response
     */
    Observable<HttpResponse> write(HttpRequest request);

    /**
     * Returns if the underlying connection is still active.
     *
     * @return if the underlying connection is still active
     */
    boolean isConnected();

    /**
     * Returns the endpoint for this connection.
     *
     * @return the endpoint for this connection
     */
    Origin getOrigin();

    /**
     * Returns time to first byte in milliseconds.
     *
     * @return time to first byte in milliseconds
     */
    long getTimeToFirstByteMillis();

    /**
     * Register a listener connection state events.
     *
     * @param listener listener to register
     */
    void addConnectionListener(Listener listener);

    /**
     * Closes the connection.
     */
    void close();

    /**
     * Notifies interested parties about closed connections.
     */
    interface Listener extends EventListener {
        /**
         * Called when a connection has been closed.
         *
         * @param connection the connection that was closed
         */
        void connectionClosed(Connection connection);
    }
}
