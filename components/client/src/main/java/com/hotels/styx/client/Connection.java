/*
  Copyright (C) 2013-2023 Expedia Inc.

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

import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.extension.Origin;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
        Mono<Connection> createConnection(Origin origin, ConnectionSettings connectionSettings);
    }

    /**
     * Writes HTTP request to a remote peer in the context of this connection.
     * This method is used from with an interceptor chain.
     *
     * @param request streaming HTTP request
     * @param context HTTP interceptor context
     * @return a Publisher that provides the response
     */
    Flux<LiveHttpResponse> write(LiveHttpRequest request, HttpInterceptor.Context context);

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
