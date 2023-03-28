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
package com.hotels.styx.client.connectionpool.stubs;

import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.extension.Announcer;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.Connection;
import com.hotels.styx.client.ConnectionSettings;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A stub {@link Connection.Factory}.
 */
public class StubConnectionFactory implements Connection.Factory {
    @Override
    public Mono<Connection> createConnection(Origin origin, ConnectionSettings connectionPoolConfiguration) {
        return Mono.create(sink -> {
            sink.success(new StubConnection(origin));
        });
    }

    /**
     * A {@link Connection} produced by a {@link StubConnectionFactory}.
     */
    public static class StubConnection implements Connection {
        private final Origin origin;
        private boolean connected = true;
        private final Announcer<Listener> listeners = Announcer.to(Connection.Listener.class);

        public StubConnection(Origin origin) {
            this.origin = origin;
        }

        @Override
        public Flux<LiveHttpResponse> write(LiveHttpRequest request, HttpInterceptor.Context context) {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public Origin getOrigin() {
            return this.origin;
        }

        @Override
        public void addConnectionListener(Listener listener) {
            listeners.addListener(listener);
        }

        @Override
        public void close() {
            // To avoid infinite recursion:
            if (connected) {
                connected = false;
                listeners.announce().connectionClosed(this);
            }
        }

        @Override
        public String toString() {
            return new StringBuilder(64)
                    .append(this.getClass().getSimpleName())
                    .append("{origin=")
                    .append(origin)
                    .append(", connected=")
                    .append(connected)
                    .append('}')
                    .toString();
        }
    }
}
